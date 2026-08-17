@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

private enum class TreatmentType { ALL, MEDICATIONS, PRESCRIPTIONS }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Care", style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink, modifier = Modifier.weight(1f))
                    SelectChip("+ Add", true, { showAddMode = true })
                }
            }
            item {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TreatmentType.entries.forEach { value -> SelectChip(value.name.lowercase().replaceFirstChar { it.uppercase() }, typeFilter == value, { typeFilter = value }) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TreatmentStatus.entries.forEach { value -> SelectChip(value.name.replaceFirstChar { it.uppercase() }, statusFilter == value, { statusFilter = value }) }
                }
            }
            if (snapshot.medications.isEmpty() && snapshot.prescriptions.isEmpty()) {
                item {
                    Spacer(Modifier.height(74.dp))
                    FriendlyEmptyState(Icons.Default.Medication, "Your cabinet is empty",
                        "Add a prescription or a single medication and your daily plan builds itself.",
                        "Add your first medication", { showAddMode = true })
                }
            } else {
                if (typeFilter != TreatmentType.MEDICATIONS && visiblePrescriptions.isNotEmpty()) {
                    item { SectionLabel("Prescriptions", Modifier.padding(top = 25.dp, bottom = 10.dp)) }
                    items(visiblePrescriptions, key = { it.id }) { rx ->
                        PrescriptionCard(repository, rx) { onPrescription(rx.id) }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (typeFilter != TreatmentType.PRESCRIPTIONS && visibleMedications.isNotEmpty()) {
                    item { SectionLabel(if (typeFilter == TreatmentType.MEDICATIONS || snapshot.prescriptions.isEmpty()) "Medications" else "Standalone", Modifier.padding(top = 18.dp, bottom = 10.dp)) }
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
                            SectionLabel("Archived (${archived.size})"); Spacer(Modifier.width(5.dp))
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
    if (showAddMode) ModalBottomSheet(onDismissRequest = { showAddMode = false }, containerColor = DS.colors.bg3) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("What are we adding?", style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            Spacer(Modifier.height(15.dp))
            ModeCard(Icons.Default.Description, DS.colors.violet, "Prescription", "A treatment plan with one or more medicines") {
                showAddMode = false; showPrescription = true
            }
            Spacer(Modifier.height(10.dp))
            ModeCard(Icons.Default.Medication, DS.colors.mint, "Single medication", "Add it directly to your daily plan") {
                showAddMode = false; onAddMedication(null)
            }
        }
    }
    if (showPrescription) PrescriptionEditor(null, onDismiss = { showPrescription = false }) { value, _ ->
        repository.addPrescription(value); showPrescription = false
    }
}

@Composable
private fun ModeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), radius = 21.dp, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
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
            Row { StatusPill(effectiveStatus.name.replaceFirstChar { it.uppercase() }, when (effectiveStatus) { TreatmentStatus.active -> DS.colors.mint; TreatmentStatus.completed -> DS.colors.cyan; TreatmentStatus.archived -> DS.colors.ink3 }); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink2) }
            Spacer(Modifier.height(20.dp))
            Text(rx.name, style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            val count = repository.medicationsIn(rx.id).size
            Text(listOf(rx.condition, rx.prescriber, "$count ${if (count == 1) "medication" else "medications"}").filter { it.isNotBlank() }.joinToString(" · "),
                color = DS.colors.ink3, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MedicationRow(med: Medication, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        MedicationIcon(med, 40.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(med.name, color = DS.colors.ink, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(med.strengthLabel, med.schedule.summary()).joinToString(" · "), color = DS.colors.ink3, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (med.inventoryEnabled) {
            Text("${prettyNumber(med.stock)} left", color = if (med.needsRefill) DS.colors.amber else DS.colors.ink3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(7.dp))
        }
        if (med.needsRefill) Icon(Icons.Default.Inventory2, null, tint = DS.colors.amber, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp)); Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun MedicationDetailScreen(repository: AppRepository, medicationId: String, onBack: () -> Unit, onEdit: () -> Unit) {
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
                GradientCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        MedicationIcon(med, 66.dp); Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(med.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink)
                            Text(listOfNotNull(med.strengthLabel, med.form.title).joinToString(" · "), color = DS.colors.ink2)
                            Spacer(Modifier.height(7.dp)); StatusPill(medicationStatus.name.replaceFirstChar { it.uppercase() }, when (medicationStatus) { TreatmentStatus.active -> DS.colors.mint; TreatmentStatus.completed -> DS.colors.cyan; TreatmentStatus.archived -> DS.colors.ink3 })
                        }
                    }
                }
            }
            nextDose?.let { next -> item { DetailSection("Next dose") {
                SettingsRow(Icons.Default.Schedule, DS.colors.mint, TimeOfDay.fromEpoch(next.time).label(), SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(next.time)))
                RowDivider(); SettingsRow(Icons.Default.CheckCircle, DS.colors.mint, "Take Now", med.doseLabel, onClick = { logDose = next })
            } } }
            if (nextDose == null && med.schedule.kind == ScheduleKind.asNeeded && medicationStatus == TreatmentStatus.active) item {
                DetailSection("As needed") {
                    SettingsRow(Icons.Default.CheckCircle, DS.colors.mint, "Take Now", med.doseLabel, onClick = { showAsNeeded = true })
                }
            }
            item { DetailSection("Schedule") {
                SettingsRow(Icons.Default.Schedule, DS.colors.mint, med.schedule.summary(), med.schedule.frequencySummary())
                RowDivider(); SettingsRow(Icons.Default.Medication, DS.colors.cyan, med.doseLabel, "Amount per dose")
                if (med.instructions.isNotBlank()) { RowDivider(); SettingsRow(Icons.Default.Notes, DS.colors.violet, med.instructions) }
            } }
            item { DetailSection("Supply") {
                if (med.inventoryEnabled) SettingsRow(Icons.Default.Inventory2, if (med.needsRefill) DS.colors.amber else DS.colors.mint,
                    "${prettyNumber(med.stock)} remaining", "Alert at ${prettyNumber(med.refillReminderThreshold)}" + (med.daysOfStockRemaining?.let { " · About $it days" } ?: ""),
                    onClick = { showRefill = true }) { Text("+ Refill", color = DS.colors.mint, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                else SettingsRow(Icons.Default.Inventory2, DS.colors.ink3, "Inventory not tracked", "Enable tracking when editing this medication")
            } }
            val history = repository.logs.filter { it.medicationID == med.id }.sortedByDescending { it.actedAt }.take(10)
            if (history.isNotEmpty()) item { DetailSection("Recent history") {
                history.forEachIndexed { index, log ->
                    if (index > 0) RowDivider()
                    SettingsRow(if (log.status == DoseStatus.taken) Icons.Default.CheckCircle else Icons.Default.RemoveCircle,
                        if (log.status == DoseStatus.taken) DS.colors.mint else DS.colors.amber,
                        if (log.isAsNeeded) "As-needed dose" else log.status.name.replaceFirstChar { it.uppercase() },
                        SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(log.actedAt)))
                }
                RowDivider(); SettingsRow(Icons.Default.List, DS.colors.cyan, "View medication logs", "Full taken and skipped history", onClick = { showLogs = true })
            } }
            item {
                Spacer(Modifier.height(18.dp))
                OutlinedButton({ if (med.isArchived) repository.archiveMedication(med.id, false) else confirmArchive = true }, Modifier.fillMaxWidth()) {
                    Icon(if (med.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, null); Spacer(Modifier.width(7.dp)); Text(if (med.isArchived) "Restore medication" else "Archive medication")
                }
                TextButton({ confirmDelete = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = DS.colors.coral)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(7.dp)); Text("Delete medication")
                }
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${med.name}?") },
        text = { Text("Its medication history will also be removed. This cannot be undone.") },
        confirmButton = { TextButton({ repository.deleteMedication(med.id); onBack() }) { Text("Delete", color = DS.colors.coral) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
    if (confirmArchive) AlertDialog(onDismissRequest = { confirmArchive = false }, title = { Text("Archive ${med.name}?") },
        text = { Text("Upcoming reminders will stop. You can restore this medication later from Archived.") },
        confirmButton = { TextButton({ repository.archiveMedication(med.id, true); confirmArchive = false }) { Text("Archive") } },
        dismissButton = { TextButton({ confirmArchive = false }) { Text("Cancel") } })
    if (showRefill) AlertDialog(onDismissRequest = { showRefill = false }, title = { Text("Add a refill") },
        text = { OutlinedTextField(refillAmount, { refillAmount = it.filter(Char::isDigit) }, label = { Text("Units to add") }) },
        confirmButton = { TextButton({ repository.refillStock(med.id, refillAmount.toDoubleOrNull() ?: 0.0); showRefill = false }) { Text("Add") } },
        dismissButton = { TextButton({ showRefill = false }) { Text("Cancel") } })
    logDose?.let { dose -> LogDoseDialog(repository, dose, { logDose = null }) }
    if (showAsNeeded) AsNeededSheet(repository, med) { showAsNeeded = false }
    if (showLogs) LogsDialog("${med.name} logs", repository.logs.filter { it.medicationID == med.id }.sortedByDescending { it.actedAt }, repository, { showLogs = false })
}

@Composable
fun PrescriptionDetailScreen(
    repository: AppRepository, prescriptionId: String, isPro: Boolean, onBack: () -> Unit,
    onMedication: (String) -> Unit, onAddMedication: () -> Unit,
) {
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
                GradientCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(21.dp)) {
                        StatusPill(effectiveStatus.name.replaceFirstChar { it.uppercase() } + " treatment", when (effectiveStatus) { TreatmentStatus.active -> DS.colors.mint; TreatmentStatus.completed -> DS.colors.cyan; TreatmentStatus.archived -> DS.colors.ink3 }); Spacer(Modifier.height(18.dp))
                        Text(rx.name, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink)
                        if (rx.condition.isNotBlank()) Text(rx.condition, color = DS.colors.ink2)
                        if (rx.prescriber.isNotBlank()) Text("Prescribed by ${rx.prescriber}", color = DS.colors.ink3, fontSize = 12.sp)
                        if (rx.facility.isNotBlank()) Text(rx.facility, color = DS.colors.ink3, fontSize = 12.sp)
                        if (rx.contact.isNotBlank()) Text(rx.contact, color = DS.colors.ink3, fontSize = 12.sp)
                        Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(rx.startDate)) + (rx.endDate?.let { " – ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it))}" } ?: " · Ongoing"), color = DS.colors.ink3, fontSize = 12.sp)
                    }
                }
            }
            item { SectionLabel("Medications", Modifier.padding(top = 24.dp, bottom = 10.dp)) }
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                    medications.forEachIndexed { index, med -> if (index > 0) RowDivider(); MedicationRow(med) { onMedication(med.id) } }
                    if (medications.isNotEmpty()) RowDivider()
                    SettingsRow(Icons.Default.Add, DS.colors.mint, "Add medication", if (!isPro && repository.activeMedications.isNotEmpty()) "MediTick Pro" else "New or existing medication", onClick = { if (!isPro && repository.activeMedications.isNotEmpty()) onAddMedication() else addMenu = true })
                }
            }
            item { DetailSection("Progress & logs") {
                SettingsRow(Icons.Default.BarChart, DS.colors.cyan,
                    if (rxStats.decided == 0) "No decided doses yet" else "${(rxStats.ratio * 100).toInt()}% adherence",
                    "${rxStats.taken} taken · ${rxStats.skipped} skipped · ${rxStats.missed} missed")
                RowDivider(); SettingsRow(Icons.Default.Timer, DS.colors.violet,
                    rxOnTime?.let { "${(it * 100).toInt()}% on time" } ?: "No taken doses yet",
                    "Within ±${progressSettings.onTimeWindowMinutes} minutes")
                RowDivider(); SettingsRow(Icons.Default.List, DS.colors.mint, "View prescription logs", "Taken, skipped and as-needed history", onClick = { showLogs = true })
            } }
            if (rx.notes.isNotBlank()) item { DetailSection("Notes") { Text(rx.notes, Modifier.padding(16.dp), color = DS.colors.ink2) } }
            item {
                Spacer(Modifier.height(22.dp))
                when (effectiveStatus) {
                    TreatmentStatus.active -> {
                        OutlinedButton({ pendingStatus = TreatmentStatus.completed }, Modifier.fillMaxWidth()) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text("Mark as complete") }
                        OutlinedButton({ pendingStatus = TreatmentStatus.archived }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Archive, null); Spacer(Modifier.width(7.dp)); Text("Archive prescription") }
                    }
                    TreatmentStatus.completed -> OutlinedButton({ pendingStatus = TreatmentStatus.active }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(7.dp)); Text("Reactivate prescription") }
                    TreatmentStatus.archived -> OutlinedButton({ pendingStatus = TreatmentStatus.active }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(7.dp)); Text("Restore prescription") }
                }
                TextButton({ delete = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = DS.colors.coral)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(7.dp)); Text("Delete prescription")
                }
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }
    if (edit) PrescriptionEditor(rx, { edit = false }, linkedMedicationCount = medications.size) { value, updateDates ->
        repository.updatePrescription(value, updateDates); edit = false
    }
    pendingStatus?.let { status ->
        val reactivating = status == TreatmentStatus.active && effectiveStatus == TreatmentStatus.completed
        AlertDialog(onDismissRequest = { pendingStatus = null },
        title = { Text(when (status) { TreatmentStatus.completed -> "Mark as complete?"; TreatmentStatus.archived -> "Archive prescription?"; TreatmentStatus.active -> if (reactivating) "Reactivate prescription?" else "Restore prescription?" }) },
        text = { Text(when (status) { TreatmentStatus.completed -> "Upcoming reminders for linked medications will stop."; TreatmentStatus.archived -> "The prescription and its linked medications move to Archived. You can restore them later."; TreatmentStatus.active -> if (reactivating) "The course becomes ongoing and reminders resume. Medications completed independently stay completed." else "Only medications changed by this prescription will be restored; medications you archived yourself stay archived." }) },
        confirmButton = { TextButton({ repository.setPrescriptionStatus(rx.id, status); pendingStatus = null }) { Text(if (status == TreatmentStatus.active) if (reactivating) "Reactivate" else "Restore" else if (status == TreatmentStatus.completed) "Complete" else "Archive") } },
        dismissButton = { TextButton({ pendingStatus = null }) { Text("Cancel") } }) }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete this prescription?") },
        text = { Text("Its medications will stay in your cabinet as standalone medications.") },
        confirmButton = { TextButton({ repository.deletePrescription(rx.id); onBack() }) { Text("Delete", color = DS.colors.coral) } },
        dismissButton = { TextButton({ delete = false }) { Text("Cancel") } })
    if (addMenu) ModalBottomSheet(onDismissRequest = { addMenu = false }, containerColor = DS.colors.bg3) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("Add medication", style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            SettingsRow(Icons.Default.Add, DS.colors.mint, "Add new medication", "Create and link a new reminder", onClick = { addMenu = false; onAddMedication() })
            SettingsRow(Icons.Default.Link, DS.colors.cyan, "Add existing medications", "Link a standalone medication", onClick = { addMenu = false; chooseExisting = true })
        }
    }
    if (chooseExisting) ModalBottomSheet(onDismissRequest = { chooseExisting = false }, containerColor = DS.colors.bg3) {
        val standalone = repository.medications.filter { it.prescriptionID == null }
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text("Add existing medications", style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            if (standalone.isEmpty()) Text("No standalone medications are available.", color = DS.colors.ink3, modifier = Modifier.padding(vertical = 20.dp))
            standalone.forEach { med -> SettingsRow(Icons.Default.Medication, DS.colors.mint, med.name, med.strengthLabel, onClick = { repository.linkMedication(med.id, rx.id); chooseExisting = false }) }
        }
    }
    if (showLogs) LogsDialog("${rx.name} logs", repository.logsForPrescription(rx.id), repository, { showLogs = false })
}

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = DS.colors.ink) }
        Text(title, Modifier.weight(1f), color = DS.colors.ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onEdit) { Icon(Icons.Default.Edit, "Edit", tint = DS.colors.mint) }
    }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "New prescription" else "Edit prescription") },
        text = { LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, singleLine = true) }
            item { OutlinedTextField(condition, { condition = it }, label = { Text("Condition") }, singleLine = true) }
            item { OutlinedTextField(prescriber, { prescriber = it }, label = { Text("Prescriber") }, singleLine = true) }
            item { OutlinedTextField(facility, { facility = it }, label = { Text("Facility / clinic") }, singleLine = true) }
            item { OutlinedTextField(contact, { contact = it }, label = { Text("Contact") }, singleLine = true) }
            item { OutlinedButton({ showDatePicker(context, startDate) { startDate = it; if (endDate < it) endDate = it } }, Modifier.fillMaxWidth()) { Text("Start · ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(startDate))}") } }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Ongoing treatment", Modifier.weight(1f)); Switch(ongoing, { ongoing = it }) } }
            if (!ongoing) item { OutlinedButton({ showDatePicker(context, endDate) { endDate = it.coerceAtLeast(startDate) } }, Modifier.fillMaxWidth()) { Text("End · ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(endDate))}") } }
            if (existing != null && linkedMedicationCount > 0) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Update $linkedMedicationCount ${if (linkedMedicationCount == 1) "medication" else "medications"}")
                        Text("Apply these start and end dates to linked schedules", color = DS.colors.ink3, fontSize = 11.sp)
                    }
                    Switch(updateLinkedDates, { updateLinkedDates = it })
                }
            }
            item { OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2) }
        } },
        confirmButton = { TextButton({ if (name.isNotBlank()) onSave((existing ?: Prescription()).copy(name = name.trim(), condition = condition.trim(), prescriber = prescriber.trim(), facility = facility.trim(), contact = contact.trim(), startDate = startDate, endDate = if (ongoing) null else endDate, notes = notes.trim()), updateLinkedDates) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Log dose") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${dose.medication.name} · ${dose.medication.doseLabel}")
            OutlinedButton({
                val calendar = Calendar.getInstance().apply { timeInMillis = actedAt }
                android.app.TimePickerDialog(context, { _, hour, minute ->
                    actedAt = Calendar.getInstance().apply { timeInMillis = actedAt; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.timeInMillis
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(7.dp)); Text("Logged time · ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(actedAt))}") }
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton({ repository.logDose(dose, DoseStatus.taken, actedAt, note); onDismiss() }) { Text("Take") } },
        dismissButton = { Row { TextButton({ repository.logDose(dose, DoseStatus.skipped, actedAt, note); onDismiss() }) { Text("Skip") }; TextButton(onDismiss) { Text("Cancel") } } })
}

@Composable
fun LogsDialog(title: String, logs: List<DoseLog>, repository: AppRepository, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        if (logs.isEmpty()) Text("No doses have been logged yet.") else LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(logs, key = { it.id }) { log ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (log.status == DoseStatus.taken) Icons.Default.CheckCircle else Icons.Default.RemoveCircle, null, tint = if (log.status == DoseStatus.taken) DS.colors.mint else DS.colors.amber)
                    Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) {
                        Text(repository.medication(log.medicationID)?.name ?: "Medication", color = DS.colors.ink, fontWeight = FontWeight.Bold)
                        Text("${log.status.name.replaceFirstChar { it.uppercase() }} · ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(log.actedAt))}", color = DS.colors.ink3, fontSize = 12.sp)
                    }
                    IconButton({ repository.deleteLog(log.id) }) { Icon(Icons.Default.DeleteOutline, "Delete log", tint = DS.colors.ink3) }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Done") } })
}
