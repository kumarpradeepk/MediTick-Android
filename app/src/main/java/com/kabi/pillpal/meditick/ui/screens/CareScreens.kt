@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kabi.pillpal.meditick.formatMediumDate
import com.kabi.pillpal.meditick.formatPercent
import com.kabi.pillpal.meditick.formatShortDate
import com.kabi.pillpal.meditick.formatTime
import com.kabi.pillpal.meditick.formatWeekdayDate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS
import java.text.SimpleDateFormat
import java.util.*

private enum class TreatmentType(@StringRes val title: Int) {
    ALL(R.string.care_filter_all),
    MEDICATIONS(R.string.care_filter_medications),
    PRESCRIPTIONS(R.string.care_filter_prescriptions),
}

/** The word for a treatment status, shared by the chips, pills and dialogs. */
@StringRes internal fun TreatmentStatus.titleRes(): Int = when (this) {
    TreatmentStatus.active -> R.string.status_active
    TreatmentStatus.completed -> R.string.status_completed
    TreatmentStatus.archived -> R.string.status_archived
}

@Composable
fun CareScreen(
    repository: AppRepository, onAddMedication: (String?) -> Unit,
    onMedication: (String) -> Unit, onPrescription: (String) -> Unit,
) {
    val snapshot = repository.data
    var showAddMode by remember { mutableStateOf(false) }
    var showPrescription by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf(TreatmentType.ALL) }
    var statusFilter by remember { mutableStateOf(TreatmentStatus.active) }
    val visiblePrescriptions = snapshot.prescriptions.filter { it.effectiveStatus() == statusFilter }
    val visibleMedications = snapshot.medications.filter { medication ->
        val parent = repository.prescription(medication.prescriptionID)
        (typeFilter == TreatmentType.MEDICATIONS || parent == null) && medication.effectiveStatus(parent) == statusFilter
    }
    ScreenBackground {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 126.dp),
        ) {
            item {
                Spacer(Modifier.statusBarsPadding().height(1.dp))
                Row(Modifier.appearFluidly(0), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.care_title), style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink, modifier = Modifier.weight(1f))
                    RoundIconButton(Icons.Default.Add, stringResource(R.string.care_add), { showAddMode = true }, tint = DS.colors.mint)
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.appearFluidly(1), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TreatmentType.entries.forEach { value -> SelectChip(stringResource(value.title), typeFilter == value, { typeFilter = value }) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.appearFluidly(2), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TreatmentStatus.entries.forEach { value -> SelectChip(stringResource(value.titleRes()), statusFilter == value, { statusFilter = value }) }
                }
            }
            if (snapshot.medications.isEmpty() && snapshot.prescriptions.isEmpty()) {
                item {
                    Spacer(Modifier.height(74.dp))
                    FriendlyEmptyState(Icons.Default.Medication, stringResource(R.string.care_empty_title),
                        stringResource(R.string.care_empty_body),
                        stringResource(R.string.today_add_first), { showAddMode = true })
                }
            } else {
                if (typeFilter != TreatmentType.MEDICATIONS && visiblePrescriptions.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.care_section_prescriptions), Modifier.padding(top = 25.dp, bottom = 10.dp)) }
                    items(visiblePrescriptions, key = { it.id }) { rx ->
                        PrescriptionCard(repository, rx) { onPrescription(rx.id) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (typeFilter != TreatmentType.PRESCRIPTIONS && visibleMedications.isNotEmpty()) {
                    item {
                        SectionLabel(
                            stringResource(
                                if (typeFilter == TreatmentType.MEDICATIONS || snapshot.prescriptions.isEmpty()) R.string.care_section_medications
                                else R.string.care_section_standalone,
                            ),
                            Modifier.padding(top = 18.dp, bottom = 10.dp),
                        )
                    }
                    item {
                        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                            visibleMedications.forEachIndexed { index, med -> if (index > 0) RowDivider(); MedicationRow(med) { onMedication(med.id) } }
                        }
                    }
                }
                val archived = emptyList<Medication>()
                if (archived.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().clickable { showArchived = !showArchived }.padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(stringResource(R.string.care_archived_count, archived.size)); Spacer(Modifier.width(5.dp))
                            Icon(if (showArchived) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, tint = DS.colors.ink3, modifier = Modifier.size(17.dp))
                        }
                    }
                    if (showArchived) item {
                        GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                            archived.forEachIndexed { index, med -> if (index > 0) RowDivider(); MedicationRow(med) { onMedication(med.id) } }
                        }
                    }
                }
            }
        }
    }
    if (showAddMode) ModalBottomSheet(
        onDismissRequest = { showAddMode = false }, containerColor = DS.colors.bg3,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(stringResource(R.string.care_what_adding), style = MaterialTheme.typography.titleLarge, color = DS.colors.ink, modifier = Modifier.appearFluidly(0))
            Spacer(Modifier.height(15.dp))
            ModeCard(Icons.Default.Description, DS.colors.violet, stringResource(R.string.care_mode_prescription), stringResource(R.string.care_mode_prescription_sub), Modifier.appearFluidly(1)) {
                showAddMode = false; showPrescription = true
            }
            Spacer(Modifier.height(10.dp))
            ModeCard(Icons.Default.Medication, DS.colors.mint, stringResource(R.string.care_mode_single), stringResource(R.string.care_mode_single_sub), Modifier.appearFluidly(2)) {
                showAddMode = false; onAddMedication(null)
            }
        }
    }
    if (showPrescription) PrescriptionEditor(null, onDismiss = { showPrescription = false }) { value, _ ->
        repository.addPrescription(value); showPrescription = false
    }
}

@Composable
private fun ModeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), radius = 21.dp, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, 46.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(subtitle, color = DS.colors.ink3, fontSize = 12.sp) }
            Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3)
        }
    }
}

@Composable
private fun PrescriptionCard(repository: AppRepository, rx: Prescription, onClick: () -> Unit) {
    val effectiveStatus = rx.effectiveStatus()
    GradientCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(20.dp)) {
            Row { StatusPill(stringResource(effectiveStatus.titleRes()), when (effectiveStatus) { TreatmentStatus.active -> DS.colors.mint; TreatmentStatus.completed -> DS.colors.cyan; TreatmentStatus.archived -> DS.colors.ink3 }); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink2) }
            Spacer(Modifier.height(20.dp))
            Text(rx.name, style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            val count = repository.medicationsIn(rx.id).size
            Text(listOf(rx.condition, rx.prescriber, pluralStringResource(R.plurals.care_medication_count, count, count)).filter { it.isNotBlank() }.joinToString(" · "),
                color = DS.colors.ink3, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MedicationRow(med: Medication, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        MedicationIcon(med, 40.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(med.name, color = DS.colors.ink, fontWeight = FontWeight.Bold)
                if (med.addedByScan) { Spacer(Modifier.width(8.dp)); ScannedBadge() }
            }
            Text(listOfNotNull(med.strengthLabel, med.schedule.summary(context)).joinToString(" · "), color = DS.colors.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (med.inventoryEnabled) {
            Text(stringResource(R.string.care_stock_left, prettyNumber(med.stock)), color = if (med.needsRefill) DS.colors.amber else DS.colors.ink3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
        }
        if (med.needsRefill) Icon(Icons.Default.Inventory2, null, tint = DS.colors.amber, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp)); Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun MedicationDetailScreen(repository: AppRepository, medicationId: String, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val med = repository.medication(medicationId)
    if (med == null) { LaunchedEffect(Unit) { onBack() }; return }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var refillAmount by remember { mutableStateOf("") }
    var showRefill by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showAsNeeded by remember { mutableStateOf(false) }
    var logDose by remember { mutableStateOf<ScheduledDose?>(null) }
    val nextDose = remember(repository.data, med.id) { repository.nextDoseFor(med.id) }
    val medicationStatus = med.effectiveStatus(repository.prescription(med.prescriptionID))
    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
            item {
                Spacer(Modifier.statusBarsPadding().height(1.dp))
                DetailTopBar(med.name, onBack, onEdit)
                Spacer(Modifier.height(15.dp))
                GradientCard(Modifier.fillMaxWidth().appearFluidly(0)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        MedicationIcon(med, 66.dp); Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(med.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink)
                            Text(listOfNotNull(med.strengthLabel, med.form.title(context)).joinToString(" · "), color = DS.colors.ink2)
                            Spacer(Modifier.height(7.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(stringResource(medicationStatus.titleRes()), when (medicationStatus) { TreatmentStatus.active -> DS.colors.mint; TreatmentStatus.completed -> DS.colors.cyan; TreatmentStatus.archived -> DS.colors.ink3 })
                                if (med.addedByScan) { Spacer(Modifier.width(8.dp)); ScannedBadge() }
                            }
                        }
                    }
                }
            }
            nextDose?.let { next -> item { DetailSection(stringResource(R.string.detail_next_dose)) {
                SettingsRow(Icons.Default.Schedule, DS.colors.mint, TimeOfDay.fromEpoch(next.time).label(context), formatWeekdayDate(next.time))
                RowDivider(); SettingsRow(Icons.Default.CheckCircle, DS.colors.mint, stringResource(R.string.action_take_now), med.doseLabel(context), onClick = { logDose = next })
            } } }
            if (nextDose == null && med.schedule.kind == ScheduleKind.asNeeded && medicationStatus == TreatmentStatus.active) item {
                DetailSection(stringResource(R.string.detail_as_needed)) {
                    SettingsRow(Icons.Default.CheckCircle, DS.colors.mint, stringResource(R.string.action_take_now), med.doseLabel(context), onClick = { showAsNeeded = true })
                }
            }
            item { DetailSection(stringResource(R.string.detail_schedule)) {
                SettingsRow(Icons.Default.Schedule, DS.colors.mint, med.schedule.summary(context), med.schedule.frequencySummary(context))
                RowDivider(); SettingsRow(Icons.Default.Medication, DS.colors.cyan, med.doseLabel(context), stringResource(R.string.detail_amount_per_dose))
                if (med.instructions.isNotBlank()) { RowDivider(); SettingsRow(Icons.Default.Notes, DS.colors.violet, med.instructions) }
            } }
            item { DetailSection(stringResource(R.string.detail_supply)) {
                if (med.inventoryEnabled) SettingsRow(Icons.Default.Inventory2, if (med.needsRefill) DS.colors.amber else DS.colors.mint,
                    stringResource(R.string.detail_supply_remaining, prettyNumber(med.stock)),
                    med.daysOfStockRemaining?.let { stringResource(R.string.detail_supply_alert_days, prettyNumber(med.refillReminderThreshold), it) }
                        ?: stringResource(R.string.detail_supply_alert, prettyNumber(med.refillReminderThreshold)),
                    onClick = { showRefill = true }) { Text(stringResource(R.string.detail_refill), color = DS.colors.mint, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                else SettingsRow(Icons.Default.Inventory2, DS.colors.ink3, stringResource(R.string.detail_supply_untracked), stringResource(R.string.detail_supply_enable))
            } }
            val history = repository.logs.filter { it.medicationID == med.id }.sortedByDescending { it.actedAt }.take(10)
            if (history.isNotEmpty()) item { DetailSection(stringResource(R.string.detail_recent_history)) {
                history.forEachIndexed { index, log ->
                    if (index > 0) RowDivider()
                    SettingsRow(if (log.status == DoseStatus.taken) Icons.Default.CheckCircle else Icons.Default.RemoveCircle,
                        if (log.status == DoseStatus.taken) DS.colors.mint else DS.colors.amber,
                        if (log.isAsNeeded) stringResource(R.string.detail_history_as_needed) else stringResource(doseStatusRes(log.status)),
                        formatWeekdayDate(log.actedAt) + " · " + formatTime(context, log.actedAt))
                }
                RowDivider(); SettingsRow(Icons.Default.List, DS.colors.cyan, stringResource(R.string.detail_view_medication_logs), stringResource(R.string.detail_view_medication_logs_sub), onClick = { showLogs = true })
            } }
            item {
                Spacer(Modifier.height(18.dp))
                GhostButton(
                    stringResource(if (med.isArchived) R.string.detail_restore_medication else R.string.detail_archive_medication),
                    onClick = { if (med.isArchived) repository.archiveMedication(med.id, false) else confirmArchive = true },
                    Modifier.fillMaxWidth(), leading = if (med.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                )
                Spacer(Modifier.height(10.dp))
                DangerButton(stringResource(R.string.detail_delete_medication), { confirmDelete = true }, Modifier.fillMaxWidth(), leading = Icons.Default.Delete)
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }
    if (confirmDelete) ConfirmSheet(
        title = stringResource(R.string.confirm_delete_medication_title, med.name),
        body = stringResource(R.string.confirm_delete_medication_body),
        confirmText = stringResource(R.string.action_delete), destructive = true,
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = { repository.deleteMedication(med.id); onBack() }, onDismiss = { confirmDelete = false },
    )
    if (confirmArchive) ConfirmSheet(
        title = stringResource(R.string.confirm_archive_medication_title, med.name),
        body = stringResource(R.string.confirm_archive_medication_body),
        confirmText = stringResource(R.string.rx_action_archive), icon = Icons.Default.Archive,
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = { repository.archiveMedication(med.id, true); confirmArchive = false }, onDismiss = { confirmArchive = false },
    )
    if (showRefill) AppSheet({ showRefill = false }, title = stringResource(R.string.refill_title)) {
        MediTickTextField(refillAmount, { refillAmount = it.filter(Char::isDigit) }, placeholder = stringResource(R.string.refill_units_label), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        PrimaryButton(stringResource(R.string.action_add), { repository.refillStock(med.id, refillAmount.toDoubleOrNull() ?: 0.0); showRefill = false }, Modifier.fillMaxWidth(), leading = Icons.Default.Add)
    }
    logDose?.let { dose -> LogDoseDialog(repository, dose, { logDose = null }) }
    if (showAsNeeded) AsNeededSheet(repository, med) { showAsNeeded = false }
    if (showLogs) LogsDialog(stringResource(R.string.logs_title, med.name), repository.logs.filter { it.medicationID == med.id }.sortedByDescending { it.actedAt }, repository, { showLogs = false })
}

@Composable
fun PrescriptionDetailScreen(
    repository: AppRepository, prescriptionId: String, isPro: Boolean, onBack: () -> Unit,
    onMedication: (String) -> Unit, onAddMedication: () -> Unit,
) {
    val context = LocalContext.current
    val rx = repository.prescription(prescriptionId)
    if (rx == null) { LaunchedEffect(Unit) { onBack() }; return }
    var edit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var pendingStatus by remember { mutableStateOf<TreatmentStatus?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var chooseExisting by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val medications = repository.medicationsIn(rx.id)
    val effectiveStatus = rx.effectiveStatus()
    val progressSettings = remember { SettingsStore.get(repository.appContext) }
    val today = DoseEngine.startOfDay(System.currentTimeMillis())
    val progressStart = DoseEngine.startOfDay(rx.startDate)
    val progressEnd = minOf(today, rx.endDate?.let(DoseEngine::startOfDay) ?: today)
    val rxStats = remember(repository.data, rx) {
        var total = DoseEngine.Stats()
        var cursor = progressStart
        while (cursor <= progressEnd) {
            total += DoseEngine.stats(repository.doses(cursor).filter { it.medication.prescriptionID == rx.id })
            cursor = DoseEngine.addDays(cursor, 1)
        }
        total
    }
    val rxLogs = repository.logsForPrescription(rx.id).filter {
        !it.isAsNeeded && it.scheduledAt >= progressStart && it.scheduledAt < DoseEngine.addDays(progressEnd, 1)
    }
    val takenLogs = rxLogs.filter { it.status == DoseStatus.taken }
    val rxOnTime = takenLogs.takeIf { it.isNotEmpty() }?.let {
        DoseEngine.onTimeRate(it, progressSettings.onTimeWindowMinutes, progressStart)
    }
    ScreenBackground {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
            item { Spacer(Modifier.statusBarsPadding().height(1.dp)); DetailTopBar(rx.name, onBack) { edit = true }; Spacer(Modifier.height(15.dp)) }
            item {
                GradientCard(Modifier.fillMaxWidth().appearFluidly(0)) {
                    Column(Modifier.padding(21.dp)) {
                        StatusPill(stringResource(R.string.status_treatment, stringResource(effectiveStatus.titleRes())), when (effectiveStatus) { TreatmentStatus.active -> DS.colors.mint; TreatmentStatus.completed -> DS.colors.cyan; TreatmentStatus.archived -> DS.colors.ink3 }); Spacer(Modifier.height(18.dp))
                        Text(rx.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink)
                        if (rx.condition.isNotBlank()) Text(rx.condition, color = DS.colors.ink2)
                        if (rx.prescriber.isNotBlank()) Text(stringResource(R.string.rx_prescribed_by, rx.prescriber), color = DS.colors.ink3, fontSize = 12.sp)
                        if (rx.facility.isNotBlank()) Text(rx.facility, color = DS.colors.ink3, fontSize = 12.sp)
                        if (rx.contact.isNotBlank()) Text(rx.contact, color = DS.colors.ink3, fontSize = 12.sp)
                        Text(
                            rx.endDate?.let { stringResource(R.string.rx_period_range, formatMediumDate(rx.startDate), formatMediumDate(it)) }
                                ?: stringResource(R.string.rx_period_ongoing, formatMediumDate(rx.startDate)),
                            color = DS.colors.ink3, fontSize = 12.sp,
                        )
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.care_section_medications), Modifier.padding(top = 24.dp, bottom = 10.dp)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                    medications.forEachIndexed { index, med -> if (index > 0) RowDivider(); MedicationRow(med) { onMedication(med.id) } }
                    if (medications.isNotEmpty()) RowDivider()
                    SettingsRow(Icons.Default.Add, DS.colors.mint, stringResource(R.string.rx_add_medication), stringResource(if (!isPro && repository.activeMedications.isNotEmpty()) R.string.rx_add_medication_locked else R.string.rx_add_medication_sub), onClick = { if (!isPro && repository.activeMedications.isNotEmpty()) onAddMedication() else addMenu = true })
                }
            }
            item { DetailSection(stringResource(R.string.rx_progress_and_logs)) {
                SettingsRow(Icons.Default.BarChart, DS.colors.cyan,
                    if (rxStats.decided == 0) stringResource(R.string.rx_no_decided)
                    else stringResource(R.string.rx_adherence, formatPercent((rxStats.ratio * 100).toInt())),
                    stringResource(R.string.rx_counts, rxStats.taken, rxStats.skipped, rxStats.missed))
                RowDivider(); SettingsRow(Icons.Default.Timer, DS.colors.violet,
                    rxOnTime?.let { stringResource(R.string.rx_on_time, formatPercent((it * 100).toInt())) } ?: stringResource(R.string.rx_no_taken),
                    stringResource(R.string.rx_within_window, progressSettings.onTimeWindowMinutes))
                RowDivider(); SettingsRow(Icons.Default.List, DS.colors.mint, stringResource(R.string.rx_view_logs), stringResource(R.string.rx_view_logs_sub), onClick = { showLogs = true })
            } }
            if (rx.notes.isNotBlank()) item { DetailSection(stringResource(R.string.rx_notes)) { Text(rx.notes, Modifier.padding(16.dp), color = DS.colors.ink2) } }
            item {
                Spacer(Modifier.height(22.dp))
                when (effectiveStatus) {
                    TreatmentStatus.active -> {
                        GhostButton(stringResource(R.string.rx_mark_complete), { pendingStatus = TreatmentStatus.completed }, Modifier.fillMaxWidth(), leading = Icons.Default.CheckCircle)
                        Spacer(Modifier.height(10.dp))
                        GhostButton(stringResource(R.string.rx_archive), { pendingStatus = TreatmentStatus.archived }, Modifier.fillMaxWidth(), leading = Icons.Default.Archive)
                    }
                    TreatmentStatus.completed -> GhostButton(stringResource(R.string.rx_reactivate), { pendingStatus = TreatmentStatus.active }, Modifier.fillMaxWidth(), leading = Icons.Default.Refresh)
                    TreatmentStatus.archived -> GhostButton(stringResource(R.string.rx_restore), { pendingStatus = TreatmentStatus.active }, Modifier.fillMaxWidth(), leading = Icons.Default.Restore)
                }
                Spacer(Modifier.height(10.dp))
                DangerButton(stringResource(R.string.rx_delete), { delete = true }, Modifier.fillMaxWidth(), leading = Icons.Default.Delete)
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }
    if (edit) PrescriptionEditor(rx, { edit = false }, linkedMedicationCount = medications.size) { value, updateDates ->
        repository.updatePrescription(value, updateDates); edit = false
    }
    pendingStatus?.let { status ->
        val reactivating = status == TreatmentStatus.active && effectiveStatus == TreatmentStatus.completed
        ConfirmSheet(
            title = stringResource(when (status) {
                TreatmentStatus.completed -> R.string.rx_confirm_complete_title
                TreatmentStatus.archived -> R.string.rx_confirm_archive_title
                TreatmentStatus.active -> if (reactivating) R.string.rx_confirm_reactivate_title else R.string.rx_confirm_restore_title
            }),
            body = stringResource(when (status) {
                TreatmentStatus.completed -> R.string.rx_confirm_complete_body
                TreatmentStatus.archived -> R.string.rx_confirm_archive_body
                TreatmentStatus.active -> if (reactivating) R.string.rx_confirm_reactivate_body else R.string.rx_confirm_restore_body
            }),
            confirmText = stringResource(when {
                status == TreatmentStatus.active && reactivating -> R.string.rx_action_reactivate
                status == TreatmentStatus.active -> R.string.rx_action_restore
                status == TreatmentStatus.completed -> R.string.rx_action_complete
                else -> R.string.rx_action_archive
            }),
            icon = when (status) {
                TreatmentStatus.completed -> Icons.Default.CheckCircle
                TreatmentStatus.archived -> Icons.Default.Archive
                TreatmentStatus.active -> Icons.Default.Restore
            },
            cancelText = stringResource(R.string.action_cancel),
            onConfirm = { repository.setPrescriptionStatus(rx.id, status); pendingStatus = null },
            onDismiss = { pendingStatus = null },
        ) }
    if (delete) ConfirmSheet(
        title = stringResource(R.string.rx_confirm_delete_title),
        body = stringResource(R.string.rx_confirm_delete_body),
        confirmText = stringResource(R.string.action_delete), destructive = true,
        cancelText = stringResource(R.string.action_cancel),
        onConfirm = { repository.deletePrescription(rx.id); onBack() }, onDismiss = { delete = false },
    )
    if (addMenu) ModalBottomSheet(
        onDismissRequest = { addMenu = false }, containerColor = DS.colors.bg3,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(stringResource(R.string.rx_add_medication), style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                SettingsRow(Icons.Default.Add, DS.colors.mint, stringResource(R.string.rx_add_new_medication), stringResource(R.string.rx_add_new_medication_sub), onClick = { addMenu = false; onAddMedication() })
                RowDivider()
                SettingsRow(Icons.Default.Link, DS.colors.cyan, stringResource(R.string.rx_add_existing), stringResource(R.string.rx_add_existing_sub), onClick = { addMenu = false; chooseExisting = true })
            }
        }
    }
    if (chooseExisting) ModalBottomSheet(
        onDismissRequest = { chooseExisting = false }, containerColor = DS.colors.bg3,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        val standalone = repository.medications.filter { it.prescriptionID == null }
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(stringResource(R.string.rx_add_existing), style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            Spacer(Modifier.height(10.dp))
            if (standalone.isEmpty()) Text(stringResource(R.string.rx_no_standalone), color = DS.colors.ink3, modifier = Modifier.padding(vertical = 20.dp))
            else GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                standalone.forEachIndexed { index, med ->
                    if (index > 0) RowDivider()
                    SettingsRow(Icons.Default.Medication, DS.colors.mint, med.name, med.strengthLabel, onClick = { repository.linkMedication(med.id, rx.id); chooseExisting = false })
                }
            }
        }
    }
    if (showLogs) LogsDialog(stringResource(R.string.logs_title, rx.name), repository.logsForPrescription(rx.id), repository, { showLogs = false })
}

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit, onEdit: () -> Unit) {
    ScreenTopBar(
        title = title,
        leadingIcon = Icons.Default.ArrowBack, leadingDescription = stringResource(R.string.action_back), onLeading = onBack,
        trailingIcon = Icons.Default.Edit, trailingDescription = stringResource(R.string.action_edit), onTrailing = onEdit,
        trailingTint = DS.colors.mint,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DetailSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column { SectionLabel(label, Modifier.padding(top = 24.dp, bottom = 10.dp)); GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp), content = content) }
}

@Composable
fun PrescriptionEditor(
    existing: Prescription?,
    onDismiss: () -> Unit,
    linkedMedicationCount: Int = 0,
    onSave: (Prescription, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var condition by remember { mutableStateOf(existing?.condition.orEmpty()) }
    var prescriber by remember { mutableStateOf(existing?.prescriber.orEmpty()) }
    var facility by remember { mutableStateOf(existing?.facility.orEmpty()) }
    var contact by remember { mutableStateOf(existing?.contact.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var startDate by remember { mutableLongStateOf(existing?.startDate ?: startOfToday()) }
    var ongoing by remember { mutableStateOf(existing?.endDate == null) }
    var endDate by remember { mutableLongStateOf(existing?.endDate ?: DoseEngine.addDays(startDate, 14)) }
    var updateLinkedDates by remember(existing?.id) { mutableStateOf(true) }
    AppSheet(onDismiss, title = stringResource(if (existing == null) R.string.rx_editor_new else R.string.rx_editor_edit)) {
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { MediTickTextField(name, { name = it }, placeholder = stringResource(R.string.rx_field_plan_name), singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { MediTickTextField(condition, { condition = it }, placeholder = stringResource(R.string.rx_field_condition), singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { MediTickTextField(prescriber, { prescriber = it }, placeholder = stringResource(R.string.rx_field_prescriber), singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { MediTickTextField(facility, { facility = it }, placeholder = stringResource(R.string.rx_field_facility), singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { MediTickTextField(contact, { contact = it }, placeholder = stringResource(R.string.rx_field_contact), singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { GhostButton(stringResource(R.string.rx_start, formatMediumDate(startDate)), { showDatePicker(context, startDate) { startDate = it; if (endDate < it) endDate = it } }, Modifier.fillMaxWidth(), leading = Icons.Default.CalendarMonth) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.rx_ongoing), Modifier.weight(1f), color = DS.colors.ink, fontWeight = FontWeight.Bold); Switch(ongoing, { ongoing = it }) } }
            if (!ongoing) item { GhostButton(stringResource(R.string.rx_end, formatMediumDate(endDate)), { showDatePicker(context, endDate) { endDate = it.coerceAtLeast(startDate) } }, Modifier.fillMaxWidth(), leading = Icons.Default.EventBusy) }
            if (existing != null && linkedMedicationCount > 0) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.rx_update_linked, linkedMedicationCount, linkedMedicationCount), color = DS.colors.ink)
                        Text(stringResource(R.string.rx_apply_dates), color = DS.colors.ink3, fontSize = 11.sp)
                    }
                    Switch(updateLinkedDates, { updateLinkedDates = it })
                }
            }
            item { MediTickTextField(notes, { notes = it }, placeholder = stringResource(R.string.rx_field_notes), minLines = 2, modifier = Modifier.fillMaxWidth()) }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            stringResource(R.string.action_save),
            { if (name.isNotBlank()) onSave((existing ?: Prescription()).copy(name = name.trim(), condition = condition.trim(), prescriber = prescriber.trim(), facility = facility.trim(), contact = contact.trim(), startDate = startDate, endDate = if (ongoing) null else endDate, notes = notes.trim()), updateLinkedDates) },
            Modifier.fillMaxWidth(), enabled = name.isNotBlank(), leading = Icons.Default.Check,
        )
    }
}

private fun showDatePicker(context: android.content.Context, initial: Long, onDate: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    android.app.DatePickerDialog(context, { _, year, month, day -> onDate(Calendar.getInstance().apply { clear(); set(year, month, day) }.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

@Composable
fun LogDoseDialog(repository: AppRepository, dose: ScheduledDose, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var actedAt by remember(dose.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember(dose.id) { mutableStateOf("") }
    val haptics = rememberHaptics()
    AppSheet(onDismiss, title = stringResource(R.string.dose_log_title)) {
        Text(stringResource(R.string.today_dose_line, dose.medication.name, dose.medication.doseLabel(context)), color = DS.colors.ink2)
        Spacer(Modifier.height(14.dp))
        GhostButton(stringResource(R.string.dose_logged_time, formatTime(context, actedAt)), {
            val calendar = Calendar.getInstance().apply { timeInMillis = actedAt }
            android.app.TimePickerDialog(context, { _, hour, minute ->
                actedAt = Calendar.getInstance().apply { timeInMillis = actedAt; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.timeInMillis
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, Modifier.fillMaxWidth(), leading = Icons.Default.Schedule)
        Spacer(Modifier.height(10.dp))
        MediTickTextField(note, { note = it }, placeholder = stringResource(R.string.dose_note_optional), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        PrimaryButton(stringResource(R.string.action_take), { haptics.success(); repository.logDose(dose, DoseStatus.taken, actedAt, note); onDismiss() }, Modifier.fillMaxWidth(), leading = Icons.Default.Check)
        Spacer(Modifier.height(10.dp))
        GhostButton(stringResource(R.string.action_skip), { repository.logDose(dose, DoseStatus.skipped, actedAt, note); onDismiss() }, Modifier.fillMaxWidth(), leading = Icons.Default.RemoveCircleOutline, tint = DS.colors.amber, borderTint = DS.colors.amber.copy(.3f), fillTint = DS.colors.amber.copy(.1f))
    }
}

@Composable
fun LogsDialog(title: String, logs: List<DoseLog>, repository: AppRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppSheet(onDismiss, title = title) {
        if (logs.isEmpty()) Text(stringResource(R.string.logs_empty), color = DS.colors.ink3)
        else GlassCard(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(vertical = 3.dp)) {
            LazyColumn {
                items(logs, key = { it.id }) { log ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (log.status == DoseStatus.taken) Icons.Default.CheckCircle else Icons.Default.RemoveCircle, null, tint = if (log.status == DoseStatus.taken) DS.colors.mint else DS.colors.amber)
                        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) {
                            Text(repository.medication(log.medicationID)?.name ?: stringResource(R.string.progress_generic_medication), color = DS.colors.ink, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(
                                    R.string.log_row,
                                    stringResource(doseStatusRes(log.status)),
                                    formatShortDate(log.actedAt) + " " + formatTime(context, log.actedAt),
                                ),
                                color = DS.colors.ink3, fontSize = 12.sp,
                            )
                        }
                        IconButton({ repository.deleteLog(log.id) }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.progress_delete_log), tint = DS.colors.ink3) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.action_done), onDismiss, Modifier.fillMaxWidth())
    }
}

/** The word for a logged outcome — "Taken" / "Skipped". */
@StringRes internal fun doseStatusRes(status: DoseStatus): Int =
    if (status == DoseStatus.taken) R.string.state_taken else R.string.state_skipped
