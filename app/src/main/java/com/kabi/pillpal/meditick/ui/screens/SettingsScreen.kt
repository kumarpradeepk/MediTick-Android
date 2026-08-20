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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.kabi.pillpal.meditick.R
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
            .onFailure { message = context.getString(R.string.settings_msg_link_failed) }
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
            .onSuccess { message = context.getString(R.string.settings_msg_exported) }
            .onFailure { message = context.getString(R.string.settings_msg_export_failed) } }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val ok = runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> repository.importJson(reader.readText()) } ?: false }.getOrDefault(false)
            message = if (ok) context.resources.getQuantityString(
                R.plurals.settings_msg_restored, repository.medications.size, repository.medications.size,
            ) else context.getString(R.string.settings_msg_bad_backup)
        }
    }
    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 126.dp)) {
            item { Spacer(Modifier.statusBarsPadding().height(1.dp)); Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink, modifier = Modifier.padding(bottom = 18.dp)) }
            item {
                if (billing.isPro) {
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.Check, DS.colors.mint, 44.dp); Spacer(Modifier.width(14.dp))
                            Column { Text(stringResource(R.string.settings_pro_active), color = DS.colors.ink, fontWeight = FontWeight.ExtraBold); Text(stringResource(R.string.settings_pro_thanks), color = DS.colors.ink3, fontSize = 12.sp) }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DS.colors.gradient).clickable(onClick = onShowPaywall).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.AutoAwesome, DS.colors.gradEnd, 44.dp); Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_unlock_pro), color = DS.colors.onMint, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(stringResource(R.string.settings_unlock_pro_sub), color = DS.colors.onMint.copy(.78f), fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = DS.colors.onMint.copy(.7f))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            item { SettingsGroup(stringResource(R.string.settings_group_pro)) {
                SettingsRow(Icons.Default.Redeem, DS.colors.violet, stringResource(R.string.settings_redeem), stringResource(R.string.settings_redeem_sub),
                    onClick = { open("https://play.google.com/redeem") })
                RowDivider(); SettingsRow(Icons.Default.Group, DS.colors.mint, stringResource(R.string.settings_join_community), stringResource(R.string.settings_join_community_sub),
                    onClick = { open(AppLinks.FACEBOOK_GROUP) })
            } }
            item { SettingsGroup(stringResource(R.string.settings_group_preferences)) {
                SettingsRow(if (settings.appearance == AppearanceMode.LIGHT) Icons.Default.LightMode else Icons.Default.DarkMode, DS.colors.violet,
                    stringResource(R.string.settings_appearance),
                    stringResource(
                        R.string.settings_appearance_summary,
                        stringResource(appearanceTitleRes(settings.appearance)),
                        stringResource(accentTitleRes(settings.accent)),
                    ),
                    onClick = { showAppearance = true })
                RowDivider(); SettingsRow(Icons.Default.Restaurant, DS.colors.amber, stringResource(R.string.settings_meal_times), stringResource(R.string.settings_meal_times_sub),
                    onClick = { if (billing.isPro) showMeals = true else onShowPaywall() }) { if (billing.isPro) Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3) else StatusPill(stringResource(R.string.badge_pro), DS.colors.violet) }
                RowDivider(); SettingsRow(Icons.Default.Widgets, DS.colors.cyan, stringResource(R.string.settings_widgets), stringResource(R.string.settings_widgets_sub), onClick = { infoDialog = "widgets" })
                RowDivider(); SettingsRow(Icons.Default.Schedule, DS.colors.mint, stringResource(R.string.settings_dose_presets), stringResource(R.string.settings_dose_presets_sub), onClick = { showPresets = true })
                RowDivider(); SettingsRow(Icons.Default.Language, DS.colors.cyan, stringResource(R.string.settings_language), stringResource(languageTitleRes(settings.languageTag)), onClick = { showLanguage = true })
                RowDivider(); SettingsRow(Icons.Default.Translate, DS.colors.ink2, stringResource(R.string.settings_system_language), stringResource(R.string.settings_system_language_sub), onClick = {
                    val intent = if (android.os.Build.VERSION.SDK_INT >= 33) Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")) else Intent(Settings.ACTION_LOCALE_SETTINGS)
                    runCatching { context.startActivity(intent) }
                })
                RowDivider(); SettingsRow(Icons.Default.TouchApp, DS.colors.ink2, stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_sub)) { Switch(settings.hapticsEnabled, settings::setHaptics) }
            } }
            item { SettingsGroup(stringResource(R.string.settings_group_notifications)) {
                SettingsRow(Icons.Default.Notifications, DS.colors.mint, stringResource(R.string.settings_notif_permission), stringResource(R.string.settings_notif_permission_sub), onClick = requestNotificationPermission)
                RowDivider(); SettingsRow(Icons.Default.Settings, DS.colors.cyan, stringResource(R.string.settings_notif_status), stringResource(R.string.settings_notif_status_sub), onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
                RowDivider(); SettingsRow(Icons.Default.NotificationAdd, DS.colors.cyan, stringResource(R.string.settings_dose_reminders), stringResource(R.string.settings_dose_reminders_sub)) { Switch(settings.remindersEnabled, onCheckedChange = {
                    settings.setReminders(it); if (it) requestNotificationPermission(); NotificationScheduler.scheduleAll(context)
                }) }
                RowDivider(); SettingsRow(Icons.Default.Alarm, DS.colors.amber, stringResource(R.string.settings_follow_up), stringResource(R.string.settings_follow_up_sub),
                    onClick = if (billing.isPro) null else onShowPaywall) {
                    if (billing.isPro) Switch(settings.followUpEnabled, onCheckedChange = { settings.setFollowUp(it); NotificationScheduler.scheduleAll(context) }) else StatusPill(stringResource(R.string.badge_pro), DS.colors.violet)
                }
                if (billing.isPro && settings.followUpEnabled) {
                    RowDivider(); SettingsRow(Icons.Default.Timer, DS.colors.amber, stringResource(R.string.settings_follow_up_delay), pluralStringResource(R.plurals.settings_minutes, settings.nudgeDelayMinutes, settings.nudgeDelayMinutes), onClick = null) {
                        var expanded by remember { mutableStateOf(false) }; Box {
                            TextButton({ expanded = true }) { Text(stringResource(R.string.settings_minutes_short, settings.nudgeDelayMinutes)) }
                            DropdownMenu(expanded, { expanded = false }) { listOf(5, 10, 15, 30, 60).forEach { minutes -> DropdownMenuItem({ Text(stringResource(R.string.settings_minutes_short, minutes)) }, { settings.setNudgeDelay(minutes); expanded = false; NotificationScheduler.scheduleAll(context) }) } }
                        }
                    }
                }
                RowDivider(); SettingsRow(Icons.Default.VisibilityOff, DS.colors.violet, stringResource(R.string.settings_hide_names), stringResource(R.string.settings_hide_names_sub)) {
                    Switch(settings.hideMedicationNames, onCheckedChange = { settings.updateHideMedicationNames(it); NotificationScheduler.scheduleAll(context) })
                }
                RowDivider(); SettingsRow(Icons.Default.MusicNote, DS.colors.cyan, stringResource(R.string.settings_alert_sound), stringResource(alertSoundTitleRes(settings.alertSound)), onClick = { showSound = true })
                RowDivider(); SettingsRow(Icons.Default.PriorityHigh, DS.colors.violet, stringResource(R.string.settings_urgent), stringResource(R.string.settings_urgent_sub)) { Switch(settings.timeSensitiveEnabled, { settings.setTimeSensitive(it); NotificationScheduler.scheduleAll(context) }) }
                if (android.os.Build.VERSION.SDK_INT >= 31) { RowDivider(); SettingsRow(Icons.Default.AlarmOn, DS.colors.amber, stringResource(R.string.settings_exact_alarm), stringResource(R.string.settings_exact_alarm_sub), onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } }) }
                RowDivider(); SettingsRow(Icons.Default.Inventory2, DS.colors.coral, stringResource(R.string.settings_refill_reminders), stringResource(R.string.settings_refill_reminders_sub)) { Switch(settings.refillRemindersEnabled, onCheckedChange = { settings.setRefillReminders(it); NotificationScheduler.scheduleAll(context) }) }
            } }
            item { SettingsGroup(stringResource(R.string.settings_group_data)) {
                SettingsRow(Icons.Default.UploadFile, DS.colors.mint, stringResource(R.string.settings_export), stringResource(R.string.settings_export_sub), onClick = { exporter.launch("MediTick-backup.json") })
                RowDivider(); SettingsRow(Icons.Default.Download, DS.colors.cyan, stringResource(R.string.settings_import), stringResource(R.string.settings_import_sub), onClick = { importer.launch(arrayOf("application/json", "text/json", "text/plain")) })
                RowDivider(); SettingsRow(Icons.Default.Delete, DS.colors.coral, stringResource(R.string.settings_erase), stringResource(R.string.settings_erase_sub), onClick = { confirmErase = true })
            } }
            item { CommunityCard(onOpen = ::open); Spacer(Modifier.height(14.dp)) }
            item { SettingsGroup(stringResource(R.string.settings_group_community)) {
                SettingsRow(Icons.Default.BugReport, DS.colors.coral, stringResource(R.string.settings_feedback), stringResource(R.string.settings_feedback_sub), onClick = { openMail(context.getString(R.string.mail_subject_bug)) })
                RowDivider(); SettingsRow(Icons.Default.Lightbulb, DS.colors.mint, stringResource(R.string.settings_feature_request), stringResource(R.string.settings_feature_request_sub), onClick = { openMail(context.getString(R.string.mail_subject_feature)) })
                RowDivider(); SettingsRow(Icons.Default.Star, DS.colors.amber, stringResource(R.string.settings_review), onClick = {
                    val uri = Uri.parse("market://details?id=${context.packageName}"); runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                })
                RowDivider(); SettingsRow(Icons.Default.Share, DS.colors.cyan, stringResource(R.string.settings_share), onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_share_text))
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.settings_share)))
                })
            } }
            item { SettingsGroup(stringResource(R.string.settings_group_about)) {
                if (AppLinks.isConfigured(AppLinks.WEBSITE)) {
                    SettingsRow(Icons.Default.Public, DS.colors.cyan, stringResource(R.string.settings_website), onClick = { open(AppLinks.WEBSITE) }); RowDivider()
                }
                if (AppLinks.isConfigured(AppLinks.THREADS)) {
                    SettingsRow(Icons.Default.AlternateEmail, DS.colors.violet, stringResource(R.string.brand_threads), onClick = { open(AppLinks.THREADS) }); RowDivider()
                }
                if (AppLinks.isConfigured(AppLinks.INSTAGRAM)) {
                    SettingsRow(Icons.Default.PhotoCamera, DS.colors.coral, stringResource(R.string.brand_instagram), onClick = { open(AppLinks.INSTAGRAM) }); RowDivider()
                }
                SettingsRow(Icons.Default.NewReleases, DS.colors.mint, stringResource(R.string.settings_whats_new), stringResource(R.string.settings_whats_new_sub, BuildConfig.VERSION_NAME), onClick = { showWhatsNew = true })
                RowDivider(); SettingsRow(Icons.Default.PrivacyTip, DS.colors.cyan, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_sub),
                    onClick = { if (AppLinks.isConfigured(AppLinks.PRIVACY_POLICY)) open(AppLinks.PRIVACY_POLICY) else infoDialog = "privacy" })
                RowDivider(); SettingsRow(Icons.Default.Gavel, DS.colors.violet, stringResource(R.string.settings_terms), stringResource(R.string.settings_terms_sub),
                    onClick = { if (AppLinks.isConfigured(AppLinks.TERMS_OF_SERVICE)) open(AppLinks.TERMS_OF_SERVICE) else infoDialog = "terms" })
                RowDivider(); SettingsRow(Icons.Default.Info, DS.colors.ink2, stringResource(R.string.settings_about), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()), onClick = { infoDialog = "about" })
            } }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(AppLinks.TAGLINE, color = DS.colors.ink2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()), color = DS.colors.ink3, fontSize = 12.sp)
                }
            }
            item { Text(stringResource(R.string.settings_disclaimer), color = DS.colors.ink3, fontSize = 12.sp,
                textAlign = TextAlign.Center, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) }
        }
    }
    if (showAppearance) AppearanceDialog(settings) { showAppearance = false }
    if (showMeals) MealTimesDialog(repository, onDismiss = { showMeals = false })
    if (showPresets) DoseTimePresetsDialog(settings, onDismiss = { showPresets = false })
    if (confirmErase) AlertDialog(onDismissRequest = { confirmErase = false }, title = { Text(stringResource(R.string.settings_erase_title)) }, text = { Text(stringResource(R.string.settings_erase_body)) },
        confirmButton = { TextButton({ repository.eraseAll(); confirmErase = false }) { Text(stringResource(R.string.settings_erase), color = DS.colors.coral) } }, dismissButton = { TextButton({ confirmErase = false }) { Text(stringResource(R.string.action_cancel)) } })
    message?.let { AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton({ message = null }) { Text(stringResource(R.string.action_ok)) } }, title = { Text(stringResource(R.string.app_name)) }, text = { Text(it) }) }
    if (showLanguage) LanguageDialog(settings) { showLanguage = false }
    if (showSound) ReminderSoundDialog(settings, billing.isPro, onShowPaywall) { showSound = false }
    if (showWhatsNew) WhatsNewDialog { showWhatsNew = false }
    infoDialog?.let { kind ->
        val (title, copy) = when (kind) {
            "widgets" -> stringResource(R.string.info_widgets_title) to stringResource(R.string.info_widgets_body)
            "privacy" -> stringResource(R.string.info_privacy_title) to stringResource(R.string.info_privacy_body)
            "terms" -> stringResource(R.string.info_terms_title) to stringResource(R.string.info_terms_body)
            else -> stringResource(R.string.settings_about) to
                stringResource(R.string.info_about_body, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())
        }
        AlertDialog(onDismissRequest = { infoDialog = null }, title = { Text(title) }, text = { Text(copy) }, confirmButton = { TextButton({ infoDialog = null }) { Text(stringResource(R.string.action_done)) } })
    }
}

/**
 * Human name for a stored BCP-47 tag; "system" follows the device.
 *
 * Every name but "System" is an autonym and is deliberately *not* translated:
 * someone hunting for their own language scans for "Français", not "French".
 */
@StringRes internal fun languageTitleRes(tag: String): Int = when (tag) {
    LocaleSupport.SYSTEM -> R.string.settings_language_system
    "en" -> R.string.lang_en
    "ar" -> R.string.lang_ar
    "da" -> R.string.lang_da
    "de" -> R.string.lang_de
    "es" -> R.string.lang_es
    "fr" -> R.string.lang_fr
    "it" -> R.string.lang_it
    "ja" -> R.string.lang_ja
    "ko" -> R.string.lang_ko
    "nb" -> R.string.lang_nb
    "nl" -> R.string.lang_nl
    "pt-BR" -> R.string.lang_pt_br
    "ru" -> R.string.lang_ru
    "sv" -> R.string.lang_sv
    "vi" -> R.string.lang_vi
    "zh-Hans" -> R.string.lang_zh_hans
    "zh-Hant" -> R.string.lang_zh_hant
    else -> R.string.settings_language_system
}

/**
 * The languages MediTick ships translated, in the same order as iOS so the
 * two pickers read identically. Anything added here needs a matching
 * `values-<tag>` folder *and* an entry in `res/xml/locales_config.xml`.
 */
internal val supportedLanguageTags = listOf(
    LocaleSupport.SYSTEM, "en", "ar", "da", "de", "es", "fr", "it", "ja", "ko",
    "nb", "nl", "pt-BR", "ru", "sv", "vi", "zh-Hans", "zh-Hant",
)

@StringRes internal fun alertSoundTitleRes(sound: AlertSound): Int = when (sound) {
    AlertSound.STANDARD -> R.string.sound_standard
    AlertSound.CHIME -> R.string.sound_chime
    AlertSound.BELL -> R.string.sound_bell
    AlertSound.URGENT -> R.string.sound_urgent
    AlertSound.SILENT -> R.string.sound_silent
}

@StringRes internal fun appearanceTitleRes(mode: AppearanceMode): Int = when (mode) {
    AppearanceMode.SYSTEM -> R.string.appearance_auto
    AppearanceMode.LIGHT -> R.string.appearance_daylight
    AppearanceMode.DARK -> R.string.appearance_midnight
}

@StringRes internal fun accentTitleRes(accent: AccentId): Int = when (accent) {
    AccentId.AURORA -> R.string.accent_aurora
    AccentId.OCEAN -> R.string.accent_ocean
    AccentId.ORCHID -> R.string.accent_orchid
    AccentId.EMBER -> R.string.accent_ember
}

/** Chime, Bell and Urgent are part of Pro. */
internal fun alertSoundRequiresPro(sound: AlertSound): Boolean =
    sound == AlertSound.CHIME || sound == AlertSound.BELL || sound == AlertSound.URGENT

/** The community card: an invitation plus the two places conversations happen. */
@Composable
private fun CommunityCard(onOpen: (String) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Text(stringResource(R.string.settings_community_title), color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_community_body),
            color = DS.colors.ink3, fontSize = 12.5.sp, lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (AppLinks.isConfigured(AppLinks.FACEBOOK_GROUP)) {
                Button({ onOpen(AppLinks.FACEBOOK_GROUP) }) { Text(stringResource(R.string.brand_facebook)) }
            }
            if (AppLinks.isConfigured(AppLinks.REDDIT)) {
                OutlinedButton({ onOpen(AppLinks.REDDIT) }) { Text(stringResource(R.string.brand_reddit)) }
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
        title = { Text(stringResource(R.string.settings_language)) },
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
                        Text(stringResource(languageTitleRes(tag)), color = if (settings.languageTag == tag) DS.colors.mint else DS.colors.ink)
                        Spacer(Modifier.weight(1f))
                        if (settings.languageTag == tag) Icon(Icons.Default.Check, null, tint = DS.colors.mint)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_done)) } },
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
        title = { Text(stringResource(R.string.settings_sound_title)) },
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
                        Text(stringResource(alertSoundTitleRes(sound)), color = if (pending == sound) DS.colors.mint else DS.colors.ink)
                        Spacer(Modifier.weight(1f))
                        when {
                            locked -> StatusPill(stringResource(R.string.badge_pro), DS.colors.violet)
                            pending == sound -> Icon(Icons.Default.Check, null, tint = DS.colors.mint)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_sound_hint),
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
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The in-app changelog. */
@Composable
private fun WhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_whats_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReleaseNotes.entries.forEach { entry ->
                    Text(
                        listOfNotNull(stringResource(entry.version), entry.date.ifBlank { null }).joinToString(" · "),
                        color = DS.colors.ink, fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(entry.lead), color = DS.colors.ink2, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    entry.bullets.forEach { bullet ->
                        Text("• " + stringResource(bullet), color = DS.colors.ink3, fontSize = 12.5.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 20.dp)) { SectionLabel(label, Modifier.padding(bottom = 8.dp)); GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp), content = content) }
}

@Composable
private fun AppearanceDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings_appearance)) }, text = {
        Column {
            SectionLabel(stringResource(R.string.settings_appearance_mode)); Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { AppearanceMode.entries.forEach { SelectChip(stringResource(appearanceTitleRes(it)), settings.appearance == it, { settings.updateAppearance(it) }) } }
            Spacer(Modifier.height(20.dp)); SectionLabel(stringResource(R.string.settings_appearance_accent)); Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { AccentId.entries.forEach { accent ->
                GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, onClick = { settings.updateAccent(accent) }, contentPadding = PaddingValues(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(accentBrush(accent))); Spacer(Modifier.width(11.dp))
                        Text(stringResource(accentTitleRes(accent)), color = DS.colors.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(if (settings.accent == accent) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (settings.accent == accent) DS.colors.mint else DS.colors.ink3)
                    }
                }
            } }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_done)) } })
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings_meal_times)) }, text = {
        Column { MealSlot.entries.forEach { slot ->
            val time = times.time(slot)
            SettingsRow(when (slot) { MealSlot.breakfast -> Icons.Default.WbSunny; MealSlot.lunch -> Icons.Default.LightMode; MealSlot.dinner -> Icons.Default.NightsStay; MealSlot.bedtime -> Icons.Default.Bedtime },
                when (slot) { MealSlot.breakfast -> DS.colors.amber; MealSlot.lunch -> DS.colors.cyan; MealSlot.dinner -> DS.colors.violet; MealSlot.bedtime -> DS.colors.mint },
                slot.title(context), time.label(context), onClick = {
                    android.app.TimePickerDialog(context, { _, h, m -> times = times.withTime(slot, TimeOfDay(h, m)) }, time.hour, time.minute, false).show()
                })
        } }
    }, confirmButton = { TextButton({ repository.setMealTimes(times); onDismiss() }) { Text(stringResource(R.string.action_save)) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun DoseTimePresetsDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val labels = dayPartLabels()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.settings_dose_presets)) }, text = {
        Column {
            settings.doseTimePresets.all().forEachIndexed { index, time ->
                SettingsRow(Icons.Default.Schedule, if (index % 2 == 0) DS.colors.mint else DS.colors.cyan, labels[index], time.label(context), onClick = {
                    android.app.TimePickerDialog(context, { _, hour, minute -> settings.setDoseTimePreset(index, TimeOfDay(hour, minute)) }, time.hour, time.minute, false).show()
                })
            }
            TextButton({ settings.restoreDoseTimePresets() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_dose_presets_restore)) }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_done)) } })
}
