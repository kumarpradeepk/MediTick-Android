package com.kabi.pillpal.meditick.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kabi.pillpal.meditick.MainActivity
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.data.AlertSound
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.model.*
import java.util.Calendar
import java.util.concurrent.Executors

object NotificationScheduler {
    const val CHANNEL_DOSE = "meditick-dose-reminders"
    const val CHANNEL_FOLLOW_UP = "meditick-follow-up"
    const val CHANNEL_REFILL = "meditick-refills"
    const val EXTRA_KIND = "kind"
    const val EXTRA_MEDICATION = "medication_id"
    const val EXTRA_SCHEDULED = "scheduled_at"
    const val KIND_DOSE = "dose"
    const val KIND_NUDGE = "nudge"
    const val KIND_REFILL = "refill"
    const val KIND_SNOOZE = "snooze"
    const val KIND_REFRESH = "refresh"
    const val ACTION_TAKE = "com.kabi.pillpal.meditick.TAKE"
    const val ACTION_SKIP = "com.kabi.pillpal.meditick.SKIP"
    const val ACTION_SNOOZE = "com.kabi.pillpal.meditick.SNOOZE"

    /** Shared by the notification action label and [scheduleSnooze] so the two can't drift. */
    const val SNOOZE_MINUTES = 10

    private val executor = Executors.newSingleThreadExecutor()

    fun createChannels(context: Context, settings: SettingsStore = SettingsStore.get(context)) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = if (settings.timeSensitiveEnabled) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
        val dose = NotificationChannel(doseChannel(settings), context.getString(R.string.channel_dose_name), importance).apply {
            description = context.getString(R.string.channel_dose_description)
            val sound = soundUri(settings.alertSound)
            setSound(sound, if (sound == null) null else audio)
            enableVibration(settings.alertSound != AlertSound.SILENT)
        }
        val followUp = NotificationChannel(CHANNEL_FOLLOW_UP, context.getString(R.string.channel_follow_up_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_follow_up_description); enableVibration(true)
        }
        val refill = NotificationChannel(CHANNEL_REFILL, context.getString(R.string.channel_refill_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_refill_description)
        }
        manager.createNotificationChannels(listOf(dose, followUp, refill))
    }

    fun scheduleAll(context: Context, repository: AppRepository = AppRepository.get(context), settings: SettingsStore = SettingsStore.get(context)) {
        val app = context.applicationContext
        executor.execute {
            synchronized(this) {
                cancelManaged(app, repository, preserveSnoozes = settings.remindersEnabled)
                if (!settings.remindersEnabled) return@synchronized
                createChannels(app, settings)
                val now = System.currentTimeMillis()
                val upcoming = buildList {
                    repeat(14) { offset ->
                        val day = DoseEngine.addDays(now, offset)
                        addAll(repository.doses(day, now).filter { it.time > now && it.state != DoseState.TAKEN && it.state != DoseState.SKIPPED })
                    }
                }.sortedBy { it.time }
                upcoming.take(160).forEach { schedule(app, KIND_DOSE, it.medication.id, it.time, it.time) }
                if (settings.followUpEnabled && BillingEntitlement.isPro(app)) {
                    upcoming.take(60).forEach {
                        schedule(app, KIND_NUDGE, it.medication.id, it.time, it.time + settings.nudgeDelayMinutes * 60_000L)
                    }
                }
                if (settings.refillRemindersEnabled) {
                    repository.activeMedications.filter { it.needsRefill }.take(6).forEach { medication ->
                        val fire = nextTenAm(now)
                        schedule(app, KIND_REFILL, medication.id, fire, fire)
                    }
                }
                schedule(app, KIND_REFRESH, "maintenance", now, nextRefresh(now), exact = false)
            }
        }
    }

    fun scheduleSnooze(context: Context, medicationId: String, scheduledAt: Long, minutes: Int = SNOOZE_MINUTES) {
        schedule(context, KIND_SNOOZE, medicationId, scheduledAt, System.currentTimeMillis() + minutes * 60_000L)
    }

    private fun schedule(context: Context, kind: String, medicationId: String, scheduledAt: Long, fireAt: Long, exact: Boolean = true) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val requestCode = requestCode(kind, medicationId, scheduledAt)
        val pending = alarmIntent(context, kind, medicationId, scheduledAt, requestCode)
        if (!exact || (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms())) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
        rememberRequest(context, requestCode, kind, medicationId, scheduledAt)
    }

    private fun cancelManaged(context: Context, repository: AppRepository, preserveSnoozes: Boolean) {
        val prefs = context.getSharedPreferences("meditick-alarms", Context.MODE_PRIVATE)
        val records = prefs.getStringSet("records", emptySet()).orEmpty()
        val alarm = context.getSystemService(AlarmManager::class.java)
        val kept = mutableSetOf<String>()
        records.forEach { record ->
            val parts = record.split('|')
            if (parts.size == 4) {
                val code = parts[0].toIntOrNull() ?: return@forEach
                val scheduled = parts[3].toLongOrNull() ?: return@forEach
                if (preserveSnoozes && shouldPreserveManagedAlarm(parts[1], parts[2], scheduled, repository.logs)) {
                    kept += record
                } else {
                    alarm.cancel(alarmIntent(context, parts[1], parts[2], scheduled, code))
                }
            }
        }
        prefs.edit().putStringSet("records", kept).apply()
    }

    private fun rememberRequest(context: Context, code: Int, kind: String, medication: String, scheduled: Long) {
        val prefs = context.getSharedPreferences("meditick-alarms", Context.MODE_PRIVATE)
        val records = prefs.getStringSet("records", emptySet()).orEmpty().toMutableSet()
        records += "$code|$kind|$medication|$scheduled"
        prefs.edit().putStringSet("records", records).apply()
    }

    internal fun forgetRequest(context: Context, kind: String, medication: String, scheduled: Long) {
        val prefs = context.getSharedPreferences("meditick-alarms", Context.MODE_PRIVATE)
        val records = prefs.getStringSet("records", emptySet()).orEmpty().toMutableSet()
        records -= "${requestCode(kind, medication, scheduled)}|$kind|$medication|$scheduled"
        prefs.edit().putStringSet("records", records).apply()
    }

    private fun alarmIntent(context: Context, kind: String, medicationId: String, scheduledAt: Long, code: Int): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            putExtra(EXTRA_KIND, kind); putExtra(EXTRA_MEDICATION, medicationId); putExtra(EXTRA_SCHEDULED, scheduledAt)
        }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun requestCode(kind: String, medication: String, scheduled: Long) = "$kind-$medication-$scheduled".hashCode()

    private fun nextTenAm(now: Long): Long = Calendar.getInstance().run {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        timeInMillis
    }

    private fun nextRefresh(now: Long): Long = Calendar.getInstance().run {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 2); set(Calendar.MINUTE, 15); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** The system tone each reminder sound maps to; null means silent. */
    fun soundUri(sound: AlertSound): android.net.Uri? = when (sound) {
        AlertSound.STANDARD, AlertSound.CHIME -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        AlertSound.BELL -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        AlertSound.URGENT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        AlertSound.SILENT -> null
    }

    /** Plays a reminder sound once so the picker can preview it. */
    fun previewSound(context: Context, sound: AlertSound) {
        val uri = soundUri(sound) ?: return
        runCatching { RingtoneManager.getRingtone(context.applicationContext, uri)?.play() }
    }

    fun doseChannel(settings: SettingsStore): String =
        "$CHANNEL_DOSE-${settings.alertSound.name.lowercase()}-${if (settings.timeSensitiveEnabled) "high" else "normal"}"
}

internal fun shouldPreserveManagedAlarm(
    kind: String, medicationId: String, scheduledAt: Long, logs: List<DoseLog>,
): Boolean = kind == NotificationScheduler.KIND_SNOOZE && logs.none {
    !it.isAsNeeded && it.medicationID == medicationId &&
        ScheduledDose.occurrenceKey(it.medicationID, it.scheduledAt) ==
        ScheduledDose.occurrenceKey(medicationId, scheduledAt)
}

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra(NotificationScheduler.EXTRA_KIND) == NotificationScheduler.KIND_REFRESH) {
            NotificationScheduler.scheduleAll(context)
            return
        }
        val medicationId = intent.getStringExtra(NotificationScheduler.EXTRA_MEDICATION) ?: return
        val scheduledAt = intent.getLongExtra(NotificationScheduler.EXTRA_SCHEDULED, 0L)
        when (intent.action) {
            NotificationScheduler.ACTION_TAKE -> act(context, medicationId, scheduledAt, DoseStatus.taken)
            NotificationScheduler.ACTION_SKIP -> act(context, medicationId, scheduledAt, DoseStatus.skipped)
            NotificationScheduler.ACTION_SNOOZE -> {
                NotificationScheduler.scheduleSnooze(context, medicationId, scheduledAt)
                NotificationManagerCompat.from(context).cancel(notificationId(medicationId, scheduledAt))
            }
            else -> {
                val kind = intent.getStringExtra(NotificationScheduler.EXTRA_KIND).orEmpty()
                if (kind == NotificationScheduler.KIND_SNOOZE) {
                    NotificationScheduler.forgetRequest(context, kind, medicationId, scheduledAt)
                }
                if (kind in setOf(NotificationScheduler.KIND_NUDGE, NotificationScheduler.KIND_SNOOZE) && occurrenceResolved(context, medicationId, scheduledAt)) return
                showNotification(context, medicationId, scheduledAt, kind)
            }
        }
    }

    private fun occurrenceResolved(context: Context, medicationId: String, scheduledAt: Long): Boolean =
        AppRepository.get(context).logs.any {
            !it.isAsNeeded && it.medicationID == medicationId &&
                ScheduledDose.occurrenceKey(it.medicationID, it.scheduledAt) == ScheduledDose.occurrenceKey(medicationId, scheduledAt)
        }

    private fun act(context: Context, medicationId: String, scheduledAt: Long, status: DoseStatus) {
        val repository = AppRepository.get(context)
        val medication = repository.medication(medicationId) ?: return
        val dose = repository.doses(scheduledAt).minByOrNull { kotlin.math.abs(it.time - scheduledAt) }
            ?.takeIf { it.medication.id == medicationId && kotlin.math.abs(it.time - scheduledAt) <= 120 * 60_000L }
            ?: ScheduledDose(medication, scheduledAt, null, DoseState.DUE)
        repository.logDose(dose, status)
        NotificationManagerCompat.from(context).cancel(notificationId(medicationId, scheduledAt))
    }

    private fun showNotification(context: Context, medicationId: String, scheduledAt: Long, kind: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val settings = SettingsStore.get(context)
        NotificationScheduler.createChannels(context, settings)
        val repository = AppRepository.get(context)
        val med = repository.medication(medicationId) ?: return
        val visibleName = if (settings.hideMedicationNames) context.getString(R.string.notif_hidden_medication) else med.name
        val title = when (kind) {
            NotificationScheduler.KIND_NUDGE, NotificationScheduler.KIND_SNOOZE -> context.getString(R.string.notif_title_still_waiting, visibleName)
            NotificationScheduler.KIND_REFILL -> context.getString(R.string.notif_title_running_low, visibleName)
            else -> context.getString(R.string.notif_title_time_for, visibleName)
        }
        val body = when (kind) {
            NotificationScheduler.KIND_NUDGE -> context.getString(R.string.notif_body_nudge)
            NotificationScheduler.KIND_SNOOZE -> context.getString(R.string.notif_body_snooze)
            NotificationScheduler.KIND_REFILL -> med.daysOfStockRemaining?.let {
                context.resources.getQuantityString(R.plurals.notif_body_refill_days, it, it)
            } ?: context.getString(R.string.notif_body_refill_generic)
            else -> if (settings.hideMedicationNames) context.getString(R.string.notif_body_hidden)
                else listOfNotNull(med.doseLabel, med.instructions.takeIf { it.isNotBlank() }).joinToString(" · ")
        }
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel = when (kind) {
            NotificationScheduler.KIND_NUDGE, NotificationScheduler.KIND_SNOOZE -> NotificationScheduler.CHANNEL_FOLLOW_UP
            NotificationScheduler.KIND_REFILL -> NotificationScheduler.CHANNEL_REFILL
            else -> NotificationScheduler.doseChannel(settings)
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(open)
        if (settings.alertSound == AlertSound.SILENT) builder.setSilent(true)
        if (!settings.timeSensitiveEnabled) builder.priority = NotificationCompat.PRIORITY_DEFAULT
        if (kind != NotificationScheduler.KIND_REFILL) {
            builder.addAction(0, context.getString(R.string.action_take), actionIntent(context, NotificationScheduler.ACTION_TAKE, medicationId, scheduledAt, 11))
                .addAction(0, context.getString(R.string.action_snooze_minutes, NotificationScheduler.SNOOZE_MINUTES), actionIntent(context, NotificationScheduler.ACTION_SNOOZE, medicationId, scheduledAt, 12))
                .addAction(0, context.getString(R.string.action_skip), actionIntent(context, NotificationScheduler.ACTION_SKIP, medicationId, scheduledAt, 13))
        }
        NotificationManagerCompat.from(context).notify(notificationId(medicationId, scheduledAt), builder.build())
    }

    private fun actionIntent(context: Context, action: String, med: String, scheduled: Long, salt: Int): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java).apply {
            this.action = action; putExtra(NotificationScheduler.EXTRA_MEDICATION, med); putExtra(NotificationScheduler.EXTRA_SCHEDULED, scheduled)
        }
        return PendingIntent.getBroadcast(context, "$action-$med-$scheduled-$salt".hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationId(medication: String, scheduled: Long) = "$medication-$scheduled".hashCode()
}

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action in setOf(
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
            )) NotificationScheduler.scheduleAll(context)
    }
}

/** Tiny bridge used by the reminder layer without retaining the Billing client. */
object BillingEntitlement {
    fun isPro(context: Context): Boolean = context.getSharedPreferences("meditick-billing", Context.MODE_PRIVATE).getBoolean("is_pro", false)
}
