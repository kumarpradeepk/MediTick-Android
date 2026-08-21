package com.kabi.pillpal.meditick.model

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.formatTime
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class TimeOfDay(val hour: Int = 8, val minute: Int = 0) : Comparable<TimeOfDay> {
    val totalMinutes: Int get() = hour * 60 + minute
    override fun compareTo(other: TimeOfDay) = totalMinutes.compareTo(other.totalMinutes)
    /** Uses the device's 12h/24h setting and locale — most of the world is not "8:00 AM". */
    fun label(context: Context): String = formatTime(context, hour, minute)
    fun toJson() = JSONObject().put("hour", hour).put("minute", minute)

    companion object {
        fun fromJson(json: JSONObject?) = TimeOfDay(
            json?.optInt("hour", 8) ?: 8,
            json?.optInt("minute", 0) ?: 0,
        )
    }
}

enum class MealSlot { breakfast, lunch, dinner, bedtime }
enum class MealRelation { before, with, after }

/** Standalone meal name, e.g. heading a settings row. */
@StringRes fun MealSlot.titleRes(): Int = when (this) {
    MealSlot.breakfast -> R.string.meal_breakfast
    MealSlot.lunch -> R.string.meal_lunch
    MealSlot.dinner -> R.string.meal_dinner
    MealSlot.bedtime -> R.string.meal_bedtime
}

/** Meal name as it appears inside a sentence, e.g. "With breakfast". */
@StringRes fun MealSlot.inlineRes(): Int = when (this) {
    MealSlot.breakfast -> R.string.meal_breakfast_inline
    MealSlot.lunch -> R.string.meal_lunch_inline
    MealSlot.dinner -> R.string.meal_dinner_inline
    MealSlot.bedtime -> R.string.meal_bedtime_inline
}

fun MealSlot.title(context: Context): String = context.getString(titleRes())

data class MealAnchor(
    val id: String = UUID.randomUUID().toString(),
    val slot: MealSlot = MealSlot.breakfast,
    val relation: MealRelation = MealRelation.with,
    val offsetMinutes: Int = 0,
) {
    val signedOffset: Int get() = when (relation) {
        MealRelation.before -> -offsetMinutes
        MealRelation.with -> 0
        MealRelation.after -> offsetMinutes
    }
    /** "With breakfast" / "30 min before breakfast" — matching the dose rows. */
    fun label(context: Context): String {
        val meal = context.getString(slot.inlineRes())
        if (slot == MealSlot.bedtime && relation == MealRelation.with) {
            return context.getString(R.string.anchor_at_bedtime)
        }
        return when (relation) {
            MealRelation.with -> context.getString(R.string.anchor_with, meal)
            MealRelation.before ->
                if (offsetMinutes > 0) context.getString(R.string.anchor_before_offset, offsetMinutes, meal)
                else context.getString(R.string.anchor_before, meal)
            MealRelation.after ->
                if (offsetMinutes > 0) context.getString(R.string.anchor_after_offset, offsetMinutes, meal)
                else context.getString(R.string.anchor_after, meal)
        }
    }
    fun toJson() = JSONObject()
        .put("id", id).put("slot", slot.name).put("relation", relation.name)
        .put("offsetMinutes", offsetMinutes)

    companion object {
        fun fromJson(json: JSONObject) = MealAnchor(
            id = json.optString("id", UUID.randomUUID().toString()),
            slot = enumValueOr(MealSlot.breakfast, json.optString("slot")),
            relation = enumValueOr(MealRelation.with, json.optString("relation")),
            offsetMinutes = json.optInt("offsetMinutes", 0),
        )
    }
}

data class MealTimes(
    val breakfast: TimeOfDay = TimeOfDay(8, 0),
    val lunch: TimeOfDay = TimeOfDay(13, 0),
    val dinner: TimeOfDay = TimeOfDay(19, 30),
    val bedtime: TimeOfDay = TimeOfDay(22, 30),
) {
    fun time(slot: MealSlot) = when (slot) {
        MealSlot.breakfast -> breakfast
        MealSlot.lunch -> lunch
        MealSlot.dinner -> dinner
        MealSlot.bedtime -> bedtime
    }
    fun withTime(slot: MealSlot, value: TimeOfDay) = when (slot) {
        MealSlot.breakfast -> copy(breakfast = value)
        MealSlot.lunch -> copy(lunch = value)
        MealSlot.dinner -> copy(dinner = value)
        MealSlot.bedtime -> copy(bedtime = value)
    }
    fun toJson() = JSONObject()
        .put("breakfast", breakfast.toJson()).put("lunch", lunch.toJson())
        .put("dinner", dinner.toJson()).put("bedtime", bedtime.toJson())

    companion object {
        fun fromJson(json: JSONObject?) = MealTimes(
            breakfast = TimeOfDay.fromJson(json?.optJSONObject("breakfast")),
            lunch = TimeOfDay.fromJson(json?.optJSONObject("lunch") ?: JSONObject().put("hour", 13)),
            dinner = TimeOfDay.fromJson(json?.optJSONObject("dinner") ?: JSONObject().put("hour", 19).put("minute", 30)),
            bedtime = TimeOfDay.fromJson(json?.optJSONObject("bedtime") ?: JSONObject().put("hour", 22).put("minute", 30)),
        )
    }
}

enum class MedicationForm(@StringRes val titleRes: Int, @PluralsRes val unitRes: Int) {
    pill(R.string.form_pill, R.plurals.unit_pill),
    capsule(R.string.form_capsule, R.plurals.unit_capsule),
    tablet(R.string.form_tablet, R.plurals.unit_tablet),
    liquid(R.string.form_liquid, R.plurals.unit_ml),
    drops(R.string.form_drops, R.plurals.unit_drop),
    injection(R.string.form_injection, R.plurals.unit_shot),
    patch(R.string.form_patch, R.plurals.unit_patch),
    cream(R.string.form_cream, R.plurals.unit_application),
    inhaler(R.string.form_inhaler, R.plurals.unit_puff),
    powder(R.string.form_powder, R.plurals.unit_scoop),
    gummy(R.string.form_gummy, R.plurals.unit_gummy),
    spray(R.string.form_spray, R.plurals.unit_spray),
    other(R.string.form_other, R.plurals.unit_dose);

    fun title(context: Context): String = context.getString(titleRes)

    /**
     * The unit word for [amount], in the plural form the active language needs.
     *
     * Android's plural rules take an Int, so a fractional amount (half a tablet)
     * falls back to the general plural — which is what English "0.5 tablets"
     * already did, and what Arabic and Russian need for non-integers too.
     */
    fun unitName(context: Context, amount: Double): String =
        context.resources.getQuantityString(unitRes, quantityFor(amount))

    private fun quantityFor(amount: Double): Int =
        if (amount.isFinite() && amount == kotlin.math.floor(amount)) amount.toInt() else 2

    companion object {
        /** The order the picker offers; `other` stays last as the catch-all. */
        val pickerOrder: List<MedicationForm> = listOf(
            pill, capsule, tablet, liquid, drops, injection,
            patch, cream, inhaler, powder, gummy, spray, other,
        )
    }
}

/** Strength units offered in the medication form; free text still round-trips. */
object StrengthUnit {
    val all = listOf("mg", "mcg", "g", "mL", "IU", "%")

    fun canonical(raw: String): String =
        all.firstOrNull { it.equals(raw.trim(), ignoreCase = true) } ?: raw.trim()
}

enum class ScheduleKind { fixedTimes, mealBased, interval, asNeeded }

/**
 * One dose inside a day: an amount plus either a wall-clock time or a meal
 * anchor. Each dose carries its own relation, so a medication can be taken at
 * 08:00 fixed and 30 minutes before dinner.
 */
data class DoseSpec(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 1.0,
    /** Used when [anchor] is null; also the last resolved time, for display. */
    val time: TimeOfDay = TimeOfDay(9, 0),
    val anchor: MealAnchor? = null,
) {
    /** The time this dose fires, given the user's meal rhythm. */
    fun firingTime(mealTimes: MealTimes): TimeOfDay {
        val meal = anchor ?: return time
        val base = mealTimes.time(meal.slot).totalMinutes + meal.signedOffset
        val wrapped = ((base % 1440) + 1440) % 1440
        return TimeOfDay(wrapped / 60, wrapped % 60)
    }

    fun summary(context: Context, form: MedicationForm): String {
        val quantity = context.getString(
            R.string.amount_with_unit, prettyNumber(amount), form.unitName(context, amount),
        )
        val timing = anchor?.label(context) ?: context.getString(R.string.anchor_fixed_time)
        return context.getString(R.string.dose_spec_summary, quantity, timing)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("amount", amount).put("time", time.toJson())
        .putNullable("anchor", anchor?.toJson())

    companion object {
        fun fromJson(json: JSONObject) = DoseSpec(
            id = json.optString("id", UUID.randomUUID().toString()),
            amount = json.optDouble("amount", 1.0),
            time = TimeOfDay.fromJson(json.optJSONObject("time")),
            anchor = json.optJSONObject("anchor")?.let(MealAnchor::fromJson),
        )
    }
}

data class DoseSchedule(
    val kind: ScheduleKind = ScheduleKind.mealBased,
    /**
     * The canonical per-dose list. Empty in data written before per-dose
     * editing existed — [resolvedDoses] derives it from the legacy fields.
     */
    val doses: List<DoseSpec> = emptyList(),
    val times: List<TimeOfDay> = listOf(TimeOfDay(9, 0)),
    val mealAnchors: List<MealAnchor> = listOf(MealAnchor()),
    val intervalHours: Int = 6,
    val intervalStart: TimeOfDay = TimeOfDay(8, 0),
    val intervalEnd: TimeOfDay = TimeOfDay(22, 0),
    val weekdays: Set<Int> = emptySet(),
    /** Calendar-day cadence. 1 preserves the legacy every-day behavior. */
    val dayInterval: Int = 1,
    val cycleActiveDays: Int? = null,
    val cyclePauseDays: Int? = null,
    val startDate: Long = startOfToday(),
    val endDate: Long? = null,
    val amountPerDose: Double = 1.0,
) {
    /** The canonical dose list, derived from legacy fields when absent. */
    val resolvedDoses: List<DoseSpec> get() = when {
        doses.isNotEmpty() -> doses.sortedBy { it.time.totalMinutes }
        kind == ScheduleKind.fixedTimes ->
            times.sorted().map { DoseSpec(amount = amountPerDose, time = it) }
        kind == ScheduleKind.mealBased ->
            mealAnchors.map { DoseSpec(amount = amountPerDose, anchor = it) }
        else -> emptyList()
    }

    /**
     * Rebuilds [doses] plus the mirrored legacy fields, so every consumer —
     * engine, widget, export, the iOS app — reads the same schedule.
     */
    fun withDoses(specs: List<DoseSpec>, mealTimes: MealTimes): DoseSchedule {
        val resolved = specs
            .map { if (it.anchor != null) it.copy(time = it.firingTime(mealTimes)) else it }
            .sortedBy { it.time.totalMinutes }
        val anchors = resolved.mapNotNull { it.anchor }
        return copy(
            doses = resolved,
            times = resolved.map { it.time },
            mealAnchors = anchors,
            amountPerDose = resolved.firstOrNull()?.amount ?: amountPerDose,
            kind = when (kind) {
                ScheduleKind.interval, ScheduleKind.asNeeded -> kind
                else -> if (anchors.isEmpty()) ScheduleKind.fixedTimes else ScheduleKind.mealBased
            },
        )
    }

    val dosesPerDay: Int get() = when (kind) {
        ScheduleKind.fixedTimes, ScheduleKind.mealBased -> resolvedDoses.size
        ScheduleKind.interval -> if (intervalHours > 0 && intervalEnd >= intervalStart)
            1 + (intervalEnd.totalMinutes - intervalStart.totalMinutes) / (intervalHours * 60) else 0
        ScheduleKind.asNeeded -> 0
    }

    /** Units consumed on one active day, honouring per-dose amounts. */
    val unitsPerActiveDay: Double get() = when (kind) {
        ScheduleKind.fixedTimes, ScheduleKind.mealBased -> resolvedDoses.sumOf { it.amount }
        ScheduleKind.interval -> dosesPerDay * amountPerDose
        ScheduleKind.asNeeded -> 0.0
    }

    fun dosesPerDayLabel(context: Context): String =
        context.resources.getQuantityString(R.plurals.schedule_times_per_day, dosesPerDay, dosesPerDay)

    fun summary(context: Context): String = when (kind) {
        ScheduleKind.fixedTimes, ScheduleKind.mealBased -> resolvedDoses
            .joinToString(", ") { it.anchor?.label(context) ?: it.time.label(context) }
            .ifEmpty { context.getString(R.string.schedule_no_times_set) }
        ScheduleKind.interval -> context.getString(
            R.string.schedule_interval, intervalHours, intervalStart.label(context), intervalEnd.label(context),
        )
        ScheduleKind.asNeeded -> context.getString(R.string.schedule_as_needed)
    }
    fun frequencySummary(context: Context): String {
        if (kind == ScheduleKind.asNeeded) return context.getString(R.string.schedule_when_needed)
        // Weekday names come from the platform, so they are already correct in
        // every locale and never need translating.
        val shortWeekdays = DateFormatSymbols.getInstance().shortWeekdays
        val day = when {
            dayInterval > 1 ->
                context.resources.getQuantityString(R.plurals.schedule_every_n_days, dayInterval, dayInterval)
            weekdays.isEmpty() || weekdays.size == 7 -> context.getString(R.string.schedule_every_day)
            else -> weekdays.sorted().joinToString(" ") { shortWeekdays[it] }
        }
        val cycle = if ((cycleActiveDays ?: 0) > 0 && (cyclePauseDays ?: 0) > 0)
            context.getString(R.string.schedule_cycle_suffix, cycleActiveDays, cyclePauseDays) else ""
        return day + cycle
    }
    fun toJson() = JSONObject()
        .put("kind", kind.name)
        .put("doses", JSONArray(doses.map { it.toJson() }))
        .put("times", JSONArray(times.map { it.toJson() }))
        .put("mealAnchors", JSONArray(mealAnchors.map { it.toJson() }))
        .put("intervalHours", intervalHours).put("intervalStart", intervalStart.toJson())
        .put("intervalEnd", intervalEnd.toJson()).put("weekdays", JSONArray(weekdays.toList()))
        .put("dayInterval", dayInterval.coerceAtLeast(1))
        .putNullable("cycleActiveDays", cycleActiveDays).putNullable("cyclePauseDays", cyclePauseDays)
        .put("startDate", isoDate(startDate)).putNullable("endDate", endDate?.let(::isoDate))
        .put("amountPerDose", amountPerDose)

    companion object {
        fun fromJson(json: JSONObject?) = if (json == null) DoseSchedule() else DoseSchedule(
            kind = enumValueOr(ScheduleKind.mealBased, json.optString("kind")),
            doses = json.optJSONArray("doses").objects().map(DoseSpec::fromJson),
            times = json.optJSONArray("times").objects().map(TimeOfDay::fromJson).ifEmpty { listOf(TimeOfDay(9, 0)) },
            mealAnchors = json.optJSONArray("mealAnchors").objects().map(MealAnchor::fromJson).ifEmpty { listOf(MealAnchor()) },
            intervalHours = json.optInt("intervalHours", 6),
            intervalStart = TimeOfDay.fromJson(json.optJSONObject("intervalStart")),
            intervalEnd = TimeOfDay.fromJson(json.optJSONObject("intervalEnd") ?: JSONObject().put("hour", 22)),
            weekdays = json.optJSONArray("weekdays").ints().toSet(),
            dayInterval = json.optInt("dayInterval", 1).coerceAtLeast(1),
            cycleActiveDays = json.optNullableInt("cycleActiveDays"),
            cyclePauseDays = json.optNullableInt("cyclePauseDays"),
            startDate = parseDate(json.opt("startDate")) ?: startOfToday(),
            endDate = parseDate(json.opt("endDate")),
            amountPerDose = json.optDouble("amountPerDose", 1.0),
        )
    }
}

enum class PillColor(val hex: Long) {
    coral(0xFFFA736B), tangerine(0xFFFC9947), honey(0xFFF2C443), meadow(0xFF61C77D),
    teal(0xFF38B8B2), sky(0xFF54A6F7), iris(0xFF7A7DF5), orchid(0xFFB578F2),
    rose(0xFFF270B5), slate(0xFF8494AD),
}

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val form: MedicationForm = MedicationForm.tablet,
    val strengthValue: Double? = null,
    val strengthUnit: String = "mg",
    val colorName: String = PillColor.coral.name,
    val schedule: DoseSchedule = DoseSchedule(),
    val prescriptionID: String? = null,
    val instructions: String = "",
    val inventoryEnabled: Boolean = false,
    val stock: Double = 30.0,
    val refillReminderThreshold: Double = 7.0,
    val isArchived: Boolean = false,
    /** Non-null only when the medication was archived by its parent treatment. */
    val archivedByPrescriptionID: String? = null,
    val archivedAt: Long? = null,
    /** Completion is separate from archive so Completed and Archived remain distinct filters. */
    val completedByPrescriptionID: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** True when Instant Scan filled the basics — drives the SCANNED badge. */
    val addedByScan: Boolean = false,
) {
    val strengthLabel: String? get() = strengthValue?.takeIf { it > 0 }?.let { "${prettyNumber(it)} $strengthUnit" }
    fun doseLabel(context: Context): String = context.getString(
        R.string.amount_with_unit,
        prettyNumber(schedule.amountPerDose),
        form.unitName(context, schedule.amountPerDose),
    )
    val daysOfStockRemaining: Int? get() {
        if (!inventoryEnabled) return null
        val daily = schedule.unitsPerActiveDay
        if (daily <= 0) return null
        var adjusted = stock / daily * schedule.dayInterval.coerceAtLeast(1)
        if (schedule.weekdays.isNotEmpty() && schedule.weekdays.size < 7) adjusted *= 7.0 / schedule.weekdays.size
        val on = schedule.cycleActiveDays
        val off = schedule.cyclePauseDays
        if (on != null && off != null && on > 0 && off > 0) adjusted *= (on + off).toDouble() / on
        return adjusted.toInt().coerceAtLeast(0)
    }
    val needsRefill: Boolean get() = inventoryEnabled && stock <= refillReminderThreshold

    /** "30 pills" / "Not tracked" — the Treatments inventory line. */
    fun remainingLabel(context: Context): String =
        if (!inventoryEnabled) context.getString(R.string.inventory_not_tracked)
        else context.getString(R.string.amount_with_unit, prettyNumber(stock), form.unitName(context, stock))
    fun toJson() = JSONObject().put("id", id).put("name", name).put("form", form.name)
        .putNullable("strengthValue", strengthValue).put("strengthUnit", strengthUnit)
        .put("colorName", colorName).put("schedule", schedule.toJson())
        .putNullable("prescriptionID", prescriptionID).put("instructions", instructions)
        .put("inventoryEnabled", inventoryEnabled).put("stock", stock)
        .put("refillReminderThreshold", refillReminderThreshold).put("isArchived", isArchived)
        .putNullable("archivedByPrescriptionID", archivedByPrescriptionID)
        .putNullable("archivedAt", archivedAt?.let(::isoDate))
        .putNullable("completedByPrescriptionID", completedByPrescriptionID)
        .putNullable("completedAt", completedAt?.let(::isoDate))
        .put("createdAt", isoDate(createdAt)).put("addedByScan", addedByScan)

    companion object {
        fun fromJson(json: JSONObject) = Medication(
            id = json.optString("id", UUID.randomUUID().toString()), name = json.optString("name"),
            form = enumValueOr(MedicationForm.tablet, json.optString("form")),
            strengthValue = json.optNullableDouble("strengthValue"), strengthUnit = json.optString("strengthUnit", "mg"),
            colorName = json.optString("colorName", PillColor.coral.name),
            schedule = DoseSchedule.fromJson(json.optJSONObject("schedule")),
            prescriptionID = json.optNullableString("prescriptionID"), instructions = json.optString("instructions"),
            inventoryEnabled = json.optBoolean("inventoryEnabled"), stock = json.optDouble("stock", 30.0),
            refillReminderThreshold = json.optDouble("refillReminderThreshold", 7.0),
            isArchived = json.optBoolean("isArchived"),
            archivedByPrescriptionID = json.optNullableString("archivedByPrescriptionID"),
            archivedAt = parseDate(json.opt("archivedAt")),
            completedByPrescriptionID = json.optNullableString("completedByPrescriptionID"),
            completedAt = parseDate(json.opt("completedAt")),
            createdAt = parseDate(json.opt("createdAt")) ?: System.currentTimeMillis(),
            addedByScan = json.optBoolean("addedByScan"),
        )
    }
}

enum class TreatmentStatus { active, completed, archived }

data class Prescription(
    val id: String = UUID.randomUUID().toString(), val name: String = "",
    val prescriber: String = "", val condition: String = "",
    val facility: String = "", val contact: String = "",
    val startDate: Long = startOfToday(), val endDate: Long? = null, val notes: String = "",
    val status: TreatmentStatus = TreatmentStatus.active,
    val statusChangedAt: Long? = null,
    /** Remembers whether an archived course was Active or Completed. Missing in legacy data means Active. */
    val statusBeforeArchive: TreatmentStatus? = null,
) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("prescriber", prescriber)
        .put("condition", condition).put("facility", facility).put("contact", contact)
        .put("startDate", isoDate(startDate)).putNullable("endDate", endDate?.let(::isoDate))
        .put("notes", notes).put("status", status.name)
        .putNullable("statusChangedAt", statusChangedAt?.let(::isoDate))
        .putNullable("statusBeforeArchive", statusBeforeArchive?.name)
    companion object {
        fun fromJson(json: JSONObject) = Prescription(
            id = json.optString("id", UUID.randomUUID().toString()), name = json.optString("name"),
            prescriber = json.optString("prescriber"), condition = json.optString("condition"),
            facility = json.optString("facility"), contact = json.optString("contact"),
            startDate = parseDate(json.opt("startDate")) ?: startOfToday(),
            endDate = parseDate(json.opt("endDate")), notes = json.optString("notes"),
            status = enumValueOr(TreatmentStatus.active, json.optString("status")),
            statusChangedAt = parseDate(json.opt("statusChangedAt")),
            statusBeforeArchive = json.optNullableString("statusBeforeArchive")?.let {
                enumValueOr<TreatmentStatus>(TreatmentStatus.active, it)
            },
        )
    }
}

fun Prescription.effectiveStatus(at: Long = System.currentTimeMillis()): TreatmentStatus =
    if (status == TreatmentStatus.active && endDate?.let { startOfToday(at) > startOfToday(it) } == true)
        TreatmentStatus.completed else status

/** The user-facing lifecycle of a medicine, including its parent course when linked. */
fun Medication.effectiveStatus(parent: Prescription? = null, at: Long = System.currentTimeMillis()): TreatmentStatus = when {
    isArchived -> TreatmentStatus.archived
    completedAt != null -> TreatmentStatus.completed
    parent?.effectiveStatus(at) == TreatmentStatus.archived -> TreatmentStatus.archived
    parent?.effectiveStatus(at) == TreatmentStatus.completed -> TreatmentStatus.completed
    else -> TreatmentStatus.active
}

enum class DoseStatus { taken, skipped }
data class DoseLog(
    val id: String = UUID.randomUUID().toString(), val medicationID: String,
    val scheduledAt: Long, val status: DoseStatus, val actedAt: Long = System.currentTimeMillis(),
    val amount: Double = 1.0, val note: String = "", val isAsNeeded: Boolean = false,
) {
    fun toJson() = JSONObject().put("id", id).put("medicationID", medicationID)
        .put("scheduledAt", isoDate(scheduledAt)).put("status", status.name).put("actedAt", isoDate(actedAt))
        .put("amount", amount).put("note", note).put("isAsNeeded", isAsNeeded)
    companion object {
        fun fromJson(json: JSONObject) = DoseLog(
            id = json.optString("id", UUID.randomUUID().toString()), medicationID = json.optString("medicationID"),
            scheduledAt = parseDate(json.opt("scheduledAt")) ?: System.currentTimeMillis(),
            status = enumValueOr(DoseStatus.taken, json.optString("status")),
            actedAt = parseDate(json.opt("actedAt")) ?: System.currentTimeMillis(),
            amount = json.optDouble("amount", 1.0), note = json.optString("note"),
            isAsNeeded = json.optBoolean("isAsNeeded"),
        )
    }
}

data class AppData(
    val medications: List<Medication> = emptyList(),
    val prescriptions: List<Prescription> = emptyList(),
    val logs: List<DoseLog> = emptyList(),
    val mealTimes: MealTimes = MealTimes(),
    val schemaVersion: Int = 1,
) {
    fun toJson() = JSONObject()
        .put("medications", JSONArray(medications.map { it.toJson() }))
        .put("prescriptions", JSONArray(prescriptions.map { it.toJson() }))
        .put("logs", JSONArray(logs.map { it.toJson() })).put("mealTimes", mealTimes.toJson())
        .put("schemaVersion", schemaVersion)
    companion object {
        fun fromJson(json: JSONObject) = AppData(
            medications = json.optJSONArray("medications").objects().map(Medication::fromJson),
            prescriptions = json.optJSONArray("prescriptions").objects().map(Prescription::fromJson),
            logs = json.optJSONArray("logs").objects().map(DoseLog::fromJson),
            mealTimes = MealTimes.fromJson(json.optJSONObject("mealTimes")),
            schemaVersion = json.optInt("schemaVersion", 1),
        )
    }
}

enum class DoseState { TAKEN, SKIPPED, UPCOMING, DUE, MISSED }
data class ScheduledDose(
    val medication: Medication, val time: Long, val mealAnchor: MealAnchor?,
    val state: DoseState, val log: DoseLog? = null,
    /** Units for this occurrence — per-dose amounts can differ within a day. */
    val amount: Double = 1.0,
) {
    val id: String get() = occurrenceKey(medication.id, time)
    fun timeLabel(context: Context): String =
        mealAnchor?.label(context) ?: TimeOfDay.fromEpoch(time).label(context)
    fun amountLabel(context: Context): String = context.getString(
        R.string.amount_with_unit, prettyNumber(amount), medication.form.unitName(context, amount),
    )
    companion object {
        fun occurrenceKey(medicationID: String, time: Long) = "$medicationID-${time / 60_000L}"
    }
}

fun TimeOfDay.Companion.fromEpoch(millis: Long): TimeOfDay {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return TimeOfDay(calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
}

fun prettyNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

internal inline fun <reified T : Enum<T>> enumValueOr(fallback: T, raw: String?): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

internal fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
internal fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
internal fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
internal fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it) }

fun startOfToday(now: Long = System.currentTimeMillis()): Long = java.util.Calendar.getInstance().run {
    timeInMillis = now
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    timeInMillis
}

fun isoDate(millis: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(millis))
fun parseDate(value: Any?): Long? = when (value) {
    null, JSONObject.NULL -> null
    is Number -> value.toLong()
    is String -> listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'",
    ).firstNotNullOfOrNull { pattern -> runCatching {
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)?.time
    }.getOrNull() }
    else -> null
}
