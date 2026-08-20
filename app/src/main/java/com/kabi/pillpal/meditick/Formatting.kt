package com.kabi.pillpal.meditick

import android.content.Context
import android.text.format.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * Locale-aware formatting helpers.
 *
 * The app used to build these by hand — `"$percent%"`, `SimpleDateFormat("h:mm a")` —
 * which reads wrong in most of the locales MediTick now ships in: French and Arabic
 * space or place the percent sign differently, and everywhere outside the US/UK a
 * 24-hour clock is the norm. These wrappers defer to the platform, which already
 * knows both the active locale and the user's own 12h/24h preference.
 */

/** `72%` in English, `72 %` in French, `٧٢٪` shaping in Arabic — whatever the locale wants. */
fun formatPercent(wholePercent: Int): String =
    NumberFormat.getPercentInstance().format(wholePercent / 100.0)

/** Formats a wall-clock time using the device's 12h/24h setting and active locale. */
fun formatTime(context: Context, millis: Long): String =
    DateFormat.getTimeFormat(context).format(Date(millis))

/** Formats an hour/minute pair using the device's 12h/24h setting and active locale. */
fun formatTime(context: Context, hour: Int, minute: Int): String {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
