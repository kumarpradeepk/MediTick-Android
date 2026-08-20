package com.kabi.pillpal.meditick.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.notifications.NotificationScheduler
import com.kabi.pillpal.meditick.widget.MediTickWidgetProvider
import org.json.JSONObject
import java.io.File

class AppRepository private constructor(private val context: Context) {
    private val dataFile = File(context.filesDir, "pillpal-data.json")
    var data by mutableStateOf(load())
        private set

    val medications get() = data.medications
    val activeMedications get() = data.medications.filter { medication ->
        medication.effectiveStatus(prescription(medication.prescriptionID)) == TreatmentStatus.active
    }
    val prescriptions get() = data.prescriptions
    val logs get() = data.logs
    val mealTimes get() = data.mealTimes
    val appContext: Context get() = context

    @Synchronized
    fun addMedication(medication: Medication) = update(data.copy(medications = data.medications + medication))

    fun duplicateMedication(name: String, strengthValue: Double?, strengthUnit: String, excludingId: String? = null): Medication? {
        val normalized = normalizeName(name)
        return data.medications.firstOrNull {
            it.id != excludingId && normalizeName(it.name) == normalized && it.strengthValue == strengthValue &&
                it.strengthUnit.equals(strengthUnit, ignoreCase = true)
        }
    }

    @Synchronized
    fun updateMedication(medication: Medication) = update(data.copy(
        medications = data.medications.map { if (it.id == medication.id) medication else it },
    ))

    @Synchronized
    fun deleteMedication(id: String) = update(data.copy(
        medications = data.medications.filterNot { it.id == id },
        logs = data.logs.filterNot { it.medicationID == id },
    ))

    fun archiveMedication(id: String, archived: Boolean) {
        medication(id)?.let {
            updateMedication(it.copy(
                isArchived = archived,
                archivedByPrescriptionID = null,
                archivedAt = if (archived) System.currentTimeMillis() else null,
            ))
        }
    }

    fun unlinkMedication(id: String) {
        medication(id)?.let { value ->
            val cascaded = value.archivedByPrescriptionID == value.prescriptionID
            updateMedication(value.copy(
                prescriptionID = null,
                isArchived = if (cascaded) false else value.isArchived,
                archivedByPrescriptionID = null,
                archivedAt = if (cascaded) null else value.archivedAt,
                completedByPrescriptionID = if (value.completedByPrescriptionID == value.prescriptionID) null else value.completedByPrescriptionID,
                completedAt = if (value.completedByPrescriptionID == value.prescriptionID) null else value.completedAt,
            ))
        }
    }

    fun linkMedication(id: String, prescriptionId: String?) {
        val value = medication(id) ?: return
        if (prescriptionId != null && prescription(prescriptionId) == null) return
        updateMedication(value.copy(
            prescriptionID = prescriptionId,
            completedByPrescriptionID = if (value.completedByPrescriptionID == value.prescriptionID) null else value.completedByPrescriptionID,
            completedAt = if (value.completedByPrescriptionID == value.prescriptionID) null else value.completedAt,
        ))
    }

    fun medication(id: String?) = data.medications.firstOrNull { it.id == id }
    fun prescription(id: String?) = data.prescriptions.firstOrNull { it.id == id }
    fun medicationsIn(prescriptionId: String) = data.medications.filter { it.prescriptionID == prescriptionId }

    fun addPrescription(value: Prescription) = update(data.copy(prescriptions = data.prescriptions + value))
    fun updatePrescription(value: Prescription, updateMedicationDates: Boolean = false) = update(data.copy(
        prescriptions = data.prescriptions.map { if (it.id == value.id) value else it },
        medications = if (!updateMedicationDates) data.medications else data.medications.map { medication ->
            if (medication.prescriptionID != value.id) medication else medication.copy(
                schedule = medication.schedule.copy(startDate = value.startDate, endDate = value.endDate),
            )
        },
    ))

    fun setPrescriptionStatus(id: String, status: TreatmentStatus) {
        val rx = prescription(id) ?: return
        val now = System.currentTimeMillis()
        val targetStatus = resolvedPrescriptionStatus(rx, status)
        val meds = data.medications.map { medication ->
            if (medication.prescriptionID != id) return@map medication
            transitionMedicationForPrescription(medication, id, targetStatus, now)
        }
        update(data.copy(
            prescriptions = data.prescriptions.map {
                if (it.id != rx.id) it else it.copy(
                    status = targetStatus,
                    statusChangedAt = now,
                    statusBeforeArchive = if (status == TreatmentStatus.archived) rx.effectiveStatus(now) else null,
                    endDate = if (targetStatus == TreatmentStatus.active && it.endDate?.let { end -> startOfToday(now) > startOfToday(end) } == true) null else it.endDate,
                )
            },
            medications = meds,
        ))
    }

    fun deletePrescription(id: String) = update(data.copy(
        prescriptions = data.prescriptions.filterNot { it.id == id },
        medications = data.medications.map {
            if (it.prescriptionID != id) it else {
                val cascaded = it.archivedByPrescriptionID == id
                it.copy(
                    prescriptionID = null,
                    isArchived = if (cascaded) false else it.isArchived,
                    archivedByPrescriptionID = null,
                    archivedAt = if (cascaded) null else it.archivedAt,
                    completedByPrescriptionID = if (it.completedByPrescriptionID == id) null else it.completedByPrescriptionID,
                    completedAt = if (it.completedByPrescriptionID == id) null else it.completedAt,
                )
            }
        },
    ))

    fun doses(day: Long, now: Long = System.currentTimeMillis()): List<ScheduledDose> =
        DoseEngine.doses(day, data.medications, data.mealTimes, data.logs, now)

    fun logDose(dose: ScheduledDose, status: DoseStatus, actedAt: Long = System.currentTimeMillis(), note: String = "") {
        logDoses(listOf(dose), status, actedAt, note)
    }

    @Synchronized
    fun logDoses(doses: List<ScheduledDose>, status: DoseStatus, actedAt: Long = System.currentTimeMillis(), note: String = "") {
        if (doses.isEmpty()) return
        var nextLogs = data.logs
        var meds = data.medications
        doses.distinctBy { it.id }.forEach { dose ->
            val previous = previousLogForDose(nextLogs, dose)
            val existing = dose.log?.id
            nextLogs = nextLogs.filterNot {
                it.id == existing || (!it.isAsNeeded && ScheduledDose.occurrenceKey(it.medicationID, it.scheduledAt) == dose.id)
            }
            val log = DoseLog(
                // The occurrence's own amount — per-dose amounts mean two doses
                // of one medication on the same day can differ.
                medicationID = dose.medication.id, scheduledAt = dose.time, status = status,
                actedAt = actedAt, amount = dose.amount, note = note,
            )
            nextLogs += log
            val stockAdjustment = inventoryAdjustment(previous, status, log.amount)
            if (stockAdjustment != 0.0) meds = adjustStock(meds, dose.medication.id, stockAdjustment)
        }
        update(data.copy(medications = meds, logs = nextLogs))
    }

    fun removeLog(dose: ScheduledDose) {
        val removed = data.logs.filter { log ->
            dose.log?.id?.let { log.id == it }
                ?: (!log.isAsNeeded && ScheduledDose.occurrenceKey(log.medicationID, log.scheduledAt) == dose.id)
        }
        if (removed.isEmpty()) return
        var meds = data.medications
        removed.filter { it.status == DoseStatus.taken }.forEach { meds = adjustStock(meds, it.medicationID, it.amount) }
        update(data.copy(medications = meds, logs = data.logs.filterNot { log -> removed.any { it.id == log.id } }))
    }

    fun deleteLog(id: String) {
        val log = data.logs.firstOrNull { it.id == id } ?: return
        val meds = if (log.status == DoseStatus.taken) adjustStock(data.medications, log.medicationID, log.amount) else data.medications
        update(data.copy(medications = meds, logs = data.logs.filterNot { it.id == id }))
    }

    fun logAsNeeded(medication: Medication, amount: Double, note: String) {
        if (amount <= 0 || !amount.isFinite()) return
        val now = System.currentTimeMillis()
        val log = DoseLog(
            medicationID = medication.id, scheduledAt = now, status = DoseStatus.taken,
            actedAt = now, amount = amount, note = note, isAsNeeded = true,
        )
        update(data.copy(
            medications = adjustStock(data.medications, medication.id, -amount),
            logs = data.logs + log,
        ))
    }

    fun refillStock(id: String, amount: Double) {
        if (amount <= 0 || !amount.isFinite()) return
        update(data.copy(medications = adjustStock(data.medications, id, amount)))
    }

    fun logsForPrescription(id: String): List<DoseLog> {
        val ids = medicationsIn(id).mapTo(mutableSetOf()) { it.id }
        return data.logs.filter { it.medicationID in ids }.sortedByDescending { it.actedAt }
    }

    fun nextDoseFor(medicationId: String, now: Long = System.currentTimeMillis()): ScheduledDose? {
        repeat(31) { offset ->
            doses(DoseEngine.addDays(now, offset), now).firstOrNull {
                it.medication.id == medicationId && it.time >= now && it.state !in setOf(DoseState.TAKEN, DoseState.SKIPPED)
            }?.let { return it }
        }
        return null
    }

    fun setMealTimes(value: MealTimes) = update(data.copy(mealTimes = value))
    fun eraseAll() = update(AppData())
    fun exportJson(): String = data.toJson().toString(2)

    fun importJson(raw: String): Boolean = runCatching {
        val decoded = AppData.fromJson(JSONObject(raw))
        require(decoded.schemaVersion in 1..1)
        update(decoded)
    }.isSuccess

    private fun adjustStock(list: List<Medication>, id: String, amount: Double) = list.map {
        if (it.id == id && it.inventoryEnabled) it.copy(stock = (it.stock + amount).coerceAtLeast(0.0)) else it
    }

    @Synchronized
    private fun update(value: AppData) {
        val encoded = value.toJson().toString(2)
        val persisted = runCatching {
            val temporary = File(context.filesDir, "pillpal-data.json.tmp")
            temporary.writeText(encoded)
            if (!temporary.renameTo(dataFile)) {
                dataFile.writeText(encoded)
                temporary.delete()
            }
        }.isSuccess
        if (!persisted) return
        data = value
        NotificationScheduler.scheduleAll(context, this, SettingsStore.get(context))
        MediTickWidgetProvider.updateAll(context)
    }

    private fun load(): AppData = runCatching {
        if (!dataFile.exists()) AppData() else AppData.fromJson(JSONObject(dataFile.readText()))
    }.getOrDefault(AppData())

    companion object {
        @Volatile private var instance: AppRepository? = null
        fun get(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context.applicationContext).also { instance = it }
        }

        internal fun normalizeName(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}

internal fun resolvedPrescriptionStatus(prescription: Prescription, requested: TreatmentStatus): TreatmentStatus =
    if (requested == TreatmentStatus.active && prescription.status == TreatmentStatus.archived) {
        prescription.statusBeforeArchive ?: TreatmentStatus.active
    } else requested

internal fun previousLogForDose(logs: List<DoseLog>, dose: ScheduledDose): DoseLog? =
    dose.log?.id?.let { id -> logs.firstOrNull { it.id == id } }
        ?: logs.firstOrNull {
            !it.isAsNeeded && ScheduledDose.occurrenceKey(it.medicationID, it.scheduledAt) == dose.id
        }

internal fun inventoryAdjustment(previous: DoseLog?, target: DoseStatus, targetAmount: Double): Double = when {
    target == DoseStatus.taken && previous?.status != DoseStatus.taken -> -targetAmount
    target != DoseStatus.taken && previous?.status == DoseStatus.taken -> previous.amount
    else -> 0.0
}

internal fun transitionMedicationForPrescription(
    medication: Medication, prescriptionId: String, status: TreatmentStatus, changedAt: Long,
): Medication = when (status) {
    TreatmentStatus.active -> if (medication.archivedByPrescriptionID == prescriptionId) medication.copy(
        isArchived = false, archivedByPrescriptionID = null, archivedAt = null,
        completedByPrescriptionID = if (medication.completedByPrescriptionID == prescriptionId) null else medication.completedByPrescriptionID,
        completedAt = if (medication.completedByPrescriptionID == prescriptionId) null else medication.completedAt,
    ) else medication.copy(
        completedByPrescriptionID = if (medication.completedByPrescriptionID == prescriptionId) null else medication.completedByPrescriptionID,
        completedAt = if (medication.completedByPrescriptionID == prescriptionId) null else medication.completedAt,
    )
    TreatmentStatus.completed -> {
        val cascadedArchive = medication.archivedByPrescriptionID == prescriptionId
        medication.copy(
            isArchived = if (cascadedArchive) false else medication.isArchived,
            archivedByPrescriptionID = if (cascadedArchive) null else medication.archivedByPrescriptionID,
            archivedAt = if (cascadedArchive) null else medication.archivedAt,
            completedByPrescriptionID = if (medication.completedAt == null || medication.completedByPrescriptionID == prescriptionId) prescriptionId else medication.completedByPrescriptionID,
            completedAt = medication.completedAt ?: changedAt,
        )
    }
    TreatmentStatus.archived -> {
        val shouldCascade = !medication.isArchived
        medication.copy(
            isArchived = true,
            archivedByPrescriptionID = if (shouldCascade) prescriptionId else medication.archivedByPrescriptionID,
            archivedAt = if (shouldCascade) changedAt else medication.archivedAt,
        )
    }
}
