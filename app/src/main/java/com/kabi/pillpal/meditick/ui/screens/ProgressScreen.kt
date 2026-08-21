@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.formatMonthYear
import com.kabi.pillpal.meditick.formatPercent
import com.kabi.pillpal.meditick.formatShortDate
import com.kabi.pillpal.meditick.formatTime
import com.kabi.pillpal.meditick.weekdayInitial
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun ProgressScreen(repository: AppRepository, settings: SettingsStore, isPro: Boolean, onRequirePro: () -> Unit) {
    val data = repository.data
    var allTime by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableLongStateOf(DoseEngine.startOfDay(System.currentTimeMillis())) }
    var displayedMonth by remember { mutableLongStateOf(DoseEngine.startOfDay(System.currentTimeMillis())) }
    var detail by remember { mutableStateOf<String?>(null) }
    val today = DoseEngine.startOfDay(System.currentTimeMillis())
    val earliestTrackedDay = remember(data) {
        DoseEngine.startOfDay((data.logs.map { it.scheduledAt } + data.medications.flatMap { listOf(it.createdAt, it.schedule.startDate) }).minOrNull() ?: today)
    }
    val trackedDays = (DoseEngine.calendarDayDistance(earliestTrackedDay, today) + 1).coerceAtLeast(1)
    val start = if (allTime) {
        earliestTrackedDay
    } else DoseEngine.addDays(today, -6)
    val stats = remember(data, allTime) { DoseEngine.stats(start, today, data.medications, data.mealTimes, data.logs) }
    val onTime = remember(data, allTime, settings.onTimeWindowMinutes) { DoseEngine.onTimeRate(data.logs, settings.onTimeWindowMinutes, start) }
    val hasTakenLogs = remember(data, start) { data.logs.any { !it.isAsNeeded && it.status == DoseStatus.taken && it.scheduledAt >= start } }
    val streak = remember(data, trackedDays) { DoseEngine.currentStreak(data, maxDays = trackedDays) }
    val best = remember(data, trackedDays) { DoseEngine.bestStreak(data, maxDays = trackedDays) }
    val selectedDoses = remember(data, selectedDay) { repository.doses(selectedDay) }
    val rangeLogs = remember(data, start, today) {
        data.logs.filter { !it.isAsNeeded && it.scheduledAt >= start && it.scheduledAt < DoseEngine.addDays(today, 1) }
    }
    val needsAttention = remember(data, start, today) {
        buildList {
            var cursor = start
            while (cursor <= today) {
                addAll(repository.doses(cursor).filter { it.state == DoseState.MISSED || it.state == DoseState.SKIPPED })
                cursor = DoseEngine.addDays(cursor, 1)
            }
        }.sortedByDescending { it.time }.take(6)
    }

    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 126.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Spacer(Modifier.statusBarsPadding().height(1.dp)); Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink, modifier = Modifier.appearFluidly(0)) }
            if (data.medications.isEmpty()) item {
                Spacer(Modifier.height(55.dp)); FriendlyEmptyState(Icons.Default.BarChart, stringResource(R.string.progress_empty_title),
                    stringResource(R.string.progress_empty_body))
            } else {
                item {
                    Row(Modifier.fillMaxWidth().appearFluidly(1).clip(RoundedCornerShape(16.dp)).background(DS.colors.glass2).padding(4.dp)) {
                        RangeChoice(stringResource(R.string.progress_range_week), !allTime, Modifier.weight(1f)) { allTime = false }
                        RangeChoice(stringResource(R.string.progress_range_all), allTime, Modifier.weight(1f)) { if (isPro) allTime = true else onRequirePro() }
                    }
                }
                item { SectionLabel(stringResource(R.string.progress_health_insights), Modifier.appearFluidly(2)) }
                item {
                    Box(Modifier.appearFluidly(2)) {
                        ProgressHero(stats, stringResource(if (allTime) R.string.progress_range_label_all else R.string.progress_range_label_week)) { detail = "adherence" }
                    }
                }
                item {
                    Row(Modifier.appearFluidly(3), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Momentum first: the streak leads, timing follows.
                        StreakTile(streak, best, Modifier.weight(1f)) { detail = "streak" }
                        // On-time: until a few doses are taken there is no
                        // rate to show — the tile says so instead.
                        if (hasTakenLogs) MetricTile(
                            Icons.Default.Timer, DS.colors.cyan,
                            formatPercent((onTime * 100).toInt()),
                            stringResource(R.string.progress_metric_on_time),
                            stringResource(R.string.progress_metric_window, settings.onTimeWindowMinutes),
                            Modifier.weight(1f),
                        ) { detail = "timing" }
                        else InsightsSoonTile(Modifier.weight(1f)) { detail = "timing" }
                    }
                }
                item { InsightCard(data, stats) }
                item { PatternCard(data, if (allTime) DoseEngine.addDays(today, -364) else DoseEngine.addDays(today, -6), today) }
                if (allTime) item { SectionLabel(stringResource(R.string.progress_calendar_all_days)) }
                if (allTime) item { MonthCard(repository, displayedMonth, selectedDay, { displayedMonth = it }, { selectedDay = it }) }
                else item { WeekCard(repository, selectedDay) { selectedDay = it } }
                item { SectionLabel(stringResource(R.string.progress_logs_for_day, formatShortDate(selectedDay))) }
                item { DayLogs(repository, selectedDoses, selectedDay, settings.onTimeWindowMinutes) }
            }
        }
    }
    detail?.let { kind -> ProgressDetailDialog(kind, stats, onTime, streak, best, settings, rangeLogs, needsAttention, { detail = null }) }
}

@Composable
private fun RangeChoice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    // The active segment fades between states instead of snapping.
    val fill by androidx.compose.animation.animateColorAsState(if (selected) DS.colors.bg3 else Color.Transparent, androidx.compose.animation.core.tween(200), label = "rangeFill")
    val label by androidx.compose.animation.animateColorAsState(if (selected) DS.colors.ink else DS.colors.ink3, androidx.compose.animation.core.tween(200), label = "rangeLabel")
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(13.dp)).background(fill)
            .clickable { if (!selected) haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ProgressHero(stats: DoseEngine.Stats, label: String, onClick: () -> Unit) {
    GradientCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(stats.ratio.toFloat(), Modifier.size(108.dp), 13.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatPercent((stats.ratio * 100).toInt()), color = DS.colors.ink, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.progress_taken_word), color = DS.colors.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel(label); Spacer(Modifier.height(10.dp))
                    LegendDot(DS.colors.mint, stringResource(R.string.state_taken), stats.taken)
                    LegendDot(DS.colors.amber, stringResource(R.string.state_skipped), stats.skipped)
                    LegendDot(DS.colors.coral, stringResource(R.string.state_missed), stats.missed)
                }
            }
            Spacer(Modifier.height(12.dp))
            // The plain-words reading, straight from the reference app.
            Text(
                stringResource(R.string.progress_took_doses, stats.taken, stats.scheduled),
                color = DS.colors.ink2, fontSize = 13.sp,
            )
        }
    }
}

/** The on-time tile before any dose exists: a promise, not a dash. */
@Composable
private fun InsightsSoonTile(modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        IconTile(Icons.Default.Timer, DS.colors.cyan, 38.dp)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.progress_metric_on_time), color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Icon(Icons.Default.AutoAwesome, null, tint = DS.colors.ink3, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.progress_insights_soon), color = DS.colors.ink3, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** Current streak, with the best run in mint under an up-right arrow. */
@Composable
private fun StreakTile(streak: Int, best: Int, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        IconTile(Icons.Default.LocalFireDepartment, DS.colors.amber, 38.dp)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.progress_metric_streak), color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(streak.toString(), color = DS.colors.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.progress_days_word), color = DS.colors.ink3, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = DS.colors.mint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.progress_best_days, best), color = DS.colors.mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable private fun LegendDot(color: Color, title: String, count: Int) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp))
        Text(title, color = DS.colors.ink2, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(count.toString(), color = DS.colors.ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, value: String, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        IconTile(icon, tint, 38.dp); Spacer(Modifier.height(12.dp))
        Text(value, color = DS.colors.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(subtitle, color = DS.colors.ink3, fontSize = 10.sp)
    }
}

@Composable
private fun InsightCard(data: AppData, stats: DoseEngine.Stats) {
    val message = when {
        data.medications.any { it.needsRefill } ->
            stringResource(R.string.progress_insight_refill, data.medications.first { it.needsRefill }.name)
        stats.missed > 0 ->
            pluralStringResource(R.plurals.progress_insight_missed, stats.missed, stats.missed)
        stats.skipped > 0 -> stringResource(R.string.progress_insight_skipped)
        else -> stringResource(R.string.progress_insight_calm)
    }
    GradientCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.Top) {
            IconTile(Icons.Default.AutoAwesome, DS.colors.violet, 42.dp); Spacer(Modifier.width(12.dp))
            Column { SectionLabel(stringResource(R.string.progress_insight_label)); Spacer(Modifier.height(4.dp)); Text(message, color = DS.colors.ink2, fontSize = 13.sp, lineHeight = 19.sp) }
        }
    }
}

/** "Morning is the hardest time" — where the misses actually cluster. */
@Composable
internal fun PatternCard(data: AppData, from: Long, to: Long) {
    val pattern = remember(data.logs.size, data.medications.size, from, to) {
        DoseEngine.hardestSlot(from, to, data.medications, data.mealTimes, data.logs)
    } ?: return
    val slotName = stringResource(
        listOf(
            R.string.daypart_morning, R.string.daypart_midday,
            R.string.daypart_evening, R.string.daypart_bedtime,
        )[pattern.slot],
    )
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(Icons.Default.Insights, DS.colors.amber, 42.dp); Spacer(Modifier.width(12.dp))
            Column {
                SectionLabel(stringResource(R.string.progress_patterns_label)); Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.progress_pattern_hardest, slotName), color = DS.colors.ink, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.progress_pattern_detail, pattern.missed, pattern.total, slotName),
                    color = DS.colors.ink3, fontSize = 12.sp, lineHeight = 18.sp,
                )
            }
        }
    }
}

/** A day's four-way legend state, the reference app's reading of a day. */
@Composable
private fun dayLegendColor(stats: DoseEngine.Stats): androidx.compose.ui.graphics.Color? = when {
    stats.scheduled == 0 -> null
    stats.taken == stats.scheduled -> DS.colors.mint
    stats.missed > 0 -> DS.colors.coral
    else -> DS.colors.amber
}

@Composable
private fun WeekCard(repository: AppRepository, selected: Long, onSelect: (Long) -> Unit) {
    val context = LocalContext.current
    val today = DoseEngine.startOfDay(System.currentTimeMillis())
    val haptics = rememberHaptics()
    Column {
        // The range this strip covers, centered above it.
        Text(
            stringResource(
                R.string.progress_calendar_range,
                formatShortDate(DoseEngine.addDays(today, -6)), formatShortDate(today),
            ),
            color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (-6..0).forEach { offset ->
                    val day = DoseEngine.addDays(today, offset)
                    val stats = DoseEngine.stats(repository.doses(day))
                    val active = day == selected
                    // MediTick's signature: the day itself is a mini daily
                    // ring — the legend color plus how far the day got.
                    val tint = dayLegendColor(stats)
                    val ratio = if (stats.scheduled == 0) 0f else stats.taken.toFloat() / stats.scheduled
                    Column(
                        Modifier.width(44.dp).clip(RoundedCornerShape(14.dp)).clickable { haptics.tick(); onSelect(day) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(weekdayInitial(day).uppercase(), color = if (active) DS.colors.mint else DS.colors.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                        Spacer(Modifier.height(7.dp))
                        MiniDayRing(
                            number = SimpleDateFormat("d", Locale.getDefault()).format(Date(day)),
                            progress = if (tint == DS.colors.mint) 1f else ratio,
                            tint = tint, selected = active,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AdherenceLegend()
    }
}

/** Complete · Partial · Missed · No doses — the strip's color key. */
@Composable
private fun AdherenceLegend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable fun key(color: androidx.compose.ui.graphics.Color?, label: String) {
            if (color != null) Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            else Box(Modifier.size(7.dp).clip(CircleShape).border(1.dp, DS.colors.ink3, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(label, color = DS.colors.ink3, fontSize = 11.sp)
            Spacer(Modifier.width(12.dp))
        }
        key(DS.colors.mint, stringResource(R.string.progress_legend_complete))
        key(DS.colors.amber, stringResource(R.string.progress_legend_partial))
        key(DS.colors.coral, stringResource(R.string.progress_legend_missed))
        key(null, stringResource(R.string.progress_legend_none))
    }
}

@Composable
private fun MonthCard(
    repository: AppRepository,
    displayedMonth: Long,
    selected: Long,
    onMonthChange: (Long) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val today = DoseEngine.startOfDay(System.currentTimeMillis())
    val month = Calendar.getInstance().apply {
        timeInMillis = displayedMonth
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val currentMonth = Calendar.getInstance().apply { timeInMillis = today }
    val monthOrdinal = month.get(Calendar.YEAR) * 12 + month.get(Calendar.MONTH)
    val currentOrdinal = currentMonth.get(Calendar.YEAR) * 12 + currentMonth.get(Calendar.MONTH)
    fun shifted(amount: Int): Long = (month.clone() as Calendar).apply { add(Calendar.MONTH, amount) }.timeInMillis

    val leading = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val cells = MutableList<Long?>(leading) { null }.apply {
        repeat(month.getActualMaximum(Calendar.DAY_OF_MONTH)) { dayIndex ->
            add((month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, dayIndex + 1) }.timeInMillis)
        }
        while (size % 7 != 0) add(null)
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onMonthChange(shifted(-1)) }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.progress_previous_month), tint = DS.colors.ink2) }
            TextButton({
                android.app.DatePickerDialog(context, { _, year, monthIndex, dayOfMonth ->
                    val chosen = Calendar.getInstance().apply { clear(); set(year, monthIndex, dayOfMonth) }
                    val first = (chosen.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                    onMonthChange(first.timeInMillis); onSelect(chosen.timeInMillis.coerceAtMost(today))
                }, month.get(Calendar.YEAR), month.get(Calendar.MONTH), month.get(Calendar.DAY_OF_MONTH)).show()
            }, Modifier.weight(1f)) {
                Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
                Text(formatMonthYear(month.timeInMillis), textAlign = TextAlign.Center, color = DS.colors.ink, fontWeight = FontWeight.Bold)
            }
            IconButton({ onMonthChange(shifted(1)) }, enabled = monthOrdinal < currentOrdinal) { Icon(Icons.Default.ChevronRight, stringResource(R.string.progress_next_month), tint = DS.colors.ink2) }
        }
        Row(Modifier.fillMaxWidth()) {
            mondayFirstInitials().forEach { label ->
                Text(label, Modifier.weight(1f), color = DS.colors.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(5.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { dayValue ->
                    if (dayValue == null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                        val dayStats = DoseEngine.stats(repository.doses(dayValue))
                        val active = dayValue == selected
                        val tint = when {
                            dayStats.scheduled == 0 -> DS.colors.line2
                            dayStats.taken == dayStats.scheduled -> DS.colors.mint
                            dayStats.missed > 0 -> DS.colors.coral
                            dayStats.skipped > 0 -> DS.colors.amber
                            else -> DS.colors.cyan
                        }
                        Box(
                            Modifier.weight(1f).padding(2.dp).aspectRatio(1f).clip(CircleShape)
                                .background(tint.copy(alpha = if (active) .28f else if (dayStats.scheduled > 0) .12f else .04f))
                                .clickable(enabled = dayValue <= today) { onSelect(dayValue) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(Calendar.getInstance().apply { timeInMillis = dayValue }.get(Calendar.DAY_OF_MONTH).toString(), color = if (active) tint else DS.colors.ink2, fontSize = 11.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressDetailDialog(
    kind: String,
    stats: DoseEngine.Stats,
    onTime: Double,
    streak: Int,
    best: Int,
    settings: SettingsStore,
    rangeLogs: List<DoseLog>,
    needsAttention: List<ScheduledDose>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val title = stringResource(
        when (kind) {
            "timing" -> R.string.progress_detail_timing_title
            "streak" -> R.string.progress_detail_streak_title
            else -> R.string.progress_detail_adherence_title
        },
    )
    AppSheet(onDismiss, title = title) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (kind) {
                    "timing" -> {
                        Text(
                            if (stats.taken == 0) stringResource(R.string.progress_detail_no_taken)
                            else stringResource(
                                R.string.progress_detail_on_time_rate,
                                formatPercent((onTime * 100).toInt()), settings.onTimeWindowMinutes,
                            ),
                        )
                        val window = settings.onTimeWindowMinutes * 60_000L
                        val taken = rangeLogs.filter { it.status == DoseStatus.taken }
                        val early = taken.count { it.actedAt - it.scheduledAt < -window }
                        val onTimeCount = taken.count { abs(it.actedAt - it.scheduledAt) <= window }
                        val late = taken.count { it.actedAt - it.scheduledAt > window }
                        val skipped = rangeLogs.count { it.status == DoseStatus.skipped }
                        Text(stringResource(R.string.progress_detail_breakdown, early, onTimeCount, late, skipped), color = DS.colors.ink2)
                        Text(stringResource(R.string.progress_detail_choose_window), color = DS.colors.ink3, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(15, 30, 60).forEach { minutes ->
                                SelectChip(stringResource(R.string.progress_metric_window, minutes), settings.onTimeWindowMinutes == minutes, { settings.setOnTimeWindow(minutes) })
                            }
                        }
                    }
                    "streak" -> {
                        Text(stringResource(R.string.progress_detail_streak_line, streak, best), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.progress_detail_streak_body), color = DS.colors.ink3)
                    }
                    else -> {
                        Text(stringResource(R.string.progress_detail_adherence_rate, formatPercent((stats.ratio * 100).toInt())), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                R.string.progress_detail_counts,
                                stats.scheduled, stats.taken, stats.skipped, stats.missed, stats.pending,
                            ),
                            color = DS.colors.ink3,
                        )
                        Text(stringResource(R.string.progress_detail_adherence_body), color = DS.colors.ink3)
                        HorizontalDivider(color = DS.colors.line)
                        Text(stringResource(R.string.progress_detail_needs_attention), fontWeight = FontWeight.Bold)
                        if (needsAttention.isEmpty()) Text(stringResource(R.string.progress_detail_no_attention), color = DS.colors.ink3)
                        needsAttention.forEach { dose ->
                            Text(
                                stringResource(
                                    R.string.progress_detail_attention_row,
                                    dose.medication.name,
                                    formatShortDate(dose.time) + " " + formatTime(context, dose.time),
                                    stringResource(doseStateRes(dose.state)),
                                ),
                                color = if (dose.state == DoseState.MISSED) DS.colors.coral else DS.colors.amber, fontSize = 12.sp,
                            )
                        }
                    }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.action_done), onDismiss, Modifier.fillMaxWidth())
    }
}

/**
 * Monday-first column headings in the active locale's own initials.
 * 2024-01-01 was a Monday, so stepping a week from it names every weekday
 * without hardcoding "M T W T F S S" — which is wrong everywhere but English.
 */
@Composable
private fun mondayFirstInitials(): List<String> {
    val monday = remember {
        Calendar.getInstance().apply {
            clear(); set(2024, Calendar.JANUARY, 1, 12, 0, 0)
        }.timeInMillis
    }
    return remember(Locale.getDefault()) { (0..6).map { weekdayInitial(monday + it * 86_400_000L) } }
}

@Composable
private fun DayLogs(repository: AppRepository, doses: List<ScheduledDose>, day: Long, onTimeWindow: Int) {
    val context = LocalContext.current
    val prnLogs = repository.logs.filter { it.isAsNeeded && DoseEngine.startOfDay(it.actedAt) == day }
    if (doses.isEmpty() && prnLogs.isEmpty()) {
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 30.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EventBusy, null, tint = DS.colors.ink3, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.progress_no_logs_day), color = DS.colors.ink3, textAlign = TextAlign.Center)
            }
        }
        return
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
        doses.forEachIndexed { index, dose ->
            if (index > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                MedicationIcon(dose.medication, 38.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(dose.medication.name, color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(TimeOfDay.fromEpoch(dose.time).label(context), color = DS.colors.ink3, fontSize = 11.sp) }
                val text = stringResource(
                    when (dose.state) {
                        DoseState.TAKEN -> dose.log?.let {
                            when {
                                abs(it.actedAt - it.scheduledAt) <= onTimeWindow * 60_000L -> R.string.state_on_time
                                it.actedAt < it.scheduledAt -> R.string.state_early
                                else -> R.string.state_late
                            }
                        } ?: R.string.state_taken
                        else -> doseStateRes(dose.state)
                    },
                )
                StatusPill(text, when (dose.state) { DoseState.TAKEN -> DS.colors.mint; DoseState.SKIPPED -> DS.colors.amber; DoseState.MISSED -> DS.colors.coral; else -> DS.colors.ink3 })
            }
        }
        prnLogs.forEach { log ->
            RowDivider(); Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                val med = repository.medication(log.medicationID); med?.let { MedicationIcon(it, 38.dp) }; Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(med?.name ?: stringResource(R.string.progress_generic_medication), color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(stringResource(R.string.progress_as_needed_log, prettyNumber(log.amount)), color = DS.colors.ink3, fontSize = 11.sp) }
                IconButton({ repository.deleteLog(log.id) }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.progress_delete_log), tint = DS.colors.ink3) }
            }
        }
    }
}
