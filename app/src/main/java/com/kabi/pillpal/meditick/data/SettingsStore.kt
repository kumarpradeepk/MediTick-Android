package com.kabi.pillpal.meditick.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kabi.pillpal.meditick.model.TimeOfDay

enum class AppearanceMode { SYSTEM, LIGHT, DARK }
enum class AccentId { AURORA, OCEAN, ORCHID, EMBER }
enum class AlertSound { STANDARD, CHIME, BELL, URGENT, SILENT }

data class DoseTimePresets(
    val morning: TimeOfDay = TimeOfDay(8, 0),
    val midday: TimeOfDay = TimeOfDay(12, 0),
    val evening: TimeOfDay = TimeOfDay(18, 0),
    val bedtime: TimeOfDay = TimeOfDay(22, 0),
) {
    fun all() = listOf(morning, midday, evening, bedtime)
}

class SettingsStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("meditick-settings", Context.MODE_PRIVATE)

    var hasCompletedOnboarding by mutableStateOf(prefs.getBoolean("onboarding", false)); private set
    var appearance by mutableStateOf(enumValueOr(AppearanceMode.SYSTEM, prefs.getString("appearance", null))); private set
    var accent by mutableStateOf(enumValueOr(AccentId.AURORA, prefs.getString("accent", null))); private set
    var remindersEnabled by mutableStateOf(prefs.getBoolean("reminders", true)); private set
    var followUpEnabled by mutableStateOf(prefs.getBoolean("follow_up", true)); private set
    var nudgeDelayMinutes by mutableStateOf(prefs.getInt("nudge_delay", 15)); private set
    var timeSensitiveEnabled by mutableStateOf(prefs.getBoolean("time_sensitive", true)); private set
    var refillRemindersEnabled by mutableStateOf(prefs.getBoolean("refill", true)); private set
    var hapticsEnabled by mutableStateOf(prefs.getBoolean("haptics", true)); private set
    var onTimeWindowMinutes by mutableStateOf(prefs.getInt("on_time", 30)); private set
    var hideMedicationNames by mutableStateOf(prefs.getBoolean("hide_medication_names", false)); private set
    var alertSound by mutableStateOf(enumValueOr(AlertSound.STANDARD, prefs.getString("alert_sound", null))); private set
    var languageTag by mutableStateOf(prefs.getString("language_tag", "system") ?: "system"); private set
    var doseTimePresets by mutableStateOf(loadDosePresets(prefs)); private set

    fun completeOnboarding() { hasCompletedOnboarding = true; save("onboarding", true) }
    fun resetOnboarding() { hasCompletedOnboarding = false; save("onboarding", false) }
    fun updateAppearance(value: AppearanceMode) { appearance = value; save("appearance", value.name) }
    fun updateAccent(value: AccentId) { accent = value; save("accent", value.name) }
    fun setReminders(value: Boolean) { remindersEnabled = value; save("reminders", value) }
    fun setFollowUp(value: Boolean) { followUpEnabled = value; save("follow_up", value) }
    fun setNudgeDelay(value: Int) { nudgeDelayMinutes = value; save("nudge_delay", value) }
    fun setTimeSensitive(value: Boolean) { timeSensitiveEnabled = value; save("time_sensitive", value) }
    fun setRefillReminders(value: Boolean) { refillRemindersEnabled = value; save("refill", value) }
    fun setHaptics(value: Boolean) { hapticsEnabled = value; save("haptics", value) }
    fun setOnTimeWindow(value: Int) { onTimeWindowMinutes = value; save("on_time", value) }
    fun updateHideMedicationNames(value: Boolean) { hideMedicationNames = value; save("hide_medication_names", value) }
    fun updateAlertSound(value: AlertSound) { alertSound = value; save("alert_sound", value.name) }
    fun updateLanguageTag(value: String) { languageTag = value; save("language_tag", value) }
    fun setDoseTimePreset(index: Int, value: TimeOfDay) {
        doseTimePresets = when (index) {
            0 -> doseTimePresets.copy(morning = value)
            1 -> doseTimePresets.copy(midday = value)
            2 -> doseTimePresets.copy(evening = value)
            else -> doseTimePresets.copy(bedtime = value)
        }
        save("dose_preset_$index", value.totalMinutes)
    }
    fun restoreDoseTimePresets() {
        doseTimePresets = DoseTimePresets()
        doseTimePresets.all().forEachIndexed { index, value -> save("dose_preset_$index", value.totalMinutes) }
    }

    private fun save(key: String, value: Any) = prefs.edit().apply {
        when (value) { is Boolean -> putBoolean(key, value); is Int -> putInt(key, value); else -> putString(key, value.toString()) }
    }.apply()

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore = instance ?: synchronized(this) {
            instance ?: SettingsStore(context.applicationContext).also { instance = it }
        }
        private inline fun <reified T : Enum<T>> enumValueOr(fallback: T, raw: String?): T =
            enumValues<T>().firstOrNull { it.name == raw } ?: fallback

        private fun loadDosePresets(prefs: android.content.SharedPreferences): DoseTimePresets {
            fun time(index: Int, fallback: TimeOfDay): TimeOfDay {
                val minutes = prefs.getInt("dose_preset_$index", fallback.totalMinutes).coerceIn(0, 1439)
                return TimeOfDay(minutes / 60, minutes % 60)
            }
            return DoseTimePresets(
                morning = time(0, TimeOfDay(8, 0)), midday = time(1, TimeOfDay(12, 0)),
                evening = time(2, TimeOfDay(18, 0)), bedtime = time(3, TimeOfDay(22, 0)),
            )
        }
    }
}
