package com.kabi.pillpal.meditick

import android.content.Context
import android.text.format.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/**
 * Formats a date from a *skeleton* rather than a fixed pattern.
 *
 * A literal pattern like `"MMM d, yyyy"` bakes in English field order and
 * punctuation: the same call renders "Jan 5, 2026" in English but must read
 * "2026年1月5日" in Japanese and "5. Jan. 2026" in German. A skeleton names only
 * the *fields* wanted, and the platform reorders them for the active locale.
 */
private fun formatSkeleton(millis: Long, skeleton: String): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

/** "Jan 5" — day and abbreviated month. */
fun formatShortDate(millis: Long): String = formatSkeleton(millis, "MMMd")

/** "Jan 5, 2026" — the full calendar date. */
fun formatMediumDate(millis: Long): String = formatSkeleton(millis, "MMMdy")

/** "Mon, Jan 5" — abbreviated weekday with the date. */
fun formatWeekdayDate(millis: Long): String = formatSkeleton(millis, "EEEMMMd")

/** "Monday · Jan 5" — the Today header. */
fun formatFullWeekdayDate(millis: Long): String = formatSkeleton(millis, "EEEEMMMd")

/** "January 2026" — the calendar month heading. */
fun formatMonthYear(millis: Long): String = formatSkeleton(millis, "MMMMy")

/** The one-letter column heading for a weekday, e.g. "M" — locale's own initial. */
fun weekdayInitial(millis: Long): String = formatSkeleton(millis, "EEEEE")
