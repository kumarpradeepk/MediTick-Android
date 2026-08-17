@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
            item { Spacer(Modifier.statusBarsPadding().height(1.dp)); Text("Progress", style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink) }
            if (data.medications.isEmpty()) item {
                Spacer(Modifier.height(55.dp)); FriendlyEmptyState(Icons.Default.BarChart, "Your story starts soon",
                    "Add a medication and log your first doses — streaks, rates and trends grow here.")
            } else {
                item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(DS.colors.glass2).padding(4.dp)) {
                        RangeChoice("7 days", !allTime, Modifier.weight(1f)) { allTime = false }
                        RangeChoice("All time", allTime, Modifier.weight(1f)) { if (isPro) allTime = true else onRequirePro() }
                    }
                }
                item { ProgressHero(stats, if (allTime) "ALL TIME" else "LAST 7 DAYS") { detail = "adherence" } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(Icons.Default.Timer, DS.colors.cyan, if (hasTakenLogs) "${(onTime * 100).toInt()}%" else "—", "On time", "±${settings.onTimeWindowMinutes} min", Modifier.weight(1f)) { detail = "timing" }
                        MetricTile(Icons.Default.LocalFireDepartment, DS.colors.amber, "$streak", "Day streak", "Best $best", Modifier.weight(1f)) { detail = "streak" }
                    }
                }
                item { InsightCard(data, stats) }
                item { SectionLabel(if (allTime) "All days calendar" else SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(DoseEngine.addDays(today, -6))) + " – " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(today))) }
                if (allTime) item { MonthCard(repository, displayedMonth, selectedDay, { displayedMonth = it }, { selectedDay = it }) }
                else item { WeekCard(repository, selectedDay) { selectedDay = it } }
                item { SectionLabel("Logs · ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(selectedDay))}") }
                item { DayLogs(repository, selectedDoses, selectedDay, settings.onTimeWindowMinutes) }
            }
        }
    }
    detail?.let { kind -> ProgressDetailDialog(kind, stats, onTime, streak, best, settings, rangeLogs, needsAttention, { detail = null }) }
}

@Composable
private fun RangeChoice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(38.dp).clip(RoundedCornerShape(13.dp)).background(if (selected) DS.colors.bg3 else Color.Transparent).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) DS.colors.ink else DS.colors.ink3, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ProgressHero(stats: DoseEngine.Stats, label: String, onClick: () -> Unit) {
    GradientCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(stats.ratio.toFloat(), Modifier.size(120.dp), 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(stats.ratio * 100).toInt()}%", color = DS.colors.ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Text("ADHERENCE", color = DS.colors.ink3, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(label); Spacer(Modifier.height(10.dp))
                LegendDot(DS.colors.mint, "Taken", stats.taken)
                LegendDot(DS.colors.amber, "Skipped", stats.skipped)
                LegendDot(DS.colors.coral, "Missed", stats.missed)
            }
        }
    }
}

@Composable private fun LegendDot(color: Color, title: String, count: Int) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp))
        Text(title, color = DS.colors.ink2, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("$count", color = DS.colors.ink, fontWeight = FontWeight.Bold)
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
        data.medications.any { it.needsRefill } -> "A refill is coming up. ${data.medications.first { it.needsRefill }.name} is below its alert level."
        stats.missed > 0 -> "${stats.missed} ${if (stats.missed == 1) "dose was" else "doses were"} missed in this period. A follow-up nudge may help on busy days."
        stats.skipped > 0 -> "A skipped dose is part of the story, not a failure. Keep logging so your trend stays useful."
        else -> "A calm, consistent week. Every small tick is keeping your routine on track."
    }
    GradientCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.Top) {
            IconTile(Icons.Default.AutoAwesome, DS.colors.violet, 42.dp); Spacer(Modifier.width(12.dp))
            Column { SectionLabel("Smart insight"); Spacer(Modifier.height(4.dp)); Text(message, color = DS.colors.ink2, fontSize = 13.sp, lineHeight = 19.sp) }
        }
    }
}

@Composable
private fun WeekCard(repository: AppRepository, selected: Long, onSelect: (Long) -> Unit) {
    val today = DoseEngine.startOfDay(System.currentTimeMillis())
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (-6..0).forEach { offset ->
                val day = DoseEngine.addDays(today, offset); val stats = DoseEngine.stats(repository.doses(day)); val active = day == selected
                Column(Modifier.width(40.dp).clickable { onSelect(day) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SimpleDateFormat("EE", Locale.getDefault()).format(Date(day)).take(1), color = if (active) DS.colors.mint else DS.colors.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    val color = when { stats.scheduled == 0 -> DS.colors.line2; stats.taken == stats.scheduled -> DS.colors.mint; stats.missed > 0 -> DS.colors.coral; stats.skipped > 0 -> DS.colors.amber; else -> DS.colors.ink3 }
                    Box(Modifier.size(if (active) 28.dp else 24.dp).clip(CircleShape).background(color.copy(if (active) .23f else .12f)), contentAlignment = Alignment.Center) {
                        if (stats.scheduled > 0) Text(if (stats.taken == stats.scheduled) "✓" else "${stats.taken}", color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
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
            IconButton({ onMonthChange(shifted(-1)) }) { Icon(Icons.Default.ChevronLeft, "Previous month", tint = DS.colors.ink2) }
            TextButton({
                android.app.DatePickerDialog(context, { _, year, monthIndex, dayOfMonth ->
                    val chosen = Calendar.getInstance().apply { clear(); set(year, monthIndex, dayOfMonth) }
                    val first = (chosen.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                    onMonthChange(first.timeInMillis); onSelect(chosen.timeInMillis.coerceAtMost(today))
                }, month.get(Calendar.YEAR), month.get(Calendar.MONTH), month.get(Calendar.DAY_OF_MONTH)).show()
            }, Modifier.weight(1f)) {
                Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp))
                Text(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(month.timeInMillis)), textAlign = TextAlign.Center, color = DS.colors.ink, fontWeight = FontWeight.Bold)
            }
            IconButton({ onMonthChange(shifted(1)) }, enabled = monthOrdinal < currentOrdinal) { Icon(Icons.Default.ChevronRight, "Next month", tint = DS.colors.ink2) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
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
    val title = when (kind) { "timing" -> "On-time detail"; "streak" -> "Streak detail"; else -> "Adherence detail" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (kind) {
                    "timing" -> {
                        Text(if (stats.taken == 0) "No taken doses in this range yet." else "${(onTime * 100).toInt()}% of taken doses were within ±${settings.onTimeWindowMinutes} minutes.")
                        val window = settings.onTimeWindowMinutes * 60_000L
                        val taken = rangeLogs.filter { it.status == DoseStatus.taken }
                        val early = taken.count { it.actedAt - it.scheduledAt < -window }
                        val onTimeCount = taken.count { abs(it.actedAt - it.scheduledAt) <= window }
                        val late = taken.count { it.actedAt - it.scheduledAt > window }
                        val skipped = rangeLogs.count { it.status == DoseStatus.skipped }
                        Text("$early early · $onTimeCount on time · $late late · $skipped skipped", color = DS.colors.ink2)
                        Text("Choose the window used by this metric:", color = DS.colors.ink3, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(15, 30, 60).forEach { minutes ->
                                SelectChip("±$minutes min", settings.onTimeWindowMinutes == minutes, { settings.setOnTimeWindow(minutes) })
                            }
                        }
                    }
                    "streak" -> {
                        Text("$streak current day streak · $best best day streak", fontWeight = FontWeight.Bold)
                        Text("A streak day has at least one scheduled dose, with every due dose taken and none skipped or missed.", color = DS.colors.ink3)
                    }
                    else -> {
                        Text("${(stats.ratio * 100).toInt()}% adherence", fontWeight = FontWeight.Bold)
                        Text("${stats.scheduled} scheduled · ${stats.taken} taken · ${stats.skipped} skipped · ${stats.missed} missed · ${stats.pending} pending", color = DS.colors.ink3)
                        Text("Adherence counts taken doses among doses that already have an outcome. Pending future doses do not lower the rate.", color = DS.colors.ink3)
                        HorizontalDivider(color = DS.colors.line)
                        Text("Needs attention", fontWeight = FontWeight.Bold)
                        if (needsAttention.isEmpty()) Text("No skipped or missed doses in this range.", color = DS.colors.ink3)
                        needsAttention.forEach { dose ->
                            Text("${dose.medication.name} · ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(dose.time))} · ${dose.state.name.lowercase()}", color = if (dose.state == DoseState.MISSED) DS.colors.coral else DS.colors.amber, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DayLogs(repository: AppRepository, doses: List<ScheduledDose>, day: Long, onTimeWindow: Int) {
    val prnLogs = repository.logs.filter { it.isAsNeeded && DoseEngine.startOfDay(it.actedAt) == day }
    if (doses.isEmpty() && prnLogs.isEmpty()) {
        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) { Text("Nothing scheduled this day.", color = DS.colors.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        return
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
        doses.forEachIndexed { index, dose ->
            if (index > 0) RowDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                MedicationIcon(dose.medication, 38.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(dose.medication.name, color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(TimeOfDay.fromEpoch(dose.time).label(), color = DS.colors.ink3, fontSize = 11.sp) }
                val text = when (dose.state) { DoseState.TAKEN -> dose.log?.let { if (abs(it.actedAt - it.scheduledAt) <= onTimeWindow * 60_000L) "On time" else if (it.actedAt < it.scheduledAt) "Early" else "Late" } ?: "Taken"; else -> dose.state.name.lowercase().replaceFirstChar { it.uppercase() } }
                StatusPill(text, when (dose.state) { DoseState.TAKEN -> DS.colors.mint; DoseState.SKIPPED -> DS.colors.amber; DoseState.MISSED -> DS.colors.coral; else -> DS.colors.ink3 })
            }
        }
        prnLogs.forEach { log ->
            RowDivider(); Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                val med = repository.medication(log.medicationID); med?.let { MedicationIcon(it, 38.dp) }; Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(med?.name ?: "Medication", color = DS.colors.ink, fontWeight = FontWeight.Bold); Text("As needed · ${prettyNumber(log.amount)}", color = DS.colors.ink3, fontSize = 11.sp) }
                IconButton({ repository.deleteLog(log.id) }) { Icon(Icons.Default.DeleteOutline, "Delete log", tint = DS.colors.ink3) }
            }
        }
    }
}
