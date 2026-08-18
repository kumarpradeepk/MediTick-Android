@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kabi.pillpal.meditick.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.data.CatalogEntry
import com.kabi.pillpal.meditick.data.DoseTimePresets
import com.kabi.pillpal.meditick.data.MedicationCatalog
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS
import java.util.Calendar
import java.util.UUID

private enum class RhythmMode(val title: String, val subtitle: String) {
    EVERY_DAY("Every day", "Same times, daily"), SPECIFIC("Specific days", "Choose weekdays"),
    EVERY_OTHER("Every other day", "One day on, one off"), INTERVAL("Interval", "Every N calendar days"), CYCLIC("Cyclic", "Days on, then days off"),
    AS_NEEDED("As needed", "No fixed schedule"),
}

internal enum class FormMealRelation { FIXED, BEFORE, WITH, AFTER }

/**
 * One dose being edited: its amount, and either a fixed time or a meal
 * relation. Per-dose relations let a medication be 08:00 fixed *and* 30 minutes
 * before dinner.
 */
internal data class FormDose(
    val id: String = UUID.randomUUID().toString(),
    val time: TimeOfDay = TimeOfDay(8, 0),
    val amount: Double = 1.0,
    val relation: FormMealRelation = FormMealRelation.FIXED,
    val mealSlot: MealSlot = MealSlot.breakfast,
    val offsetMinutes: Int = 30,
) {
    val anchor: MealAnchor?
        get() = when (relation) {
            FormMealRelation.FIXED -> null
            FormMealRelation.BEFORE -> MealAnchor(slot = mealSlot, relation = MealRelation.before, offsetMinutes = offsetMinutes)
            FormMealRelation.WITH -> MealAnchor(slot = mealSlot, relation = MealRelation.with, offsetMinutes = 0)
            FormMealRelation.AFTER -> MealAnchor(slot = mealSlot, relation = MealRelation.after, offsetMinutes = offsetMinutes)
        }

    fun firingTime(mealTimes: MealTimes): TimeOfDay {
        val meal = anchor ?: return time
        val base = mealTimes.time(meal.slot).totalMinutes + meal.signedOffset
        val wrapped = ((base % 1440) + 1440) % 1440
        return TimeOfDay(wrapped / 60, wrapped % 60)
    }

    fun relationLabel(): String = anchor?.label() ?: "Fixed time"
}

private fun FormMealRelation.title() = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable
fun MedicationFormScreen(
    repository: AppRepository, editingId: String?, prescriptionId: String?,
    onClose: () -> Unit, onSaved: () -> Unit,
) {
    val existing = repository.medication(editingId)
    val context = LocalContext.current
    val catalog = remember { MedicationCatalog.get(context) }
    val presets = remember { SettingsStore.get(context) }.doseTimePresets
    var step by remember { mutableIntStateOf(if (existing == null) 0 else 1) }
    var describe by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var strength by remember { mutableStateOf(existing?.strengthValue?.let(::prettyNumber).orEmpty()) }
    var strengthUnit by remember { mutableStateOf(existing?.strengthUnit ?: "mg") }
    var form by remember { mutableStateOf(existing?.form ?: MedicationForm.tablet) }
    var instructions by remember { mutableStateOf(existing?.instructions.orEmpty()) }
    var trackStock by remember { mutableStateOf(existing?.inventoryEnabled ?: false) }
    var stock by remember { mutableStateOf(prettyNumber(existing?.stock ?: 30.0)) }
    var alertAt by remember { mutableStateOf(prettyNumber(existing?.refillReminderThreshold ?: 7.0)) }
    var associationId by remember { mutableStateOf(existing?.prescriptionID ?: prescriptionId) }
    var mode by remember { mutableStateOf(initialMode(existing?.schedule)) }
    var weekdays by remember { mutableStateOf(existing?.schedule?.weekdays ?: emptySet()) }
    var cycleOn by remember { mutableIntStateOf(existing?.schedule?.cycleActiveDays ?: 21) }
    var cycleOff by remember { mutableIntStateOf(existing?.schedule?.cyclePauseDays ?: 7) }
    var dayInterval by remember { mutableIntStateOf(existing?.schedule?.dayInterval ?: 2) }
    val mealTimes = repository.mealTimes
    var doses by remember { mutableStateOf(initialDoses(existing?.schedule, presets)) }
    var amount by remember { mutableStateOf(prettyNumber(existing?.schedule?.amountPerDose ?: 1.0)) }
    var ongoing by remember { mutableStateOf(existing?.schedule?.endDate == null) }
    var startDate by remember { mutableLongStateOf(existing?.schedule?.startDate ?: startOfToday()) }
    var durationDays by remember { mutableIntStateOf(existing?.schedule?.endDate?.let { ((it - existing.schedule.startDate) / 86_400_000L).toInt().coerceAtLeast(1) } ?: 14) }
    var duplicateName by remember { mutableStateOf<String?>(null) }

    val suggestions = remember(name) { catalog.search(name) }
    ScreenBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Default.Close, "Close", tint = DS.colors.ink) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (existing == null) "Add medication" else "Edit medication", color = DS.colors.ink, fontWeight = FontWeight.Bold)
                    Text("Step ${step + 1} of 3", color = DS.colors.ink3, fontSize = 11.sp)
                }
                Spacer(Modifier.width(48.dp))
            }
            LinearProgressIndicator(progress = { (step + 1) / 3f }, Modifier.fillMaxWidth().height(2.dp), color = DS.colors.mint, trackColor = DS.colors.line)
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> DescribeStep(describe, { describe = it }, catalog, presets, onParsed = { parsed ->
                        name = parsed.name; parsed.strength?.let { strength = it.first; strengthUnit = it.second }
                        parsed.form?.let { form = it }; doses = parsed.doses
                        parsed.durationDays?.let { durationDays = it; ongoing = false }
                        step = 1
                    })
                    1 -> BasicsStep(name, { name = it }, suggestions, { entry ->
                        name = entry.name; form = entry.form
                        entry.strengths.firstOrNull()?.let { parseStrength(it) }?.let { strength = it.first; strengthUnit = it.second }
                    }, strength, { strength = it }, strengthUnit, { strengthUnit = it }, form, { form = it }, instructions, { instructions = it },
                        trackStock, { trackStock = it }, stock, { stock = it }, alertAt, { alertAt = it },
                        repository.prescriptions, associationId, { associationId = it })
                    else -> RhythmStep(mode, { mode = it }, weekdays, { weekdays = it }, cycleOn, { cycleOn = it }, cycleOff, { cycleOff = it },
                        dayInterval, { dayInterval = it }, startDate, { startDate = it },
                        doses, { doses = it }, amount, { amount = it },
                        ongoing, { ongoing = it }, durationDays, { durationDays = it }, presets, mealTimes, repository)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0 && !(existing != null && step == 1)) OutlinedButton({ step-- }, Modifier.height(56.dp)) { Icon(Icons.Default.ArrowBack, null) }
                PrimaryButton(
                    if (step == 2) if (existing == null) "Add to my routine" else "Save changes" else "Continue",
                    onClick = {
                        if (step == 0) {
                            smartParse(describe, catalog, presets)?.let { parsed ->
                                name = parsed.name
                                parsed.strength?.let { strength = it.first; strengthUnit = it.second }
                                parsed.form?.let { form = it }
                                doses = parsed.doses
                                parsed.durationDays?.let { durationDays = it; ongoing = false }
                            }
                            step = 1
                        } else if (step == 1) step = 2 else {
                            val parsedStrength = strength.toDoubleOrNull()
                            val duplicate = repository.duplicateMedication(name, parsedStrength, strengthUnit, existing?.id)
                            if (duplicate != null) { duplicateName = duplicate.name; return@PrimaryButton }
                            val schedule = buildSchedule(mode, weekdays, cycleOn, cycleOff, dayInterval, doses,
                                amount.toDoubleOrNull() ?: 1.0, ongoing, durationDays, startDate, mealTimes)
                            val medication = (existing ?: Medication()).copy(
                                name = name.trim(), form = form, strengthValue = parsedStrength, strengthUnit = strengthUnit,
                                colorName = existing?.colorName ?: PillColor.entries[(repository.medications.size) % PillColor.entries.size].name,
                                schedule = schedule, prescriptionID = associationId,
                                instructions = instructions.trim(), inventoryEnabled = trackStock,
                                stock = stock.toDoubleOrNull() ?: 30.0, refillReminderThreshold = alertAt.toDoubleOrNull() ?: 7.0,
                            )
                            if (existing == null) repository.addMedication(medication) else repository.updateMedication(medication)
                            onSaved()
                        }
                    }, modifier = Modifier.weight(1f), enabled = when (step) { 0 -> describe.trim().length > 2; 1 -> name.isNotBlank(); else -> mode == RhythmMode.AS_NEEDED || doses.isNotEmpty() },
                    leading = if (step == 2) Icons.Default.Check else Icons.Default.ArrowForward,
                )
            }
        }
    }
    duplicateName?.let { duplicate -> AlertDialog(onDismissRequest = { duplicateName = null }, title = { Text("Duplicate medication") },
        text = { Text("A medication named '$duplicate' with this strength already exists.") },
        confirmButton = { TextButton({ duplicateName = null }) { Text("OK") } }) }
}

private data class ParsedDraft(
    val name: String, val strength: Pair<String, String>?, val form: MedicationForm?,
    val doses: List<FormDose>, val durationDays: Int? = null,
)

@Composable
private fun DescribeStep(text: String, onText: (String) -> Unit, catalog: MedicationCatalog, presets: DoseTimePresets, onParsed: (ParsedDraft) -> Unit) {
    val parsed = remember(text, presets) { smartParse(text, catalog, presets) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp)) {
        item {
            SectionLabel("Say it naturally")
            Spacer(Modifier.height(8.dp)); Text("What do you take?", style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink)
            Spacer(Modifier.height(7.dp)); Text("Try “Metformin 500mg every morning with food”.", color = DS.colors.ink2)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(text, onText, Modifier.fillMaxWidth(), minLines = 4, textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                placeholder = { Text("Medication, strength and when you take it…") })
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("every morning", "twice daily", "before meals", "after meals", "at 9pm", "500mg").forEach { chip ->
                    SelectChip(chip, false, { onText((text.trim() + " " + chip).trim()) })
                }
            }
            Spacer(Modifier.height(14.dp)); SectionLabel("Examples")
            listOf(
                "Amoxicillin 500mg at 08:00 and 20:00 for 7 days",
                "Metformin 500mg every morning with food",
                "Omeprazole 20mg 30 minutes before breakfast",
                "Vitamin D 1000 IU at 09:00",
            ).forEach { example -> TextButton({ onText(example) }, Modifier.fillMaxWidth()) { Text(example, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) } }
            parsed?.let {
                Spacer(Modifier.height(20.dp)); GradientCard(Modifier.fillMaxWidth(), onClick = { onParsed(it) }) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Default.AutoAwesome, DS.colors.mint, 46.dp); Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            SectionLabel("MediTick understood")
                            Text(listOfNotNull(it.name, it.strength?.let { s -> "${s.first} ${s.second}" }).joinToString(" · "), color = DS.colors.ink, fontWeight = FontWeight.Bold)
                            Text(it.doses.joinToString { d -> d.time.label() }, color = DS.colors.ink3, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = DS.colors.mint)
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicsStep(
    name: String, onName: (String) -> Unit, suggestions: List<CatalogEntry>, onSuggestion: (CatalogEntry) -> Unit,
    strength: String, onStrength: (String) -> Unit, unit: String, onUnit: (String) -> Unit,
    form: MedicationForm, onForm: (MedicationForm) -> Unit, instructions: String, onInstructions: (String) -> Unit,
    track: Boolean, onTrack: (Boolean) -> Unit, stock: String, onStock: (String) -> Unit, alert: String, onAlert: (String) -> Unit,
    prescriptions: List<Prescription>, associationId: String?, onAssociation: (String?) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionLabel("The basics"); Spacer(Modifier.height(8.dp)); Text("Make it recognisable", style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink) }
        item { OutlinedTextField(name, onName, label = { Text("Medication name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text(prescriptions.firstOrNull { it.id == associationId }?.name ?: "None (Standalone)", modifier = Modifier.weight(1f)); Icon(Icons.Default.ExpandMore, null)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("None (Standalone)") }, { onAssociation(null); menu = false })
                    prescriptions.filter { it.effectiveStatus() == TreatmentStatus.active }.forEach { rx -> DropdownMenuItem({ Text(rx.name) }, { onAssociation(rx.id); menu = false }) }
                }
            }
        }
        if (suggestions.isNotEmpty() && name.length >= 2) item {
            GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, contentPadding = PaddingValues(vertical = 2.dp)) {
                suggestions.take(4).forEachIndexed { index, entry ->
                    if (index > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable { onSuggestion(entry) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(formIcon(entry.form), null, tint = DS.colors.mint); Spacer(Modifier.width(10.dp))
                        Column { Text(entry.name, color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(entry.strengths.take(3).joinToString(), color = DS.colors.ink3, fontSize = 11.sp) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(strength, { onStrength(it.filter { ch -> ch.isDigit() || ch == '.' }) }, label = { Text("Strength") }, modifier = Modifier.weight(1f), singleLine = true)
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ menu = true }, Modifier.height(56.dp)) { Text(unit); Icon(Icons.Default.ExpandMore, null) }
                    DropdownMenu(menu, { menu = false }) { StrengthUnit.all.forEach { DropdownMenuItem({ Text(it) }, { onUnit(it); menu = false }) } }
                }
            }
        }
        item { SectionLabel("Medication form"); FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MedicationForm.pickerOrder.forEach { SelectChip(it.title, form == it, { onForm(it) }) }
        } }
        item { OutlinedTextField(instructions, onInstructions, label = { Text("Instructions (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.Inventory2, DS.colors.amber); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Track supply", color = DS.colors.ink, fontWeight = FontWeight.Bold); Text("Refill before you run out", color = DS.colors.ink3, fontSize = 12.sp) }
                Switch(track, onTrack)
            }
            if (track) { Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(stock, { onStock(it.filter(Char::isDigit)) }, label = { Text("In stock") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(alert, { onAlert(it.filter(Char::isDigit)) }, label = { Text("Alert at") }, modifier = Modifier.weight(1f), singleLine = true)
            } }
        } }
    }
}

@Composable
private fun RhythmStep(
    mode: RhythmMode, onMode: (RhythmMode) -> Unit, weekdays: Set<Int>, onWeekdays: (Set<Int>) -> Unit,
    cycleOn: Int, onCycleOn: (Int) -> Unit, cycleOff: Int, onCycleOff: (Int) -> Unit,
    dayInterval: Int, onDayInterval: (Int) -> Unit, startDate: Long, onStartDate: (Long) -> Unit,
    doses: List<FormDose>, onDoses: (List<FormDose>) -> Unit,
    amount: String, onAmount: (String) -> Unit, ongoing: Boolean, onOngoing: (Boolean) -> Unit,
    duration: Int, onDuration: (Int) -> Unit, presets: DoseTimePresets, mealTimes: MealTimes,
    repository: AppRepository,
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<FormDose?>(null) }
    var showAddDose by remember { mutableStateOf(false) }
    var showMealTimes by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionLabel("Your rhythm"); Spacer(Modifier.height(8.dp)); Text("When does it fit?", style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            RhythmMode.entries.forEach { SelectChip(it.title, mode == it, { onMode(it) }) }
        } }
        if (mode == RhythmMode.SPECIFIC) item {
            SectionLabel("Days"); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { index, title ->
                    SelectChip(title, index + 1 in weekdays, { onWeekdays(if (index + 1 in weekdays) weekdays - (index + 1) else weekdays + (index + 1)) })
                }
            }
        }
        if (mode == RhythmMode.CYCLIC) item { NumberPair("Days on", cycleOn, onCycleOn, "Days off", cycleOff, onCycleOff) }
        if (mode == RhythmMode.INTERVAL) item { OutlinedTextField(dayInterval.toString(), { onDayInterval(it.toIntOrNull()?.coerceIn(2, 365) ?: 2) }, label = { Text("Repeat every N days") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (mode != RhythmMode.AS_NEEDED) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Doses")
                    Spacer(Modifier.width(8.dp))
                    Text(if (doses.size == 1) "1 time/day" else "${doses.size} times/day", color = DS.colors.ink3, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton({ showAddDose = true }) { Icon(Icons.Default.Add, null); Text("Add Dose") }
                }
                doses.forEach { dose ->
                    Spacer(Modifier.height(8.dp))
                    GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, onClick = { editing = dose }, contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.Schedule, DS.colors.mint); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(dose.firingTime(mealTimes).label(), color = DS.colors.ink, fontWeight = FontWeight.Bold)
                                Text("${prettyNumber(dose.amount)} • ${dose.relationLabel()}", color = DS.colors.ink3, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3)
                        }
                    }
                }
            }
            // Only relevant once something is actually tied to a meal.
            if (doses.any { it.relation != FormMealRelation.FIXED }) item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Meal-linked reminders")
                        Spacer(Modifier.weight(1f))
                        TextButton({ showMealTimes = true }) { Text("Edit") }
                    }
                    Text("Changing meal times updates reminders automatically", color = DS.colors.ink3, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        MealSlot.entries.forEach { slot ->
                            StatusPill("${slot.name.replaceFirstChar(Char::uppercase)} ${mealTimes.time(slot).label()}", DS.colors.ink2)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Synced with reminder times", color = DS.colors.mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (mode == RhythmMode.AS_NEEDED) item {
            OutlinedTextField(amount, { onAmount(it.filter { ch -> ch.isDigit() || ch == '.' }) }, label = { Text("Amount per dose") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (mode != RhythmMode.AS_NEEDED) item {
            OutlinedButton({
                val cal = Calendar.getInstance().apply { timeInMillis = startDate }
                android.app.DatePickerDialog(context, { _, y, m, d -> onStartDate(Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }, Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("Start date · ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(startDate))}") }
        }
        if (mode != RhythmMode.AS_NEEDED) item { GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Ongoing treatment", color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(if (ongoing) "No end date" else "$duration day course", color = DS.colors.ink3, fontSize = 12.sp) }
                Switch(ongoing, onOngoing)
            }
            if (!ongoing) { Spacer(Modifier.height(10.dp)); Slider(duration.toFloat(), { onDuration(it.toInt()) }, valueRange = 1f..90f, steps = 88) }
        } }
    }

    if (showAddDose) {
        AddDoseDialog(presets, onDismiss = { showAddDose = false }) { added ->
            onDoses((doses + added).sortedBy { it.time.totalMinutes })
            showAddDose = false
        }
    }
    editing?.let { target ->
        DoseEditorDialog(
            draft = target, mealTimes = mealTimes,
            onDismiss = { editing = null },
            onSave = { updated ->
                onDoses(doses.map { if (it.id == updated.id) updated else it })
                editing = null
            },
            onRemove = {
                onDoses(doses.filterNot { it.id == target.id })
                editing = null
            },
            canRemove = doses.size > 1,
        )
    }
    if (showMealTimes) MealTimesDialog(repository, onDismiss = { showMealTimes = false })
}

/** Picks a time for a new dose: a common preset, or any time at all. */
@Composable
private fun AddDoseDialog(presets: DoseTimePresets, onDismiss: () -> Unit, onAdd: (FormDose) -> Unit) {
    val context = LocalContext.current
    val labels = listOf("Morning", "Midday", "Evening", "Bedtime")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Dose") },
        text = {
            Column {
                Text("Choose an exact time or start from a common preset.", color = DS.colors.ink3, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                presets.all().forEachIndexed { index, preset ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAdd(FormDose(time = preset)) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(labels[index], color = DS.colors.ink)
                        Spacer(Modifier.weight(1f))
                        Text(preset.label(), color = DS.colors.ink2, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton({
                    TimePickerDialog(context, { _, h, m -> onAdd(FormDose(time = TimeOfDay(h, m))) },
                        presets.morning.hour, presets.morning.minute, false).show()
                }) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Custom Time") }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Edits one dose: how much, and whether it is a fixed time or tied to a meal. */
@Composable
private fun DoseEditorDialog(
    draft: FormDose, mealTimes: MealTimes, onDismiss: () -> Unit,
    onSave: (FormDose) -> Unit, onRemove: () -> Unit, canRemove: Boolean,
) {
    val context = LocalContext.current
    var working by remember(draft.id) { mutableStateOf(draft) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Dose") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(working.firingTime(mealTimes).label(), color = DS.colors.mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                SectionLabel("Amount")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Half-dose steps: splitting a tablet is routine.
                    IconButton({ working = working.copy(amount = (working.amount - 0.5).coerceAtLeast(0.5)) }) {
                        Icon(Icons.Default.Remove, "Decrease dose", tint = DS.colors.ink)
                    }
                    Text(prettyNumber(working.amount), color = DS.colors.ink, fontWeight = FontWeight.Bold)
                    IconButton({ working = working.copy(amount = (working.amount + 0.5).coerceAtMost(20.0)) }) {
                        Icon(Icons.Default.Add, "Increase dose", tint = DS.colors.ink)
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel("Timing")
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    FormMealRelation.entries.forEach { option ->
                        SelectChip(option.title(), working.relation == option, { working = working.copy(relation = option) })
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (working.relation == FormMealRelation.FIXED) {
                    OutlinedButton({
                        TimePickerDialog(context, { _, h, m -> working = working.copy(time = TimeOfDay(h, m)) },
                            working.time.hour, working.time.minute, false).show()
                    }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(8.dp))
                        Text("Reminder time · ${working.time.label()}")
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        MealSlot.entries.forEach { slot ->
                            SelectChip(slot.name.replaceFirstChar(Char::uppercase), working.mealSlot == slot, { working = working.copy(mealSlot = slot) })
                        }
                    }
                    if (working.relation != FormMealRelation.WITH) {
                        Spacer(Modifier.height(10.dp))
                        Text("${working.offsetMinutes} minutes ${working.relation.name.lowercase()} the meal", color = DS.colors.ink2, fontSize = 12.sp)
                        Slider(
                            working.offsetMinutes.toFloat(),
                            { working = working.copy(offsetMinutes = it.toInt()) },
                            valueRange = 0f..180f, steps = 35,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${working.mealSlot.name.replaceFirstChar(Char::uppercase)} at ${mealTimes.time(working.mealSlot).label()} · " +
                            "changing meal times updates reminders automatically",
                        color = DS.colors.ink3, fontSize = 12.sp,
                    )
                }

                if (canRemove) {
                    Spacer(Modifier.height(14.dp))
                    TextButton({ onRemove() }) { Icon(Icons.Default.RemoveCircle, null, tint = DS.colors.coral); Spacer(Modifier.width(6.dp)); Text("Remove Dose", color = DS.colors.coral) }
                }
            }
        },
        confirmButton = { TextButton({ onSave(working) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable private fun NumberPair(a: String, av: Int, setA: (Int) -> Unit, b: String, bv: Int, setB: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(av.toString(), { setA(it.toIntOrNull()?.coerceIn(1, 90) ?: 1) }, label = { Text(a) }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(bv.toString(), { setB(it.toIntOrNull()?.coerceIn(1, 90) ?: 1) }, label = { Text(b) }, modifier = Modifier.weight(1f), singleLine = true)
    }
}

private fun smartParse(text: String, catalog: MedicationCatalog, presets: DoseTimePresets): ParsedDraft? {
    val clean = text.trim(); if (clean.length < 3) return null
    val words = clean.split(Regex("[\\s,]+"))
    val stop = setOf("every", "each", "twice", "once", "daily", "take", "with", "before", "after", "morning", "night", "evening", "at")
    val word = words.firstOrNull { it.length >= 3 && it.all(Char::isLetter) && it.lowercase() !in stop } ?: return null
    val entry = catalog.resolve(word)
    val name = entry?.name ?: word.replaceFirstChar(Char::uppercase)
    val strengthToken = words.firstNotNullOfOrNull(::parseStrength)
    val lower = clean.lowercase()
    val amPm = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").findAll(lower).mapNotNull { match ->
        var hour = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 1..12 || minute !in 0..59) return@mapNotNull null
        if (match.groupValues[3] == "pm") hour = (hour % 12) + 12 else hour %= 12
        TimeOfDay(hour, minute)
    }.toList()
    val twentyFourHour = Regex("(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)(?!\\s*(?:am|pm))").findAll(lower).mapNotNull { match ->
        val hour = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val minute = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        TimeOfDay(hour, minute)
    }.toList()
    val slots = buildList {
        if ("morning" in lower || "breakfast" in lower) add(presets.morning)
        if ("lunch" in lower || "noon" in lower) add(presets.midday)
        if ("evening" in lower || "dinner" in lower) add(presets.evening)
        if ("bed" in lower || "night" in lower) add(presets.bedtime)
    }
    val times = (amPm + twentyFourHour + slots).distinct().ifEmpty { if ("twice" in lower) listOf(presets.morning, presets.evening) else listOf(presets.morning) }
    val relation = when { "before" in lower -> FormMealRelation.BEFORE; "after" in lower -> FormMealRelation.AFTER; "with food" in lower || "with meal" in lower -> FormMealRelation.WITH; else -> FormMealRelation.FIXED }
    val duration = Regex("(?:for\\s+)?(\\d{1,3})\\s*days?").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 365)
    // The parser reads one relation for the whole phrase; seed every dose with
    // it, then each can be retimed on its own in the editor.
    val doses = times.sorted().map { time ->
        FormDose(
            time = time,
            relation = relation,
            mealSlot = when (time.hour) {
                in 5..10 -> MealSlot.breakfast
                in 11..15 -> MealSlot.lunch
                in 16..20 -> MealSlot.dinner
                else -> MealSlot.bedtime
            },
        )
    }
    return ParsedDraft(name, strengthToken ?: entry?.strengths?.singleOrNull()?.let(::parseStrength), entry?.form, doses, duration)
}

private fun parseStrength(raw: String): Pair<String, String>? {
    val match = Regex("(\\d+(?:\\.\\d+)?)\\s*(mcg|mg|g|ml|iu|%)", RegexOption.IGNORE_CASE).find(raw) ?: return null
    return match.groupValues[1] to match.groupValues[2].let { if (it.equals("iu", true)) "IU" else it.lowercase() }
}

private fun initialMode(schedule: DoseSchedule?): RhythmMode = when {
    schedule == null -> RhythmMode.EVERY_DAY
    schedule.kind == ScheduleKind.asNeeded -> RhythmMode.AS_NEEDED
    schedule.dayInterval > 2 -> RhythmMode.INTERVAL
    schedule.dayInterval == 2 -> RhythmMode.EVERY_OTHER
    schedule.cycleActiveDays == 1 && schedule.cyclePauseDays == 1 -> RhythmMode.EVERY_OTHER
    schedule.cycleActiveDays != null -> RhythmMode.CYCLIC
    schedule.weekdays.isNotEmpty() -> RhythmMode.SPECIFIC
    else -> RhythmMode.EVERY_DAY
}

/** Rebuilds the editable dose list, preserving each dose's amount and relation. */
private fun initialDoses(schedule: DoseSchedule?, presets: DoseTimePresets): List<FormDose> {
    if (schedule == null) return listOf(FormDose(time = presets.morning))

    if (schedule.kind == ScheduleKind.interval) {
        // Materialise an hourly-interval schedule into concrete times so
        // editing one preserves when it actually fires.
        val drafts = buildList {
            if (schedule.intervalHours <= 0) return@buildList
            var cursor = schedule.intervalStart.totalMinutes
            while (cursor <= schedule.intervalEnd.totalMinutes && size < 12) {
                add(FormDose(time = TimeOfDay(cursor / 60, cursor % 60), amount = schedule.amountPerDose))
                cursor += schedule.intervalHours * 60
            }
        }
        return drafts.ifEmpty { listOf(FormDose(time = presets.morning)) }
    }

    val drafts = schedule.resolvedDoses.map { spec ->
        val anchor = spec.anchor
        FormDose(
            time = spec.time,
            amount = spec.amount,
            relation = when (anchor?.relation) {
                MealRelation.before -> FormMealRelation.BEFORE
                MealRelation.with -> FormMealRelation.WITH
                MealRelation.after -> FormMealRelation.AFTER
                null -> FormMealRelation.FIXED
            },
            mealSlot = anchor?.slot ?: MealSlot.breakfast,
            offsetMinutes = anchor?.offsetMinutes?.coerceAtLeast(0) ?: 30,
        )
    }
    return drafts.ifEmpty { listOf(FormDose(time = presets.morning)) }
}

private fun buildSchedule(
    mode: RhythmMode, weekdays: Set<Int>, on: Int, off: Int, intervalDays: Int,
    doses: List<FormDose>, amount: Double, ongoing: Boolean, duration: Int, start: Long,
    mealTimes: MealTimes,
): DoseSchedule {
    val end = if (ongoing) null else DoseEngine.addDays(start, duration)
    val cycle = when (mode) { RhythmMode.CYCLIC -> on to off; else -> null }
    val cadence = when (mode) { RhythmMode.EVERY_OTHER -> 2; RhythmMode.INTERVAL -> intervalDays.coerceAtLeast(2); else -> 1 }
    val days = if (mode == RhythmMode.SPECIFIC) weekdays else emptySet()

    val base = DoseSchedule(
        weekdays = days, dayInterval = cadence,
        cycleActiveDays = cycle?.first, cyclePauseDays = cycle?.second,
        startDate = start, endDate = end, amountPerDose = amount,
    )
    if (mode == RhythmMode.AS_NEEDED) {
        return base.copy(kind = ScheduleKind.asNeeded, doses = emptyList(), endDate = null)
    }

    // Two doses resolving to the same minute would collide on one occurrence
    // key, so keep the first of each.
    val seen = mutableSetOf<Int>()
    val specs = doses.mapNotNull { draft ->
        val time = draft.firingTime(mealTimes)
        if (!seen.add(time.totalMinutes)) return@mapNotNull null
        DoseSpec(amount = draft.amount.coerceAtLeast(0.25), time = time, anchor = draft.anchor)
    }
    return base.copy(kind = ScheduleKind.fixedTimes).withDoses(specs, mealTimes)
}
