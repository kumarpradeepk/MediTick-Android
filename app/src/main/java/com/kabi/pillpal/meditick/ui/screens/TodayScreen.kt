@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.formatFullWeekdayDate
import com.kabi.pillpal.meditick.formatPercent
import com.kabi.pillpal.meditick.formatShortDate
import com.kabi.pillpal.meditick.formatTime
import com.kabi.pillpal.meditick.weekdayInitial
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

private enum class DayPart(@StringRes val title: Int) {
    MORNING(R.string.daypart_morning), MIDDAY(R.string.daypart_midday),
    EVENING(R.string.daypart_evening), BEDTIME(R.string.daypart_bedtime),
}

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
                FriendlyEmptyState(Icons.Default.Medication, stringResource(R.string.today_empty_title),
                    stringResource(R.string.today_empty_body),
                    stringResource(R.string.today_add_first), onAdd)
                Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.today_takes_30_seconds), color = DS.colors.ink3, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 126.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Spacer(Modifier.statusBarsPadding().height(1.dp)); Header(now, onAdd, Modifier.appearFluidly(0)) }
                item { DayStrip(repository, selectedDay, Modifier.appearFluidly(1)) { selectedDay = it } }
                item { HeroCard(stats, doses.firstOrNull { it.state == DoseState.DUE || it.state == DoseState.UPCOMING }, isToday, isFuture, Modifier.appearFluidly(2)) { selectedDose = it } }
                if (isToday) item { MealBanner(repository.mealTimes, Modifier.appearFluidly(3)) }
                doses.groupBy { it.time }.toSortedMap().forEach { (time, group) ->
                    item(key = "header-$time") {
                        val haptics = rememberHaptics()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimelineHeader(partFor(time), time, isToday && kotlin.math.abs(time - now) < 60 * 60_000L, now, Modifier.weight(1f))
                            val pending = group.filter { it.state !in setOf(DoseState.TAKEN, DoseState.SKIPPED) }
                            if (!isFuture && pending.isNotEmpty()) TextButton({ haptics.success(); repository.logDoses(pending, DoseStatus.taken) }) { Text(stringResource(R.string.today_take_all), fontWeight = FontWeight.Bold) }
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
                    item { SectionLabel(stringResource(R.string.today_when_you_need_it), Modifier.padding(top = 4.dp)) }
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
                                        Text(
                                            if (count == 0) stringResource(R.string.today_no_doses_logged)
                                            else pluralStringResource(R.plurals.today_taken_today, count, count),
                                            color = DS.colors.ink3, fontSize = 12.sp,
                                        )
                                    }
                                    Icon(Icons.Default.AddCircle, stringResource(R.string.action_log_dose), tint = DS.colors.mint)
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
private fun Header(now: Long, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val greeting = stringResource(
        when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.today_greeting_morning
            in 12..16 -> R.string.today_greeting_afternoon
            else -> R.string.today_greeting_evening
        },
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SectionLabel(formatFullWeekdayDate(now))
            Text(greeting, style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink)
        }
        RoundIconButton(Icons.Default.Add, stringResource(R.string.today_add_new), onAdd, tint = DS.colors.mint)
    }
}

@Composable
private fun DayStrip(repository: AppRepository, selectedDay: Long, modifier: Modifier = Modifier, onSelect: (Long) -> Unit) {
    val today = DoseEngine.startOfDay(System.currentTimeMillis())
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (-3..3).forEach { offset ->
            val day = DoseEngine.addDays(today, offset)
            val selected = day == selectedDay
            val dayStats = DoseEngine.stats(repository.doses(day))
            val initialTint by animateColorAsState(if (selected) DS.colors.mint else DS.colors.ink3, tween(200), label = "dayInitial")
            val interaction = remember { MutableInteractionSource() }
            // The MediTick signature: each day is a miniature daily ring.
            val isFutureDay = day > today
            val tint = when {
                isFutureDay || dayStats.scheduled == 0 -> null
                dayStats.taken == dayStats.scheduled -> DS.colors.mint
                dayStats.missed > 0 -> DS.colors.coral
                else -> DS.colors.amber
            }
            val ratio = if (dayStats.scheduled == 0) 0f else dayStats.taken.toFloat() / dayStats.scheduled
            Column(
                Modifier.width(43.dp)
                    .pressScale(interaction, 0.9f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(interaction, ripple()) { haptics.tick(); onSelect(day) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekdayInitial(day), color = initialTint,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                MiniDayRing(
                    number = SimpleDateFormat("d", Locale.getDefault()).format(Date(day)),
                    progress = if (tint == DS.colors.mint) 1f else ratio,
                    tint = tint, selected = selected,
                )
            }
        }
    }
}

@Composable
private fun HeroCard(stats: DoseEngine.Stats, next: ScheduledDose?, isToday: Boolean, isFuture: Boolean, modifier: Modifier = Modifier, onTake: (ScheduledDose) -> Unit) {
    val context = LocalContext.current
    GradientCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(stats.completionRatio.toFloat(), Modifier.size(104.dp), 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatPercent((stats.completionRatio * 100).toInt()), color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp)
                    Text(stringResource(R.string.today_ring_label), color = DS.colors.ink3, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(
                    if (next == null) stringResource(R.string.today_daily_plan)
                    else stringResource(R.string.today_left_next_up, stats.pending),
                )
                Spacer(Modifier.height(5.dp))
                Text(next?.medication?.name ?: when {
                    stats.scheduled == 0 -> stringResource(R.string.today_quiet_day)
                    stats.missed > 0 -> pluralStringResource(R.plurals.today_doses_missed, stats.missed, stats.missed)
                    stats.taken == stats.scheduled -> stringResource(R.string.today_all_done)
                    else -> stringResource(R.string.today_day_complete)
                }, color = DS.colors.ink,
                    style = MaterialTheme.typography.titleLarge)
                Text(next?.let { stringResource(R.string.today_dose_line, it.timeLabel(context), it.medication.doseLabel(context)) }
                    ?: if (stats.missed > 0) stringResource(R.string.today_tap_missed)
                    else stringResource(R.string.today_doses_ticked, stats.taken, stats.scheduled),
                    color = DS.colors.ink2, fontSize = 12.sp)
                if (next != null && isToday && !isFuture) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { onTake(next) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = DS.colors.mint.copy(.15f), contentColor = DS.colors.mint)) {
                        Icon(Icons.Default.Check, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.action_log_dose), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MealBanner(mealTimes: MealTimes, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DS.colors.glass)
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Restaurant, null, tint = DS.colors.amber)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.today_meals_in_sync), color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                stringResource(R.string.today_meal_summary, mealTimes.breakfast.label(context), mealTimes.dinner.label(context)),
                color = DS.colors.ink3, fontSize = 11.sp,
            )
        }
        Icon(Icons.Default.Sync, null, tint = DS.colors.ink3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TimelineHeader(part: DayPart, time: Long, showNow: Boolean, now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        if (showNow) {
            Row(Modifier.padding(bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(DS.colors.mint))
                Spacer(Modifier.width(9.dp))
                Text(stringResource(R.string.today_now, TimeOfDay.fromEpoch(now).label(context)), color = DS.colors.mint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.width(9.dp)); HorizontalDivider(color = DS.colors.mint.copy(.35f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlotIcon(time, 30.dp); Spacer(Modifier.width(10.dp))
            Text(stringResource(part.title), color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp)); Text(TimeOfDay.fromEpoch(time).label(context), color = DS.colors.ink3, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DoseRow(dose: ScheduledDose, isFuture: Boolean, prescriptionName: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedicationIcon(dose.medication, 42.dp); Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(dose.medication.name, color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(listOfNotNull(dose.medication.strengthLabel, dose.medication.doseLabel(context), prescriptionName).joinToString(" · "), color = DS.colors.ink3, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(TimeOfDay.fromEpoch(dose.time).label(context), color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    Icon(icon, stringResource(doseStateRes(state)), tint = color, modifier = Modifier.size(22.dp))
}

@Composable
private fun DoseActionSheet(repository: AppRepository, dose: ScheduledDose, onDismiss: () -> Unit) {
    val resolved = repository.doses(dose.time).firstOrNull { it.id == dose.id } ?: dose
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var actedAt by remember(resolved.id) { mutableLongStateOf(resolved.log?.actedAt ?: initialDoseActedAt(resolved.time)) }
    var note by remember(resolved.id) { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = DS.colors.bg3,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.appearFluidly(0)) { MedicationIcon(resolved.medication, 62.dp) }
            Spacer(Modifier.height(12.dp))
            Text(resolved.medication.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink, modifier = Modifier.appearFluidly(1))
            Text(stringResource(R.string.today_dose_line, resolved.timeLabel(context), resolved.medication.doseLabel(context)), color = DS.colors.ink2, modifier = Modifier.appearFluidly(1))
            Spacer(Modifier.height(22.dp))
            if (resolved.state == DoseState.TAKEN || resolved.state == DoseState.SKIPPED) {
                PrimaryButton(
                    stringResource(if (resolved.state == DoseState.TAKEN) R.string.dose_undo_taken else R.string.dose_undo_skipped),
                    { repository.removeLog(resolved); onDismiss() }, Modifier.fillMaxWidth().appearFluidly(2), leading = Icons.Default.Undo,
                )
            } else {
                Row(Modifier.fillMaxWidth().appearFluidly(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(formatShortDate(actedAt), onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = actedAt }
                        android.app.DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            actedAt = Calendar.getInstance().apply {
                                timeInMillis = actedAt; set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, dayOfMonth)
                            }.timeInMillis.coerceAtMost(System.currentTimeMillis())
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, Modifier.weight(1f), leading = Icons.Default.CalendarMonth)
                    GhostButton(formatTime(context, actedAt), onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = actedAt }
                        android.app.TimePickerDialog(context, { _, h, m ->
                            actedAt = Calendar.getInstance().apply {
                                timeInMillis = actedAt; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }.timeInMillis.coerceAtMost(System.currentTimeMillis())
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
                    }, Modifier.weight(1f), leading = Icons.Default.Schedule)
                }
                Spacer(Modifier.height(8.dp)); MediTickTextField(note, { note = it }, placeholder = stringResource(R.string.dose_note_optional), modifier = Modifier.fillMaxWidth().appearFluidly(3))
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.dose_mark_as_taken), { haptics.success(); repository.logDose(resolved, DoseStatus.taken, actedAt, note); onDismiss() }, Modifier.fillMaxWidth().appearFluidly(4), leading = Icons.Default.Check)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.appearFluidly(5), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(stringResource(R.string.action_snooze), onClick = { NotificationScheduler.scheduleSnooze(repository.appContext, resolved.medication.id, resolved.time, 15); onDismiss() }, Modifier.weight(1f), leading = Icons.Default.Snooze)
                    GhostButton(stringResource(R.string.action_skip), onClick = { repository.logDose(resolved, DoseStatus.skipped, actedAt, note); onDismiss() }, Modifier.weight(1f), leading = Icons.Default.RemoveCircleOutline, tint = DS.colors.amber, borderTint = DS.colors.amber.copy(.3f), fillTint = DS.colors.amber.copy(.1f))
                }
            }
        }
    }
}

internal fun initialDoseActedAt(scheduledAt: Long, now: Long = System.currentTimeMillis()): Long =
    minOf(scheduledAt, now)

@Composable
fun AsNeededSheet(repository: AppRepository, medication: Medication, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var amount by remember { mutableStateOf(medication.schedule.amountPerDose.toString()) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = DS.colors.bg3,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            SectionLabel(stringResource(R.string.dose_as_needed_title), Modifier.appearFluidly(0))
            Text(medication.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink, modifier = Modifier.appearFluidly(1))
            Spacer(Modifier.height(18.dp))
            MediTickTextField(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, placeholder = stringResource(R.string.dose_amount_label, medication.form.unitName(context, 2.0)), modifier = Modifier.fillMaxWidth().appearFluidly(2))
            Spacer(Modifier.height(10.dp))
            MediTickTextField(note, { note = it }, placeholder = stringResource(R.string.dose_note_optional), modifier = Modifier.fillMaxWidth().appearFluidly(3))
            Spacer(Modifier.height(18.dp))
            PrimaryButton(stringResource(R.string.action_log_dose), { haptics.success(); repository.logAsNeeded(medication, amount.toDoubleOrNull() ?: 1.0, note); onDismiss() }, Modifier.fillMaxWidth().appearFluidly(4), leading = Icons.Default.Check)
        }
    }
}

/** The dose-state word, shared by Today and Progress. */
@StringRes internal fun doseStateRes(state: DoseState): Int = when (state) {
    DoseState.TAKEN -> R.string.state_taken
    DoseState.SKIPPED -> R.string.state_skipped
    DoseState.MISSED -> R.string.state_missed
    DoseState.DUE -> R.string.state_due
    DoseState.UPCOMING -> R.string.state_upcoming
}

private fun partFor(time: Long): DayPart = when (TimeOfDay.fromEpoch(time).hour) {
    in 5..10 -> DayPart.MORNING
    in 11..15 -> DayPart.MIDDAY
    in 16..20 -> DayPart.EVENING
    else -> DayPart.BEDTIME
}
