@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.AppLinks
import com.kabi.pillpal.meditick.LocaleSupport
import com.kabi.pillpal.meditick.ReleaseNotes
import com.kabi.pillpal.meditick.billing.BillingManager
import com.kabi.pillpal.meditick.BuildConfig
import com.kabi.pillpal.meditick.data.*
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.notifications.NotificationScheduler
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS

@Composable
fun SettingsScreen(
    repository: AppRepository, settings: SettingsStore, billing: BillingManager,
    onShowPaywall: () -> Unit, requestNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    fun openMail(subject: String) {
        val uri = Uri.parse("mailto:?subject=${Uri.encode(subject)}")
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }
    }
    /** Opens an external destination; a missing browser must never crash Settings. */
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { message = "Couldn't open that link." }
    }
    var showAppearance by remember { mutableStateOf(false) }
    var showMeals by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showSound by remember { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<String?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(repository.exportJson()) } }
            .onSuccess { message = "Backup exported." }.onFailure { message = "Couldn’t export the backup." } }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val ok = runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> repository.importJson(reader.readText()) } ?: false }.getOrDefault(false)
            message = if (ok) "Backup restored — ${repository.medications.size} medication${if (repository.medications.size == 1) "" else "s"} loaded." else "That file doesn’t look like a MediTick backup."
        }
    }
    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 126.dp)) {
            item { Spacer(Modifier.statusBarsPadding().height(1.dp)); Text("Settings", style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink, modifier = Modifier.padding(bottom = 18.dp)) }
            item {
                if (billing.isPro) {
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.Check, DS.colors.mint, 44.dp); Spacer(Modifier.width(14.dp))
                            Column { Text("Pro is active", color = DS.colors.ink, fontWeight = FontWeight.ExtraBold); Text("Thanks for the support", color = DS.colors.ink3, fontSize = 12.sp) }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DS.colors.gradient).clickable(onClick = onShowPaywall).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.AutoAwesome, DS.colors.gradEnd, 44.dp); Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Unlock MediTick Pro", color = DS.colors.onMint, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("Meal sync, follow-ups, full history and unlimited meds", color = DS.colors.onMint.copy(.78f), fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = DS.colors.onMint.copy(.7f))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            item { SettingsGroup("MediTick Pro") {
                SettingsRow(Icons.Default.Redeem, DS.colors.violet, "Redeem Offer Code", "Unlock Pro with a Play promo code",
                    onClick = { open("https://play.google.com/redeem") })
                RowDivider(); SettingsRow(Icons.Default.Group, DS.colors.mint, "Join the MediTick Community", "Routines, questions and what's next",
                    onClick = { open(AppLinks.FACEBOOK_GROUP) })
            } }
            item { SettingsGroup("Preferences") {
                SettingsRow(if (settings.appearance == AppearanceMode.LIGHT) Icons.Default.LightMode else Icons.Default.DarkMode, DS.colors.violet,
                    "Appearance", "${when (settings.appearance) { AppearanceMode.SYSTEM -> "Auto"; AppearanceMode.LIGHT -> "Daylight"; AppearanceMode.DARK -> "Midnight" }} · ${settings.accent.name.lowercase().replaceFirstChar { it.uppercase() }} accent", onClick = { showAppearance = true })
                RowDivider(); SettingsRow(Icons.Default.Restaurant, DS.colors.amber, "Meal times", "Breakfast · Lunch · Dinner · Bedtime",
                    onClick = { if (billing.isPro) showMeals = true else onShowPaywall() }) { if (billing.isPro) Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3) else StatusPill("Pro", DS.colors.violet) }
                RowDivider(); SettingsRow(Icons.Default.Widgets, DS.colors.cyan, "Widgets", "Home and supported lock-screen options", onClick = { infoDialog = "widgets" })
                RowDivider(); SettingsRow(Icons.Default.Schedule, DS.colors.mint, "Dose time presets", "Morning · Midday · Evening · Bedtime", onClick = { showPresets = true })
                RowDivider(); SettingsRow(Icons.Default.Language, DS.colors.cyan, "Language", languageTitle(settings.languageTag), onClick = { showLanguage = true })
                RowDivider(); SettingsRow(Icons.Default.Translate, DS.colors.ink2, "System language settings", "Open Android's per-app language screen", onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= 33) Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")) else Intent(Settings.ACTION_LOCALE_SETTINGS)
                    runCatching { context.startActivity(intent) }
                })
                RowDivider(); SettingsRow(Icons.Default.TouchApp, DS.colors.ink2, "Haptics", "Subtle taps as you interact") { Switch(settings.hapticsEnabled, settings::setHaptics) }
            } }
            item { SettingsGroup("Notifications") {
                SettingsRow(Icons.Default.Notifications, DS.colors.mint, "Notification permission", "Tap to allow reminders", onClick = requestNotificationPermission)
                RowDivider(); SettingsRow(Icons.Default.Settings, DS.colors.cyan, "Notification status", "Open Android notification settings", onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
                RowDivider(); SettingsRow(Icons.Default.NotificationAdd, DS.colors.cyan, "Dose reminders", "A notification at every dose") { Switch(settings.remindersEnabled, onCheckedChange = {
                    settings.setReminders(it); if (it) requestNotificationPermission(); NotificationScheduler.scheduleAll(context)
                }) }
                RowDivider(); SettingsRow(Icons.Default.Alarm, DS.colors.amber, "Follow-up reminder", "Re-nudge until you log the dose",
                    onClick = if (billing.isPro) null else onShowPaywall) {
                    if (billing.isPro) Switch(settings.followUpEnabled, onCheckedChange = { settings.setFollowUp(it); NotificationScheduler.scheduleAll(context) }) else StatusPill("Pro", DS.colors.violet)
                }
                if (billing.isPro && settings.followUpEnabled) {
                    RowDivider(); SettingsRow(Icons.Default.Timer, DS.colors.amber, "Follow-up delay", "${settings.nudgeDelayMinutes} minutes", onClick = null) {
                        var expanded by remember { mutableStateOf(false) }; Box {
                            TextButton({ expanded = true }) { Text("${settings.nudgeDelayMinutes} min") }
                            DropdownMenu(expanded, { expanded = false }) { listOf(5, 10, 15, 30, 60).forEach { minutes -> DropdownMenuItem({ Text("$minutes min") }, { settings.setNudgeDelay(minutes); expanded = false; NotificationScheduler.scheduleAll(context) }) } }
                        }
                    }
                }
                RowDivider(); SettingsRow(Icons.Default.VisibilityOff, DS.colors.violet, "Hide medication names in notifications", "Keep drug names off the lock screen") {
                    Switch(settings.hideMedicationNames, onCheckedChange = { settings.updateHideMedicationNames(it); NotificationScheduler.scheduleAll(context) })
                }
                RowDivider(); SettingsRow(Icons.Default.MusicNote, DS.colors.cyan, "Alert Sound", alertSoundTitle(settings.alertSound), onClick = { showSound = true })
                RowDivider(); SettingsRow(Icons.Default.PriorityHigh, DS.colors.violet, "Urgent reminders", "High-priority channel; Android may still apply Do Not Disturb") { Switch(settings.timeSensitiveEnabled, { settings.setTimeSensitive(it); NotificationScheduler.scheduleAll(context) }) }
                if (android.os.Build.VERSION.SDK_INT >= 31) { RowDivider(); SettingsRow(Icons.Default.AlarmOn, DS.colors.amber, "Exact alarm access", "Open Android’s Alarms & reminders access", onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } }) }
                RowDivider(); SettingsRow(Icons.Default.Inventory2, DS.colors.coral, "Refill reminders", "Alert below your stock threshold") { Switch(settings.refillRemindersEnabled, onCheckedChange = { settings.setRefillReminders(it); NotificationScheduler.scheduleAll(context) }) }
            } }
            item { SettingsGroup("Your data") {
                SettingsRow(Icons.Default.UploadFile, DS.colors.mint, "Export backup", "Everything as a JSON file", onClick = { exporter.launch("MediTick-backup.json") })
                RowDivider(); SettingsRow(Icons.Default.Download, DS.colors.cyan, "Import backup", "Restore an iOS or Android MediTick export", onClick = { importer.launch(arrayOf("application/json", "text/json", "text/plain")) })
                RowDivider(); SettingsRow(Icons.Default.Delete, DS.colors.coral, "Erase all data", "Every medication, plan and log", onClick = { confirmErase = true })
            } }
            item { CommunityCard(onOpen = ::open); Spacer(Modifier.height(14.dp)) }
            item { SettingsGroup("Community") {
                SettingsRow(Icons.Default.BugReport, DS.colors.coral, "Send Feedback / Report Bug", "Share device and reproduction details", onClick = { openMail("MediTick Android bug report") })
                RowDivider(); SettingsRow(Icons.Default.Lightbulb, DS.colors.mint, "Request a Feature", "Tell us what would make MediTick better", onClick = { openMail("MediTick Android feature request") })
                RowDivider(); SettingsRow(Icons.Default.Star, DS.colors.amber, "Write a review", onClick = {
                    val uri = Uri.parse("market://details?id=${context.packageName}"); runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                })
                RowDivider(); SettingsRow(Icons.Default.Share, DS.colors.cyan, "Share MediTick", onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "MediTick — never miss a dose again. Pill timer, reminders and tracking.")
                    context.startActivity(Intent.createChooser(send, "Share MediTick"))
                })
            } }
            item { SettingsGroup("About") {
                if (AppLinks.isConfigured(AppLinks.WEBSITE)) {
                    SettingsRow(Icons.Default.Public, DS.colors.cyan, "Website", onClick = { open(AppLinks.WEBSITE) }); RowDivider()
                }
                if (AppLinks.isConfigured(AppLinks.THREADS)) {
                    SettingsRow(Icons.Default.AlternateEmail, DS.colors.violet, "Threads", onClick = { open(AppLinks.THREADS) }); RowDivider()
                }
                if (AppLinks.isConfigured(AppLinks.INSTAGRAM)) {
                    SettingsRow(Icons.Default.PhotoCamera, DS.colors.coral, "Instagram", onClick = { open(AppLinks.INSTAGRAM) }); RowDivider()
                }
                SettingsRow(Icons.Default.NewReleases, DS.colors.mint, "What’s New", "MediTick ${BuildConfig.VERSION_NAME}", onClick = { showWhatsNew = true })
                RowDivider(); SettingsRow(Icons.Default.PrivacyTip, DS.colors.cyan, "Privacy Policy", "How MediTick handles your data",
                    onClick = { if (AppLinks.isConfigured(AppLinks.PRIVACY_POLICY)) open(AppLinks.PRIVACY_POLICY) else infoDialog = "privacy" })
                RowDivider(); SettingsRow(Icons.Default.Gavel, DS.colors.violet, "Terms of Service", "Reminder-tool limitations",
                    onClick = { if (AppLinks.isConfigured(AppLinks.TERMS_OF_SERVICE)) open(AppLinks.TERMS_OF_SERVICE) else infoDialog = "terms" })
                RowDivider(); SettingsRow(Icons.Default.Info, DS.colors.ink2, "About MediTick", "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onClick = { infoDialog = "about" })
            } }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(AppLinks.TAGLINE, color = DS.colors.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = DS.colors.ink3, fontSize = 12.sp)
                }
            }
            item { Text("MediTick · Your health data stays on this device.\nA reminder tool, not medical advice.", color = DS.colors.ink3, fontSize = 12.sp,
                textAlign = TextAlign.Center, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) }
        }
    }
    if (showAppearance) AppearanceDialog(settings) { showAppearance = false }
    if (showMeals) MealTimesDialog(repository, onDismiss = { showMeals = false })
    if (showPresets) DoseTimePresetsDialog(settings, onDismiss = { showPresets = false })
    if (confirmErase) AlertDialog(onDismissRequest = { confirmErase = false }, title = { Text("Erase everything?") }, text = { Text("This permanently deletes all medications, prescriptions and history from this device.") },
        confirmButton = { TextButton({ repository.eraseAll(); confirmErase = false }) { Text("Erase all data", color = DS.colors.coral) } }, dismissButton = { TextButton({ confirmErase = false }) { Text("Cancel") } })
    message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton({ message = null }) { Text("OK") } }, title = { Text("MediTick") }, text = { Text(it) }) }
    if (showLanguage) LanguageDialog(settings) { showLanguage = false }
    if (showSound) ReminderSoundDialog(settings, billing.isPro, onShowPaywall) { showSound = false }
    if (showWhatsNew) WhatsNewDialog { showWhatsNew = false }
    infoDialog?.let { kind ->
        val (title, copy) = when (kind) {
            "widgets" -> "Widgets" to "Add MediTick from your launcher’s Home-screen widget picker. Lock-screen widget support depends on your Android version and device maker, and cannot be enabled automatically. The current widget shows today’s progress and next dose; logging still opens the app."
            "privacy" -> "Privacy" to "Medication data, schedules and logs are stored on this device. Export only when you choose it. Google Play processes purchases; Android delivers notifications."
            "terms" -> "Terms of use" to "MediTick is a reminder and personal logging tool, not medical advice. Always follow your clinician’s instructions and seek professional help for medication questions."
            "what_new" -> "What’s new" to "Treatment filters, prescription lifecycle controls, day-interval schedules, private notifications, exact-time groups and richer progress history."
            else -> "About MediTick" to "MediTick ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nPrivate, offline-first medication reminders for Android."
        }
        AlertDialog(onDismissRequest = { infoDialog = null }, title = { Text(title) }, text = { Text(copy) }, confirmButton = { TextButton({ infoDialog = null }) { Text("Done") } })
    }
}

/** Human name for a stored BCP-47 tag; "system" follows the device. */
internal fun languageTitle(tag: String): String = when (tag) {
    "system" -> "System"
    "en" -> "English"
    "es" -> "Español (España)"
    "ru" -> "Русский"
    "de" -> "Deutsch"
    "pt-PT" -> "Português (Portugal)"
    "vi" -> "Tiếng Việt"
    "ja" -> "日本語"
    "ko" -> "한국어"
    else -> tag
}

internal val supportedLanguageTags = listOf("system", "en", "es", "ru", "de", "pt-PT", "vi", "ja", "ko")

internal fun alertSoundTitle(sound: AlertSound): String = when (sound) {
    AlertSound.STANDARD -> "MediTick (standard)"
    AlertSound.CHIME -> "Chime (long)"
    AlertSound.BELL -> "Bell"
    AlertSound.URGENT -> "Urgent"
    AlertSound.SILENT -> "Silent"
}

/** Chime, Bell and Urgent are part of Pro. */
internal fun alertSoundRequiresPro(sound: AlertSound): Boolean =
    sound == AlertSound.CHIME || sound == AlertSound.BELL || sound == AlertSound.URGENT

/** The community card: an invitation plus the two places conversations happen. */
@Composable
private fun CommunityCard(onOpen: (String) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Text("You're not doing this alone", color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Swap routines with other MediTick users, ask questions, report bugs, and hear about new features first.",
            color = DS.colors.ink3, fontSize = 12.5.sp, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (AppLinks.isConfigured(AppLinks.FACEBOOK_GROUP)) {
                Button({ onOpen(AppLinks.FACEBOOK_GROUP) }) { Text("Facebook") }
            }
            if (AppLinks.isConfigured(AppLinks.REDDIT)) {
                OutlinedButton({ onOpen(AppLinks.REDDIT) }) { Text("Reddit") }
            }
        }
    }
}

/** In-app language override; iOS and Android offer the same eight. */
@Composable
private fun LanguageDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Language") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                supportedLanguageTags.forEach { tag ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                settings.updateLanguageTag(tag)
                                applyAppLocale(context, tag)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(languageTitle(tag), color = if (settings.languageTag == tag) DS.colors.mint else DS.colors.ink)
                        Spacer(Modifier.weight(1f))
                        if (settings.languageTag == tag) Icon(Icons.Default.Check, null, tint = DS.colors.mint)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

/**
 * Records the choice with the system where the platform supports it, then
 * recreates the Activity so every string re-resolves immediately.
 */
private fun applyAppLocale(context: android.content.Context, tag: String) {
    LocaleSupport.apply(context, tag)
    (context as? android.app.Activity)?.recreate()
}

/**
 * Reminder sound picker: tapping previews, and the choice only applies once
 * saved, so browsing never silently changes what a reminder sounds like.
 */
@Composable
private fun ReminderSoundDialog(
    settings: SettingsStore,
    isPro: Boolean,
    onShowPaywall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(settings.alertSound) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder Sound") },
        text = {
            Column {
                AlertSound.entries.forEach { sound ->
                    val locked = alertSoundRequiresPro(sound) && !isPro
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                if (locked) onShowPaywall() else {
                                    pending = sound
                                    NotificationScheduler.previewSound(context, sound)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(alertSoundTitle(sound), color = if (pending == sound) DS.colors.mint else DS.colors.ink)
                        Spacer(Modifier.weight(1f))
                        when {
                            locked -> StatusPill("Pro", DS.colors.violet)
                            pending == sound -> Icon(Icons.Default.Check, null, tint = DS.colors.mint)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tap a sound to preview it. Changes are applied after you save. Some sounds require MediTick Pro.",
                    color = DS.colors.ink3, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    settings.updateAlertSound(pending)
                    // Channels are immutable once created, so the scheduler
                    // rebuilds them under a new id before re-queuing.
                    NotificationScheduler.scheduleAll(context)
                    onDismiss()
                },
                enabled = pending != settings.alertSound,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** The in-app changelog. */
@Composable
private fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What’s New") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReleaseNotes.entries.forEach { entry ->
                    Text(
                        listOfNotNull(entry.version, entry.date.ifBlank { null }).joinToString(" · "),
                        color = DS.colors.ink, fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(entry.lead, color = DS.colors.ink2, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    entry.bullets.forEach { bullet ->
                        Text("• $bullet", color = DS.colors.ink3, fontSize = 12.5.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 20.dp)) { SectionLabel(label, Modifier.padding(bottom = 8.dp)); GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp), content = content) }
}

@Composable
private fun AppearanceDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Appearance") }, text = {
        Column {
            SectionLabel("Mode"); Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { AppearanceMode.entries.forEach { SelectChip(when (it) { AppearanceMode.SYSTEM -> "Auto"; AppearanceMode.LIGHT -> "Daylight"; AppearanceMode.DARK -> "Midnight" }, settings.appearance == it, { settings.updateAppearance(it) }) } }
            Spacer(Modifier.height(20.dp)); SectionLabel("Accent"); Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { AccentId.entries.forEach { accent ->
                GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, onClick = { settings.updateAccent(accent) }, contentPadding = PaddingValues(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(accentBrush(accent))); Spacer(Modifier.width(11.dp))
                        Text(accent.name.lowercase().replaceFirstChar { it.uppercase() }, color = DS.colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (settings.accent == accent) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (settings.accent == accent) DS.colors.mint else DS.colors.ink3)
                    }
                }
            } }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Done") } })
}

private fun accentBrush(accent: AccentId) = androidx.compose.ui.graphics.Brush.linearGradient(when (accent) {
    AccentId.AURORA -> listOf(androidx.compose.ui.graphics.Color(0xFF8CF5BE), androidx.compose.ui.graphics.Color(0xFF54D8F5))
    AccentId.OCEAN -> listOf(androidx.compose.ui.graphics.Color(0xFF7ECDFF), androidx.compose.ui.graphics.Color(0xFF94A0FF))
    AccentId.ORCHID -> listOf(androidx.compose.ui.graphics.Color(0xFFCDABFF), androidx.compose.ui.graphics.Color(0xFFFF9CD8))
    AccentId.EMBER -> listOf(androidx.compose.ui.graphics.Color(0xFFFFC670), androidx.compose.ui.graphics.Color(0xFFFF8577))
})

@Composable
internal fun MealTimesDialog(repository: AppRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var times by remember { mutableStateOf(repository.mealTimes) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Meal times") }, text = {
        Column { MealSlot.entries.forEach { slot ->
            val time = times.time(slot)
            SettingsRow(when (slot) { MealSlot.breakfast -> Icons.Default.WbSunny; MealSlot.lunch -> Icons.Default.LightMode; MealSlot.dinner -> Icons.Default.NightsStay; MealSlot.bedtime -> Icons.Default.Bedtime },
                when (slot) { MealSlot.breakfast -> DS.colors.amber; MealSlot.lunch -> DS.colors.cyan; MealSlot.dinner -> DS.colors.violet; MealSlot.bedtime -> DS.colors.mint },
                slot.name.replaceFirstChar { it.uppercase() }, time.label(), onClick = {
                    android.app.TimePickerDialog(context, { _, h, m -> times = times.withTime(slot, TimeOfDay(h, m)) }, time.hour, time.minute, false).show()
                })
        } }
    }, confirmButton = { TextButton({ repository.setMealTimes(times); onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun DoseTimePresetsDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val labels = listOf("Morning", "Midday", "Evening", "Bedtime")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Dose time presets") }, text = {
        Column {
            settings.doseTimePresets.all().forEachIndexed { index, time ->
                SettingsRow(Icons.Default.Schedule, if (index % 2 == 0) DS.colors.mint else DS.colors.cyan, labels[index], time.label(), onClick = {
                    android.app.TimePickerDialog(context, { _, hour, minute -> settings.setDoseTimePreset(index, TimeOfDay(hour, minute)) }, time.hour, time.minute, false).show()
                })
            }
            TextButton({ settings.restoreDoseTimePresets() }, Modifier.fillMaxWidth()) { Text("Restore default times") }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Done") } })
}
