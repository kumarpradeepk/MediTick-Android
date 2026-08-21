@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kabi.pillpal.meditick.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.content.Context
import androidx.annotation.StringRes
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.formatMediumDate
import com.kabi.pillpal.meditick.weekdayInitial
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.data.CatalogEntry
import com.kabi.pillpal.meditick.data.DoseTimePresets
import com.kabi.pillpal.meditick.data.DrugSuggestion
import com.kabi.pillpal.meditick.data.DrugSuggestions
import com.kabi.pillpal.meditick.data.MedicationCatalog
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.UUID

private enum class RhythmMode(@StringRes val title: Int, @StringRes val subtitle: Int) {
    EVERY_DAY(R.string.rhythm_every_day, R.string.rhythm_every_day_sub),
    SPECIFIC(R.string.rhythm_specific, R.string.rhythm_specific_sub),
    EVERY_OTHER(R.string.rhythm_every_other, R.string.rhythm_every_other_sub),
    INTERVAL(R.string.rhythm_interval, R.string.rhythm_interval_sub),
    CYCLIC(R.string.rhythm_cyclic, R.string.rhythm_cyclic_sub),
    AS_NEEDED(R.string.rhythm_as_needed, R.string.rhythm_as_needed_sub),
}

internal enum class FormMealRelation(@StringRes val title: Int) {
    FIXED(R.string.dose_relation_fixed), BEFORE(R.string.dose_relation_before),
    WITH(R.string.dose_relation_with), AFTER(R.string.dose_relation_after),
}

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

    fun relationLabel(context: Context): String =
        anchor?.label(context) ?: context.getString(R.string.anchor_fixed_time)
}

/**
 * Weekday chips in the locale's own initials, Sunday-first to match the
 * `Calendar.DAY_OF_WEEK` values the schedule stores. 2024-01-07 was a Sunday.
 */
@Composable
private fun sundayFirstInitials(): List<String> {
    val sunday = remember {
        Calendar.getInstance().apply { clear(); set(2024, Calendar.JANUARY, 7, 12, 0, 0) }.timeInMillis
    }
    return remember(java.util.Locale.getDefault()) { (0..6).map { weekdayInitial(sunday + it * 86_400_000L) } }
}

@Composable
fun MedicationFormScreen(
    repository: AppRepository, editingId: String?, prescriptionId: String?,
    onClose: () -> Unit, onSaved: () -> Unit,
    isPro: Boolean = true, aiScanAccountID: String = "android-preview-account",
    onShowPaywall: () -> Unit = {}, startWithScan: Boolean = false,
) {
    val existing = repository.medication(editingId)
    val context = LocalContext.current
    val haptics = rememberHaptics()
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
    var saved by remember { mutableStateOf<Medication?>(null) }
    var showScan by remember { mutableStateOf(false) }
    /** True once Instant Scan filled the basics — drives the AI-filled tags. */
    var prefilledFromScan by remember { mutableStateOf(false) }

    // Entering straight from a Home / Add New "Instant Scan" tap.
    LaunchedEffect(startWithScan) {
        if (!startWithScan) return@LaunchedEffect
        if (ScanQuota.canStart(context, isPro)) showScan = true else onShowPaywall()
    }

    /** Clears the whole draft for "Add another" from the success screen. */
    fun resetForAnother() {
        saved = null; step = 0; describe = ""; name = ""; strength = ""; strengthUnit = "mg"
        form = MedicationForm.tablet; instructions = ""; trackStock = false; prefilledFromScan = false
        stock = prettyNumber(30.0); alertAt = prettyNumber(7.0)
        mode = RhythmMode.EVERY_DAY; weekdays = emptySet(); cycleOn = 21; cycleOff = 7; dayInterval = 2
        doses = listOf(FormDose(time = presets.morning)); amount = prettyNumber(1.0)
        ongoing = true; startDate = startOfToday(); durationDays = 14
        // The prescription being added to is deliberately kept.
    }

    val suggestions = remember(name) { catalog.search(name) }
    if (showScan) {
        // Full-screen scanner; a picked match prefills the basics and jumps
        // past the describe step, exactly like iOS.
        ScanToAddScreen(
            isPro = isPro,
            accountID = aiScanAccountID,
            onClose = { showScan = false; if (startWithScan && name.isBlank()) onClose() },
            onMatch = { candidate ->
                name = candidate.name
                candidate.strengthText?.let(::parseStrength)?.let { strength = it.first; strengthUnit = it.second }
                candidate.form?.let { form = it }
                candidate.note?.takeIf { instructions.isBlank() }?.let { instructions = it }
                ScanQuota.consume(context, isPro)
                prefilledFromScan = true
                showScan = false
                step = 1
            },
        )
        return
    }
    ScreenBackground {
        saved?.let { done ->
            SavedCelebration(
                medication = done, canAddAnother = isPro,
                onDone = {
                    if (done.addedByScan) ToastCenter.say(context.getString(R.string.scan_added_toast))
                    onSaved()
                },
                onAddAnother = ::resetForAnother,
                onAddMorePro = onShowPaywall,
            )
            return@ScreenBackground
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenTopBar(
                title = stringResource(if (existing == null) R.string.form_add_title else R.string.form_edit_title),
                subtitle = stringResource(R.string.form_step, step + 1),
                leadingIcon = Icons.Default.Close, leadingDescription = stringResource(R.string.action_close), onLeading = onClose,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            // The step bar glides instead of jumping.
            val stepProgress by animateFloatAsState((step + 1) / 3f, spring(dampingRatio = 0.9f, stiffness = 160f), label = "stepBar")
            LinearProgressIndicator(progress = { stepProgress }, Modifier.fillMaxWidth().height(2.dp), color = DS.colors.mint, trackColor = DS.colors.line)
            // Steps slide in from the side they came from.
            AnimatedContent(
                targetState = step, modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(200))) togetherWith
                        fadeOut(tween(140))
                },
                label = "formStep",
            ) { active ->
                when (active) {
                    0 -> DescribeStep(describe, { describe = it }, catalog, presets,
                        scanCaption = when {
                            isPro -> stringResource(R.string.scan_card_caption_quota, ScanQuota.aiRemaining(context))
                            ScanQuota.remainingFree(context) > 0 -> stringResource(R.string.scan_card_caption_free, ScanQuota.remainingFree(context))
                            else -> stringResource(R.string.scan_card_caption_locked)
                        },
                        onScan = {
                            // Free users get a few real scans before the gate —
                            // the feature is experienced, not just advertised.
                            if (ScanQuota.canStart(context, isPro)) showScan = true
                            else { haptics.warning(); onShowPaywall() }
                        },
                        onSuggestion = { suggestion ->
                            haptics.tick()
                            name = suggestion.name
                            suggestion.strengthText?.let(::parseStrength)?.let { strength = it.first; strengthUnit = it.second }
                            suggestion.form?.let { form = it }
                            step = 1
                        },
                        onParsed = { parsed ->
                        name = parsed.name; parsed.strength?.let { strength = it.first; strengthUnit = it.second }
                        parsed.form?.let { form = it }; doses = parsed.doses
                        parsed.durationDays?.let { durationDays = it; ongoing = false }
                        step = 1
                    })
                    1 -> BasicsStep(prefilledFromScan, name, { name = it }, suggestions, { entry ->
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0 && !(existing != null && step == 1)) {
                    RoundIconButton(Icons.Default.ArrowBack, stringResource(R.string.action_back), onClick = { step-- }, modifier = Modifier.size(52.dp))
                }
                PrimaryButton(
                    if (step == 2) stringResource(if (existing == null) R.string.form_add_to_routine else R.string.form_save_changes)
                    else stringResource(R.string.action_continue),
                    onClick = {
                        if (step == 0) {
                            smartParse(context, describe, catalog, presets)?.let { parsed ->
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
                            if (duplicate != null) { haptics.warning(); duplicateName = duplicate.name; return@PrimaryButton }
                            val schedule = buildSchedule(mode, weekdays, cycleOn, cycleOff, dayInterval, doses,
                                amount.toDoubleOrNull() ?: 1.0, ongoing, durationDays, startDate, mealTimes)
                            val medication = (existing ?: Medication()).copy(
                                name = name.trim(), form = form, strengthValue = parsedStrength, strengthUnit = strengthUnit,
                                colorName = existing?.colorName ?: PillColor.entries[(repository.medications.size) % PillColor.entries.size].name,
                                schedule = schedule, prescriptionID = associationId,
                                instructions = instructions.trim(), inventoryEnabled = trackStock,
                                stock = stock.toDoubleOrNull() ?: 30.0, refillReminderThreshold = alertAt.toDoubleOrNull() ?: 7.0,
                                addedByScan = existing?.addedByScan ?: prefilledFromScan,
                            )
                            haptics.success()
                            if (existing == null) { repository.addMedication(medication); saved = medication }
                            else { repository.updateMedication(medication); onSaved() }
                        }
                    }, modifier = Modifier.weight(1f), enabled = when (step) { 0 -> describe.trim().length > 2; 1 -> name.isNotBlank(); else -> mode == RhythmMode.AS_NEEDED || doses.isNotEmpty() },
                    leading = if (step == 2) Icons.Default.Check else Icons.Default.ArrowForward,
                )
            }
        }
    }
    duplicateName?.let { duplicate ->
        InfoSheet(
            stringResource(R.string.form_duplicate_title),
            stringResource(R.string.form_duplicate_body, duplicate),
            stringResource(R.string.action_ok), onDismiss = { duplicateName = null },
            icon = Icons.Default.ErrorOutline, tint = DS.colors.amber,
        )
    }
}

/**
 * The post-save celebration, mirroring the iOS success screen: confetti, a
 * springing gradient check tile, the medication's name, and the next move —
 * done, add another, or the Pro gate when the free tier is already full.
 */
@Composable
private fun SavedCelebration(
    medication: Medication, canAddAnother: Boolean,
    onDone: () -> Unit, onAddAnother: () -> Unit, onAddMorePro: () -> Unit,
) {
    val c = DS.colors
    val context = LocalContext.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ConfettiBurst()
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                shown,
                enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 300f), initialScale = 0.4f) + fadeIn(tween(220)),
            ) {
                Box(
                    Modifier.size(92.dp)
                        .shadow(20.dp, RoundedCornerShape(32.dp), ambientColor = c.glow.copy(.5f), spotColor = c.glow.copy(.5f))
                        .clip(RoundedCornerShape(32.dp)).background(c.gradient),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = c.onMint, modifier = Modifier.size(44.dp)) }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(
                    if (medication.prescriptionID != null) R.string.form_success_added_rx else R.string.form_success_scheduled,
                    medication.name,
                ),
                style = MaterialTheme.typography.headlineMedium, color = c.ink, textAlign = TextAlign.Center,
                modifier = Modifier.appearFluidly(1),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                medication.schedule.summary(context),
                color = c.ink2, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.appearFluidly(2),
            )
            Spacer(Modifier.height(30.dp))
            PrimaryButton(
                stringResource(if (medication.prescriptionID != null) R.string.form_success_view_rx else R.string.form_success_done),
                onDone, Modifier.fillMaxWidth().appearFluidly(3),
            )
            Spacer(Modifier.height(10.dp))
            if (canAddAnother) {
                GhostButton(stringResource(R.string.form_success_add_another), onAddAnother, Modifier.fillMaxWidth().appearFluidly(4))
            } else {
                // The free tier caps at one active medication — the success
                // screen must not offer a way around the gate.
                GhostButton(stringResource(R.string.form_success_add_more_pro), onAddMorePro, Modifier.fillMaxWidth().appearFluidly(4))
            }
        }
    }
}

private data class ParsedDraft(
    val name: String, val strength: Pair<String, String>?, val form: MedicationForm?,
    val doses: List<FormDose>, val durationDays: Int? = null,
)

@Composable
private fun DescribeStep(text: String, onText: (String) -> Unit, catalog: MedicationCatalog, presets: DoseTimePresets, scanCaption: String, onScan: () -> Unit, onSuggestion: (DrugSuggestion) -> Unit, onParsed: (ParsedDraft) -> Unit) {
    val context = LocalContext.current
    val parsed = remember(text, presets, context) { smartParse(context, text, catalog, presets) }

    // Search-as-you-type: the catalog answers instantly, RxTerms broadens
    // the list after a debounce when the network allows.
    val nameQuery = remember(text) {
        text.trim().split(Regex("""\s+""")).firstOrNull()?.takeIf { token ->
            token.length >= 2 && token.all { it.isLetter() }
        }.orEmpty()
    }
    val localSuggestions = remember(nameQuery) {
        if (nameQuery.isEmpty()) emptyList() else DrugSuggestions.local(catalog, nameQuery)
    }
    var indexSuggestions by remember { mutableStateOf(listOf<DrugSuggestion>()) }
    var onlineSuggestions by remember { mutableStateOf(listOf<DrugSuggestion>()) }
    LaunchedEffect(nameQuery) {
        indexSuggestions = emptyList()
        onlineSuggestions = emptyList()
        if (nameQuery.length < 3) return@LaunchedEffect
        val seen = localSuggestions.map { it.name.lowercase() }.toMutableSet()
        // Offline breadth from the bundled 19k-name index, off the UI thread.
        indexSuggestions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            DrugSuggestions.fromIndex(context, nameQuery, seen)
        }
        indexSuggestions.forEach { seen.add(it.name.lowercase()) }
        // Then the network, debounced — purely additive when it answers.
        kotlinx.coroutines.delay(350)
        onlineSuggestions = DrugSuggestions.online(nameQuery).filter { it.name.lowercase() !in seen }
    }
    val suggestions = (localSuggestions + indexSuggestions + onlineSuggestions).take(6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp)) {
        item {
            SectionLabel(stringResource(R.string.describe_label))
            Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.describe_question), style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink)
            Spacer(Modifier.height(7.dp)); Text(stringResource(R.string.describe_hint), color = DS.colors.ink2)
            Spacer(Modifier.height(22.dp))
            MediTickTextField(text, onText, Modifier.fillMaxWidth(), minLines = 4, textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                placeholder = stringResource(R.string.describe_placeholder))
            AnimatedVisibility(
                suggestions.isNotEmpty(),
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                        suggestions.forEachIndexed { index, suggestion ->
                            if (index > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { onSuggestion(suggestion) }
                                    .padding(horizontal = 15.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconTile(formIcon(suggestion.form ?: MedicationForm.tablet), DS.colors.mint, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(suggestion.name, color = DS.colors.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        listOfNotNull(
                                            suggestion.strengthText,
                                            suggestion.form?.title(context),
                                            suggestion.detail,
                                        ).joinToString(" · "),
                                        color = DS.colors.ink3, fontSize = 12.sp,
                                    )
                                }
                                Icon(Icons.Default.AddCircle, null, tint = DS.colors.mint, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    R.string.describe_chip_morning, R.string.describe_chip_twice, R.string.describe_chip_before,
                    R.string.describe_chip_after, R.string.describe_chip_9pm, R.string.describe_chip_500mg,
                ).map { stringResource(it) }.forEach { chip ->
                    SelectChip(chip, false, { onText((text.trim() + " " + chip).trim()) })
                }
            }
            Spacer(Modifier.height(14.dp))
            // Instant Scan — point the camera at the label instead of typing.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(DS.colors.line))
                Text(
                    stringResource(R.string.scan_or_skip_typing), color = DS.colors.ink3,
                    fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp,
                )
                Box(Modifier.weight(1f).height(1.dp).background(DS.colors.line))
            }
            Spacer(Modifier.height(14.dp))
            InstantScanCard(
                title = stringResource(R.string.scan_instant_title),
                subtitle = scanCaption,
                onClick = onScan,
            )
            Spacer(Modifier.height(16.dp)); SectionLabel(stringResource(R.string.describe_examples))
            Spacer(Modifier.height(8.dp))
            listOf(
                R.string.describe_example_1, R.string.describe_example_2,
                R.string.describe_example_3, R.string.describe_example_4,
            ).map { stringResource(it) }.forEach { example ->
                // Quote-card examples, like iOS "Examples you can type".
                GlassCard(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp), radius = 16.dp,
                    onClick = { onText(example) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatQuote, null, tint = DS.colors.ink3, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(example, color = DS.colors.ink2, fontSize = 13.sp)
                    }
                }
            }
            AnimatedVisibility(
                parsed != null,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
            ) {
                parsed?.let {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        GradientCard(Modifier.fillMaxWidth(), onClick = { onParsed(it) }) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Default.AutoAwesome, DS.colors.mint, 46.dp); Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)) {
                                    SectionLabel(stringResource(R.string.describe_understood))
                                    Text(listOfNotNull(it.name, it.strength?.let { s -> context.getString(R.string.amount_with_unit, s.first, s.second) }).joinToString(" · "), color = DS.colors.ink, fontWeight = FontWeight.Bold)
                                    Text(it.doses.joinToString { d -> d.time.label(context) }, color = DS.colors.ink3, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = DS.colors.mint)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicsStep(
    prefilled: Boolean,
    name: String, onName: (String) -> Unit, suggestions: List<CatalogEntry>, onSuggestion: (CatalogEntry) -> Unit,
    strength: String, onStrength: (String) -> Unit, unit: String, onUnit: (String) -> Unit,
    form: MedicationForm, onForm: (MedicationForm) -> Unit, instructions: String, onInstructions: (String) -> Unit,
    track: Boolean, onTrack: (Boolean) -> Unit, stock: String, onStock: (String) -> Unit, alert: String, onAlert: (String) -> Unit,
    prescriptions: List<Prescription>, associationId: String?, onAssociation: (String?) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionLabel(stringResource(R.string.basics_label)); Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.basics_headline), style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink) }
        if (prefilled) item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(DS.colors.gradStart.copy(alpha = .10f), DS.colors.cyan.copy(alpha = .10f))))
                    .border(1.dp, DS.colors.mint.copy(alpha = .25f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = DS.colors.mint, modifier = Modifier.size(13.dp))
                Text(
                    stringResource(R.string.scan_prefilled_banner), color = DS.colors.mint,
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item {
            AIFilledField(prefilled && name.isNotBlank(), stringResource(R.string.scan_ai_filled)) {
                MediTickTextField(name, onName, placeholder = stringResource(R.string.basics_field_name), modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        item {
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text(prescriptions.firstOrNull { it.id == associationId }?.name ?: stringResource(R.string.basics_no_prescription), modifier = Modifier.weight(1f)); Icon(Icons.Default.ExpandMore, null)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.basics_no_prescription)) }, { onAssociation(null); menu = false })
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
                AIFilledField(prefilled && strength.isNotBlank(), stringResource(R.string.scan_ai_filled_short), Modifier.weight(1f)) {
                    MediTickTextField(strength, { onStrength(it.filter { ch -> ch.isDigit() || ch == '.' }) }, placeholder = stringResource(R.string.basics_field_strength), modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ menu = true }, Modifier.height(56.dp)) { Text(unit); Icon(Icons.Default.ExpandMore, null) }
                    DropdownMenu(menu, { menu = false }) {
                        (StrengthUnit.all + unit).distinctBy { it.lowercase() }.forEach {
                            DropdownMenuItem({ Text(it) }, { onUnit(it); menu = false })
                        }
                    }
                }
            }
        }
        item { SectionLabel(stringResource(R.string.basics_section_form)); FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MedicationForm.pickerOrder.forEach { candidate ->
                Box {
                    SelectChip(candidate.title(context), form == candidate, { onForm(candidate) })
                    // The form the scan chose wears the sparkle, like the design.
                    if (prefilled && form == candidate) Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-7).dp)
                            .size(16.dp).clip(CircleShape).background(DS.colors.gradient),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.AutoAwesome, null, tint = DS.colors.onMint, modifier = Modifier.size(9.dp)) }
                }
            }
        } }
        item {
            AIFilledField(prefilled && instructions.isNotBlank(), stringResource(R.string.scan_ai_filled)) {
                MediTickTextField(instructions, onInstructions, placeholder = stringResource(R.string.basics_field_instructions), modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        }
        item { GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.Inventory2, DS.colors.amber); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.basics_track_supply), color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(stringResource(R.string.basics_track_supply_sub), color = DS.colors.ink3, fontSize = 12.sp) }
                Switch(track, onTrack)
            }
            if (track) { Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MediTickTextField(stock, { onStock(it.filter(Char::isDigit)) }, placeholder = stringResource(R.string.basics_field_stock), modifier = Modifier.weight(1f), singleLine = true)
                MediTickTextField(alert, { onAlert(it.filter(Char::isDigit)) }, placeholder = stringResource(R.string.basics_field_alert), modifier = Modifier.weight(1f), singleLine = true)
            } }
        } }
    }
}

/**
 * Wraps a field the scan populated: an "AI FILLED" tag riding the top edge and
 * a mint outline that fades out, so the eye lands on what to verify.
 */
@Composable
private fun AIFilledField(show: Boolean, badge: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!show) { Box(modifier) { content() }; return }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1600); settled = true }
    val highlight by animateColorAsState(
        if (settled) Color.Transparent else DS.colors.mint.copy(alpha = .45f),
        tween(1200), label = "aiFieldHighlight",
    )
    Box(modifier.border(1.5.dp, highlight, RoundedCornerShape(18.dp))) {
        content()
        AIFilledBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-8).dp))
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
        item { SectionLabel(stringResource(R.string.rhythm_label)); Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.rhythm_headline), style = MaterialTheme.typography.headlineLarge, color = DS.colors.ink) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            RhythmMode.entries.forEach { SelectChip(stringResource(it.title), mode == it, { onMode(it) }) }
        } }
        if (mode == RhythmMode.SPECIFIC) item {
            SectionLabel(stringResource(R.string.rhythm_section_days)); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                sundayFirstInitials().forEachIndexed { index, title ->
                    SelectChip(title, index + 1 in weekdays, { onWeekdays(if (index + 1 in weekdays) weekdays - (index + 1) else weekdays + (index + 1)) })
                }
            }
        }
        if (mode == RhythmMode.CYCLIC) item { NumberPair(stringResource(R.string.rhythm_days_on), cycleOn, onCycleOn, stringResource(R.string.rhythm_days_off), cycleOff, onCycleOff) }
        if (mode == RhythmMode.INTERVAL) item { MediTickTextField(dayInterval.toString(), { onDayInterval(it.toIntOrNull()?.coerceIn(2, 365) ?: 2) }, placeholder = stringResource(R.string.rhythm_repeat_every), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (mode != RhythmMode.AS_NEEDED) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.rhythm_section_doses))
                    Spacer(Modifier.width(8.dp))
                    Text(pluralStringResource(R.plurals.schedule_times_per_day, doses.size, doses.size), color = DS.colors.ink3, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton({ showAddDose = true }) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.rhythm_add_dose)) }
                }
                doses.forEach { dose ->
                    Spacer(Modifier.height(8.dp))
                    GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, onClick = { editing = dose }, contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.Schedule, DS.colors.mint); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(dose.firingTime(mealTimes).label(context), color = DS.colors.ink, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.rhythm_dose_summary, prettyNumber(dose.amount), dose.relationLabel(context)), color = DS.colors.ink3, fontSize = 12.sp)
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
                        SectionLabel(stringResource(R.string.rhythm_meal_linked))
                        Spacer(Modifier.weight(1f))
                        TextButton({ showMealTimes = true }) { Text(stringResource(R.string.action_edit)) }
                    }
                    Text(stringResource(R.string.rhythm_meal_auto), color = DS.colors.ink3, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        MealSlot.entries.forEach { slot ->
                            StatusPill(stringResource(R.string.rhythm_meal_pill, slot.title(context), mealTimes.time(slot).label(context)), DS.colors.ink2)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.rhythm_meal_synced), color = DS.colors.mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (mode == RhythmMode.AS_NEEDED) item {
            MediTickTextField(amount, { onAmount(it.filter { ch -> ch.isDigit() || ch == '.' }) }, placeholder = stringResource(R.string.rhythm_amount_per_dose), modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (mode != RhythmMode.AS_NEEDED) item {
            GhostButton(
                stringResource(R.string.rhythm_start_date, formatMediumDate(startDate)),
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = startDate }
                    android.app.DatePickerDialog(context, { _, y, m, d -> onStartDate(Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis) }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                Modifier.fillMaxWidth(), leading = Icons.Default.CalendarMonth,
            )
        }
        if (mode != RhythmMode.AS_NEEDED) item { GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.rhythm_ongoing), color = DS.colors.ink, fontWeight = FontWeight.Bold); Text(if (ongoing) stringResource(R.string.rhythm_no_end_date) else pluralStringResource(R.plurals.rhythm_course_days, duration, duration), color = DS.colors.ink3, fontSize = 12.sp) }
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
    val labels = dayPartLabels()
    val slotTints = listOf(DS.colors.amber, DS.colors.cyan, DS.colors.violet, DS.colors.mint)
    val slotIcons = listOf(Icons.Default.WbSunny, Icons.Default.LightMode, Icons.Default.NightsStay, Icons.Default.Bedtime)
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = DS.colors.bg3,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(stringResource(R.string.rhythm_add_dose), style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.dose_add_hint), color = DS.colors.ink3, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 3.dp)) {
                presets.all().forEachIndexed { index, preset ->
                    if (index > 0) RowDivider()
                    SettingsRow(slotIcons[index], slotTints[index], labels[index], onClick = { onAdd(FormDose(time = preset)) }) {
                        Text(preset.label(context), color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GhostButton(
                stringResource(R.string.dose_custom_time),
                onClick = {
                    TimePickerDialog(context, { _, h, m -> onAdd(FormDose(time = TimeOfDay(h, m))) },
                        presets.morning.hour, presets.morning.minute, false).show()
                },
                Modifier.fillMaxWidth(), leading = Icons.Default.Schedule,
            )
        }
    }
}

/** The four dose-time presets, in order. Shared with Settings. */
@Composable
internal fun dayPartLabels(): List<String> = listOf(
    stringResource(R.string.daypart_morning), stringResource(R.string.daypart_midday),
    stringResource(R.string.daypart_evening), stringResource(R.string.daypart_bedtime),
)

/** Edits one dose: how much, and whether it is a fixed time or tied to a meal. */
@Composable
private fun DoseEditorDialog(
    draft: FormDose, mealTimes: MealTimes, onDismiss: () -> Unit,
    onSave: (FormDose) -> Unit, onRemove: () -> Unit, canRemove: Boolean,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var working by remember(draft.id) { mutableStateOf(draft) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = DS.colors.bg3,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 30.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.dose_edit_title), style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            Text(working.firingTime(mealTimes).label(context), color = DS.colors.mint, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.dose_section_amount))
            Spacer(Modifier.height(6.dp))
            GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Half-dose steps: splitting a tablet is routine.
                    IconButton({ haptics.tick(); working = working.copy(amount = (working.amount - 0.5).coerceAtLeast(0.5)) }) {
                        Icon(Icons.Default.Remove, stringResource(R.string.dose_decrease), tint = DS.colors.ink)
                    }
                    Text(prettyNumber(working.amount), color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton({ haptics.tick(); working = working.copy(amount = (working.amount + 0.5).coerceAtMost(20.0)) }) {
                        Icon(Icons.Default.Add, stringResource(R.string.dose_increase), tint = DS.colors.ink)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.dose_section_timing))
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FormMealRelation.entries.forEach { option ->
                    SelectChip(stringResource(option.title), working.relation == option, { working = working.copy(relation = option) })
                }
            }

            Spacer(Modifier.height(12.dp))
            if (working.relation == FormMealRelation.FIXED) {
                GhostButton(
                    stringResource(R.string.dose_reminder_time, working.time.label(context)),
                    onClick = {
                        TimePickerDialog(context, { _, h, m -> working = working.copy(time = TimeOfDay(h, m)) },
                            working.time.hour, working.time.minute, false).show()
                    },
                    Modifier.fillMaxWidth(), leading = Icons.Default.Schedule,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    MealSlot.entries.forEach { slot ->
                        SelectChip(slot.title(context), working.mealSlot == slot, { working = working.copy(mealSlot = slot) })
                    }
                }
                if (working.relation != FormMealRelation.WITH) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pluralStringResource(
                            if (working.relation == FormMealRelation.BEFORE) R.plurals.dose_offset_before else R.plurals.dose_offset_after,
                            working.offsetMinutes, working.offsetMinutes,
                        ),
                        color = DS.colors.ink2, fontSize = 12.sp,
                    )
                    Slider(
                        working.offsetMinutes.toFloat(),
                        { working = working.copy(offsetMinutes = it.toInt()) },
                        valueRange = 0f..180f, steps = 35,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.dose_meal_note,
                        working.mealSlot.title(context), mealTimes.time(working.mealSlot).label(context),
                    ),
                    color = DS.colors.ink3, fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.action_save), { onSave(working) }, Modifier.fillMaxWidth(), leading = Icons.Default.Check)
            if (canRemove) {
                Spacer(Modifier.height(10.dp))
                DangerButton(stringResource(R.string.dose_remove), onRemove, Modifier.fillMaxWidth(), leading = Icons.Default.RemoveCircle)
            }
        }
    }
}

@Composable private fun NumberPair(a: String, av: Int, setA: (Int) -> Unit, b: String, bv: Int, setB: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        MediTickTextField(av.toString(), { setA(it.toIntOrNull()?.coerceIn(1, 90) ?: 1) }, placeholder = a, modifier = Modifier.weight(1f), singleLine = true)
        MediTickTextField(bv.toString(), { setB(it.toIntOrNull()?.coerceIn(1, 90) ?: 1) }, placeholder = b, modifier = Modifier.weight(1f), singleLine = true)
    }
}

// The parser reads free text the *user* types, so its trigger words have to
// exist in the user's language — matching only English made the whole feature
// a no-op in the other fifteen locales. Each resource is a comma-separated
// token list; the English tokens are merged back in so an English label
// ("twice daily") still parses in a non-English UI.
private fun lexicon(context: Context, @StringRes res: Int, english: List<String>): List<String> =
    (context.getString(res).lowercase().split(',').map(String::trim).filter(String::isNotEmpty) + english)
        .distinct()

private fun CharSequence.containsAny(needles: List<String>) = needles.any { it.isNotEmpty() && contains(it) }

/** Arabic-Indic and Persian digits, so "٨ صباحًا" parses like "8 am". */
private fun normalizeDigits(text: String): String = buildString(text.length) {
    for (ch in text) {
        val v = Character.digit(ch, 10)
        append(if (v in 0..9 && ch.code > 127) '0' + v else ch)
    }
}

/**
 * A number immediately before a day/week/month word: "for 7 days",
 * "pendant 2 semaines", "7日間". Every locale the app ships in puts the count
 * first, and matching the other order would read "每周2次" (twice a week) as a
 * two-week course.
 */
private fun durationDays(context: Context, text: String): Int? {
    val groups = listOf(
        lexicon(context, R.string.parse_unit_day, listOf("day", "days")) to 1,
        lexicon(context, R.string.parse_unit_week, listOf("week", "weeks")) to 7,
        lexicon(context, R.string.parse_unit_month, listOf("month", "months")) to 30,
    )
    for ((units, multiplier) in groups) {
        for (unit in units) {
            if (unit.isEmpty()) continue
            val value = Regex("(\\d{1,3})\\s*" + Regex.escape(unit)).find(text)
                ?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (value > 0) return (value * multiplier).coerceIn(1, 365)
        }
    }
    return null
}

private fun smartParse(
    context: Context, text: String, catalog: MedicationCatalog, presets: DoseTimePresets,
): ParsedDraft? {
    val clean = normalizeDigits(text.trim()); if (clean.length < 3) return null
    val words = clean.split(Regex("[\\s,]+"))
    val stop = lexicon(
        context, R.string.parse_stopwords,
        listOf("every", "each", "twice", "once", "daily", "take", "with", "before", "after",
               "morning", "night", "evening", "noon", "midday", "bed", "bedtime", "lunch",
               "dinner", "breakfast", "meal", "meals", "the", "and", "at"),
    ).toSet()
    val word = words.firstOrNull { it.length >= 3 && it.all(Char::isLetter) && it.lowercase() !in stop } ?: return null
    val entry = catalog.resolve(word)
    val name = entry?.name ?: word.replaceFirstChar(Char::uppercase)
    val strengthToken = words.firstNotNullOfOrNull(::parseStrength)
    val lower = clean.lowercase()
    val amWords = lexicon(context, R.string.parse_marker_am, listOf("am"))
    val pmWords = lexicon(context, R.string.parse_marker_pm, listOf("pm"))
    // "8pm", "8:30 pm", ja "午後8時" — the marker can lead or trail the number.
    val clockSep = "[:時时點点시]"
    val meridiemTimes = buildList {
        for ((words, isPm) in listOf(amWords to false, pmWords to true)) {
            val alt = words.filter(String::isNotEmpty).joinToString("|") { Regex.escape(it) }
            if (alt.isEmpty()) continue
            val patterns = listOf(
                Regex("(\\d{1,2})(?:$clockSep(\\d{2}))?\\s*(?:$alt)"),
                Regex("(?:$alt)\\s*(\\d{1,2})(?:$clockSep(\\d{2}))?"),
            )
            for (pattern in patterns) for (match in pattern.findAll(lower)) {
                val raw = match.groupValues[1].toIntOrNull() ?: continue
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                if (raw !in 1..12 || minute !in 0..59) continue
                add(TimeOfDay(if (isPm) (raw % 12) + 12 else raw % 12, minute))
            }
        }
    }
    // Only look for bare 24-hour times when no meridiem was found, so
    // "8:30 pm" is not also read as 08:30.
    val twentyFourHour = if (meridiemTimes.isNotEmpty()) emptyList() else
        Regex("([01]?\\d|2[0-3])$clockSep([0-5]\\d)").findAll(lower).mapNotNull { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val minute = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            TimeOfDay(hour, minute)
        }.toList()
    val slots = buildList {
        if (lower.containsAny(lexicon(context, R.string.parse_slot_morning, listOf("morning", "breakfast")))) add(presets.morning)
        if (lower.containsAny(lexicon(context, R.string.parse_slot_midday, listOf("noon", "midday", "lunch")))) add(presets.midday)
        if (lower.containsAny(lexicon(context, R.string.parse_slot_evening, listOf("evening", "night", "dinner")))) add(presets.evening)
        if (lower.containsAny(lexicon(context, R.string.parse_slot_bedtime, listOf("bedtime", "bed")))) add(presets.bedtime)
    }
    val twiceWords = lexicon(context, R.string.parse_twice, listOf("twice", "2x", "two times"))
    val times = (meridiemTimes + twentyFourHour + slots).distinct()
        .ifEmpty { if (lower.containsAny(twiceWords)) listOf(presets.morning, presets.evening) else listOf(presets.morning) }
    val relation = when {
        lower.containsAny(lexicon(context, R.string.parse_meal_before,
            listOf("before meal", "before food", "before breakfast", "before lunch", "before dinner"))) -> FormMealRelation.BEFORE
        lower.containsAny(lexicon(context, R.string.parse_meal_after,
            listOf("after meal", "after food", "after breakfast", "after lunch", "after dinner"))) -> FormMealRelation.AFTER
        lower.containsAny(lexicon(context, R.string.parse_meal_with,
            listOf("with meal", "with food"))) -> FormMealRelation.WITH
        else -> FormMealRelation.FIXED
    }
    val duration = durationDays(context, lower)
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

internal fun parseStrength(raw: String): Pair<String, String>? {
    val concentration = Regex(
        """^(\d+(?:\.\d+)?)\s*(mcg|mg|g|iu)\s*/\s*(\d+(?:\.\d+)?)\s*(ml|l)$""",
        RegexOption.IGNORE_CASE,
    ).find(raw.trim())
    if (concentration != null) {
        val numerator = concentration.groupValues[2].let { if (it.equals("iu", true)) "IU" else it.lowercase() }
        val volume = concentration.groupValues[4].let { if (it.equals("ml", true)) "mL" else "L" }
        return concentration.groupValues[1] to "$numerator/${concentration.groupValues[3]} $volume"
    }
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
