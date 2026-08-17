@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.notifications.NotificationScheduler
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private enum class DayPart(val title: String) { MORNING("Morning"), MIDDAY("Midday"), EVENING("Evening"), BEDTIME("Bedtime") }

@Composable
fun TodayScreen(repository: AppRepository, onAdd: () -> Unit, onMedication: (String) -> Unit) {
    val snapshot = repository.data
    var selectedDay by remember { mutableLongStateOf(DoseEngine.startOfDay(System.currentTimeMillis())) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedDose by remember { mutableStateOf<ScheduledDose?>(null) }
    var prnMedication by remember { mutableStateOf<Medication?>(null) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }

    val doses = remember(snapshot, selectedDay, now) { repository.doses(selectedDay, now) }
    val stats = DoseEngine.stats(doses)
    val isToday = DoseEngine.startOfDay(now) == selectedDay
    val isFuture = selectedDay > DoseEngine.startOfDay(now)
    ScreenBackground {
        if (repository.activeMedications.isEmpty()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FriendlyEmptyState(Icons.Default.Medication, "Let’s build your routine",
                    "Describe a medication like you’d say it out loud — MediTick turns it into a schedule, reminders and a daily ring.",
                    "Add your first medication", onAdd)
                Spacer(Modifier.height(12.dp)); Text("Takes about 30 seconds.", color = DS.colors.ink3, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 126.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(Modifier.statusBarsPadding().height(1.dp)); Header(now, onAdd) }
                item { DayStrip(repository, selectedDay) { selectedDay = it } }
                item { HeroCard(stats, doses.firstOrNull { it.state == DoseState.DUE || it.state == DoseState.UPCOMING }, isToday, isFuture) { selectedDose = it } }
                if (isToday) item { MealBanner(repository.mealTimes) }
                doses.groupBy { it.time }.toSortedMap().forEach { (time, group) ->
                    item(key = "header-$time") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimelineHeader(partFor(time), time, isToday && kotlin.math.abs(time - now) < 60 * 60_000L, now, Modifier.weight(1f))
                            val pending = group.filter { it.state !in setOf(DoseState.TAKEN, DoseState.SKIPPED) }
                            if (!isFuture && pending.isNotEmpty()) TextButton({ repository.logDoses(pending, DoseStatus.taken) }) { Text("Take All") }
                        }
                    }
                    item(key = "card-$time") {
                            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                                group.forEachIndexed { index, dose ->
                                    if (index > 0) RowDivider()
                                    DoseRow(dose, isFuture, repository.prescription(dose.medication.prescriptionID)?.name, onClick = { selectedDose = dose })
                                }
                            }
                    }
                }
                val prn = repository.activeMedications.filter { it.schedule.kind == ScheduleKind.asNeeded }
                if (prn.isNotEmpty() && isToday) {
                    item { SectionLabel("When you need it", Modifier.padding(top = 4.dp)) }
                    item {
                        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                            prn.forEachIndexed { index, med ->
                                if (index > 0) RowDivider()
                                Row(
                                    Modifier.fillMaxWidth().clickable { prnMedication = med }.padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MedicationIcon(med, 40.dp); Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(med.name, color = DS.colors.ink, fontWeight = FontWeight.Bold)
                                        val count = repository.logs.count { it.medicationID == med.id && it.isAsNeeded && DoseEngine.startOfDay(it.actedAt) == selectedDay }
                                        Text(if (count == 0) "No doses logged today" else "$count taken today", color = DS.colors.ink3, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.AddCircle, "Log dose", tint = DS.colors.mint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDose?.let { dose -> DoseActionSheet(repository, dose, onDismiss = { selectedDose = null }) }
    prnMedication?.let { med -> AsNeededSheet(repository, med, onDismiss = { prnMedication = null }) }
}

@Composable
private fun Header(now: Long, onAdd: () -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val greeting = when (cal.get(Calendar.HOUR_OF_DAY)) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SectionLabel(SimpleDateFormat("EEEE · MMM d", Locale.getDefault()).format(Date(now)))
            Text(greeting, style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink)
        }
        IconButton(onAdd) { Icon(Icons.Default.AddCircle, "Add New", tint = DS.colors.mint, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun DayStrip(repository: AppRepository, selectedDay: Long, onSelect: (Long) -> Unit) {
    val today = DoseEngine.startOfDay(System.currentTimeMillis())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (-3..3).forEach { offset ->
            val day = DoseEngine.addDays(today, offset)
            val selected = day == selectedDay
            val dayStats = DoseEngine.stats(repository.doses(day))
            Column(
                Modifier.width(43.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (selected) DS.colors.mint.copy(.14f) else Color.Transparent)
                    .clickable { onSelect(day) }.padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(SimpleDateFormat("EE", Locale.getDefault()).format(Date(day)).take(1), color = if (selected) DS.colors.mint else DS.colors.ink3,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(day)), color = if (selected) DS.colors.ink else DS.colors.ink2,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                val dot = when {
                    dayStats.scheduled == 0 -> DS.colors.line2
                    dayStats.taken == dayStats.scheduled -> DS.colors.mint
                    dayStats.missed > 0 -> DS.colors.coral
                    dayStats.skipped > 0 -> DS.colors.amber
                    else -> DS.colors.ink3
                }
                Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
            }
        }
    }
}

@Composable
private fun HeroCard(stats: DoseEngine.Stats, next: ScheduledDose?, isToday: Boolean, isFuture: Boolean, onTake: (ScheduledDose) -> Unit) {
    GradientCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(stats.completionRatio.toFloat(), Modifier.size(104.dp), 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(stats.completionRatio * 100).toInt()}%", color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp)
                    Text("TODAY", color = DS.colors.ink3, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(if (next == null) "Daily plan" else "${stats.pending} left · Next up")
                Spacer(Modifier.height(5.dp))
                Text(next?.medication?.name ?: when {
                    stats.scheduled == 0 -> "A quiet day"
                    stats.missed > 0 -> "${stats.missed} ${if (stats.missed == 1) "dose" else "doses"} missed"
                    stats.taken == stats.scheduled -> "All done"
                    else -> "Day complete"
                }, color = DS.colors.ink,
                    style = MaterialTheme.typography.titleLarge)
                Text(next?.let { "${it.timeLabel} · ${it.medication.doseLabel}" }
                    ?: if (stats.missed > 0) "Tap a missed dose below to log it" else "${stats.taken} of ${stats.scheduled} doses ticked",
                    color = DS.colors.ink2, fontSize = 12.sp)
                if (next != null && isToday && !isFuture) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { onTake(next) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = DS.colors.mint.copy(.15f), contentColor = DS.colors.mint)) {
                        Icon(Icons.Default.Check, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Log dose", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MealBanner(mealTimes: MealTimes) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DS.colors.glass)
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Restaurant, null, tint = DS.colors.amber)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("Meals stay in sync", color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Breakfast ${mealTimes.breakfast.label()} · Dinner ${mealTimes.dinner.label()}", color = DS.colors.ink3, fontSize = 11.sp)
        }
        Icon(Icons.Default.Sync, null, tint = DS.colors.ink3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TimelineHeader(part: DayPart, time: Long, showNow: Boolean, now: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (showNow) {
            Row(Modifier.padding(bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(DS.colors.mint))
                Spacer(Modifier.width(9.dp))
                Text("NOW · ${TimeOfDay.fromEpoch(now).label()}", color = DS.colors.mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.width(9.dp)); HorizontalDivider(color = DS.colors.mint.copy(.35f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlotIcon(time, 30.dp); Spacer(Modifier.width(10.dp))
            Text(part.title, color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp)); Text(TimeOfDay.fromEpoch(time).label(), color = DS.colors.ink3, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DoseRow(dose: ScheduledDose, isFuture: Boolean, prescriptionName: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedicationIcon(dose.medication, 42.dp); Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(dose.medication.name, color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(listOfNotNull(dose.medication.strengthLabel, dose.medication.doseLabel, prescriptionName).joinToString(" · "), color = DS.colors.ink3, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(TimeOfDay.fromEpoch(dose.time).label(), color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp)); DoseStateMark(dose.state, isFuture)
        }
    }
}

@Composable
private fun DoseStateMark(state: DoseState, isFuture: Boolean) {
    val (icon, color) = when (state) {
        DoseState.TAKEN -> Icons.Default.CheckCircle to DS.colors.mint
        DoseState.SKIPPED -> Icons.Default.RemoveCircle to DS.colors.amber
        DoseState.MISSED -> Icons.Default.Error to DS.colors.coral
        DoseState.DUE -> Icons.Default.RadioButtonUnchecked to DS.colors.mint
        DoseState.UPCOMING -> Icons.Default.Schedule to DS.colors.ink3
    }
    Icon(icon, state.name.lowercase(), tint = color, modifier = Modifier.size(22.dp))
}

@Composable
private fun DoseActionSheet(repository: AppRepository, dose: ScheduledDose, onDismiss: () -> Unit) {
    val resolved = repository.doses(dose.time).firstOrNull { it.id == dose.id } ?: dose
    val context = LocalContext.current
    var actedAt by remember(resolved.id) { mutableLongStateOf(resolved.log?.actedAt ?: initialDoseActedAt(resolved.time)) }
    var note by remember(resolved.id) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DS.colors.bg3) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MedicationIcon(resolved.medication, 62.dp); Spacer(Modifier.height(12.dp))
            Text(resolved.medication.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink)
            Text("${resolved.timeLabel} · ${resolved.medication.doseLabel}", color = DS.colors.ink2)
            Spacer(Modifier.height(22.dp))
            if (resolved.state == DoseState.TAKEN || resolved.state == DoseState.SKIPPED) {
                PrimaryButton("Undo ${resolved.state.name.lowercase()}", { repository.removeLog(resolved); onDismiss() }, Modifier.fillMaxWidth(), leading = Icons.Default.Undo)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({
                        val calendar = Calendar.getInstance().apply { timeInMillis = actedAt }
                        android.app.DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            actedAt = Calendar.getInstance().apply {
                                timeInMillis = actedAt; set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            }.timeInMillis.coerceAtMost(System.currentTimeMillis())
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, Modifier.weight(1f)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(5.dp)); Text(SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(actedAt))) }
                    OutlinedButton({
                        val calendar = Calendar.getInstance().apply { timeInMillis = actedAt }
                        android.app.TimePickerDialog(context, { _, h, m ->
                            actedAt = Calendar.getInstance().apply {
                                timeInMillis = actedAt; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }.timeInMillis.coerceAtMost(System.currentTimeMillis())
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
                    }, Modifier.weight(1f)) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(5.dp)); Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(actedAt))) }
                }
                Spacer(Modifier.height(8.dp)); OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Mark as taken", { repository.logDose(resolved, DoseStatus.taken, actedAt, note); onDismiss() }, Modifier.fillMaxWidth(), leading = Icons.Default.Check)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton({ NotificationScheduler.scheduleSnooze(repository.appContext, resolved.medication.id, resolved.time, 15); onDismiss() }, Modifier.weight(1f)) {
                        Icon(Icons.Default.Snooze, null); Spacer(Modifier.width(5.dp)); Text("Snooze")
                    }
                    OutlinedButton({ repository.logDose(resolved, DoseStatus.skipped, actedAt, note); onDismiss() }, Modifier.weight(1f)) {
                        Icon(Icons.Default.RemoveCircleOutline, null); Spacer(Modifier.width(5.dp)); Text("Skip")
                    }
                }
            }
        }
    }
}

internal fun initialDoseActedAt(scheduledAt: Long, now: Long = System.currentTimeMillis()): Long =
    minOf(scheduledAt, now)

@Composable
fun AsNeededSheet(repository: AppRepository, medication: Medication, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(medication.schedule.amountPerDose.toString()) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DS.colors.bg3) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            SectionLabel("As needed dose")
            Text(medication.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, label = { Text("Amount (${medication.form.unitName(2.0)})") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Log dose", { repository.logAsNeeded(medication, amount.toDoubleOrNull() ?: 1.0, note); onDismiss() }, Modifier.fillMaxWidth(), leading = Icons.Default.Check)
        }
    }
}

private fun partFor(time: Long): DayPart = when (TimeOfDay.fromEpoch(time).hour) {
    in 5..10 -> DayPart.MORNING
    in 11..15 -> DayPart.MIDDAY
    in 16..20 -> DayPart.EVENING
    else -> DayPart.BEDTIME
}
