package com.kabi.pillpal.meditick

import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.data.transitionMedicationForPrescription
import com.kabi.pillpal.meditick.data.resolvedPrescriptionStatus
import com.kabi.pillpal.meditick.data.previousLogForDose
import com.kabi.pillpal.meditick.data.inventoryAdjustment
import com.kabi.pillpal.meditick.notifications.NotificationScheduler
import com.kabi.pillpal.meditick.notifications.shouldPreserveManagedAlarm
import com.kabi.pillpal.meditick.ui.screens.initialDoseActedAt
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DoseEngineTest {
    private val day = DoseEngine.startOfDay(System.currentTimeMillis())

    @Test fun fixedTimesGenerateInOrder() {
        val schedule = DoseSchedule(
            kind = ScheduleKind.fixedTimes,
            times = listOf(TimeOfDay(21, 0), TimeOfDay(8, 30)),
            startDate = day,
        )
        val firing = DoseEngine.firingTimes(schedule, day, MealTimes())
        assertEquals(listOf(8, 21), firing.map { TimeOfDay.fromEpoch(it.time).hour })
    }

    @Test fun mealAnchorsMoveWithMeals() {
        val schedule = DoseSchedule(
            kind = ScheduleKind.mealBased,
            mealAnchors = listOf(MealAnchor(slot = MealSlot.breakfast, relation = MealRelation.before, offsetMinutes = 30)),
            startDate = day,
        )
        val original = DoseEngine.firingTimes(schedule, day, MealTimes(breakfast = TimeOfDay(8, 0))).single().time
        val moved = DoseEngine.firingTimes(schedule, day, MealTimes(breakfast = TimeOfDay(9, 0))).single().time
        assertEquals(60 * 60_000L, moved - original)
        assertEquals(TimeOfDay(8, 30), TimeOfDay.fromEpoch(moved))
    }

    @Test fun cyclesRespectActiveAndPauseDays() {
        val schedule = DoseSchedule(
            kind = ScheduleKind.fixedTimes,
            times = listOf(TimeOfDay(9, 0)), startDate = day,
            cycleActiveDays = 2, cyclePauseDays = 1,
        )
        assertTrue(DoseEngine.isActive(schedule, day))
        assertTrue(DoseEngine.isActive(schedule, DoseEngine.addDays(day, 1)))
        assertFalse(DoseEngine.isActive(schedule, DoseEngine.addDays(day, 2)))
        assertTrue(DoseEngine.isActive(schedule, DoseEngine.addDays(day, 3)))
    }

    @Test fun doseStatesResolveAndLogsWin() {
        val scheduled = DoseEngine.atTime(day, TimeOfDay(9, 0))
        val medication = Medication(name = "Metformin", schedule = DoseSchedule(
            kind = ScheduleKind.fixedTimes, times = listOf(TimeOfDay(9, 0)), startDate = day,
        ))
        val missed = DoseEngine.doses(day, listOf(medication), MealTimes(), emptyList(), scheduled + 2 * 60 * 60_000L).single()
        assertEquals(DoseState.MISSED, missed.state)

        val log = DoseLog(medicationID = medication.id, scheduledAt = scheduled, status = DoseStatus.taken, actedAt = scheduled + 5 * 60_000L)
        val taken = DoseEngine.doses(day, listOf(medication), MealTimes(), listOf(log), scheduled + 2 * 60 * 60_000L).single()
        assertEquals(DoseState.TAKEN, taken.state)
        assertEquals(1, DoseEngine.stats(listOf(taken)).taken)
    }

    @Test fun weekdayFilterUsesCalendarNumbering() {
        val weekday = Calendar.getInstance().apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK)
        val schedule = DoseSchedule(kind = ScheduleKind.fixedTimes, startDate = day, weekdays = setOf(weekday))
        assertTrue(DoseEngine.isActive(schedule, day))
        assertFalse(DoseEngine.isActive(schedule, DoseEngine.addDays(day, 1)))
    }

    @Test fun dayIntervalUsesCalendarDays() {
        val schedule = DoseSchedule(kind = ScheduleKind.fixedTimes, startDate = day, dayInterval = 3)
        assertTrue(DoseEngine.isActive(schedule, day))
        assertFalse(DoseEngine.isActive(schedule, DoseEngine.addDays(day, 1)))
        assertFalse(DoseEngine.isActive(schedule, DoseEngine.addDays(day, 2)))
        assertTrue(DoseEngine.isActive(schedule, DoseEngine.addDays(day, 3)))
        assertEquals("Every 3 days", schedule.frequencySummary())
    }

    @Test fun treatmentCompletionAndArchiveKeepSeparateChildProvenance() {
        val rx = "rx-1"
        val child = Medication(name = "Child", prescriptionID = rx)
        val completed = transitionMedicationForPrescription(child, rx, TreatmentStatus.completed, day)
        assertFalse(completed.isArchived)
        assertEquals(rx, completed.completedByPrescriptionID)

        val archived = transitionMedicationForPrescription(completed, rx, TreatmentStatus.archived, day + 1)
        assertTrue(archived.isArchived)
        assertEquals(rx, archived.archivedByPrescriptionID)
        assertEquals(rx, archived.completedByPrescriptionID)

        val restored = transitionMedicationForPrescription(archived, rx, TreatmentStatus.completed, day + 2)
        assertFalse(restored.isArchived)
        assertNull(restored.archivedByPrescriptionID)
        assertEquals(rx, restored.completedByPrescriptionID)
        assertEquals(day, restored.completedAt)
    }

    @Test fun treatmentRestorePreservesPreexistingManualArchive() {
        val rx = "rx-1"
        val manuallyArchived = Medication(name = "Child", prescriptionID = rx, isArchived = true, archivedAt = day)
        val completed = transitionMedicationForPrescription(manuallyArchived, rx, TreatmentStatus.completed, day + 1)
        val restored = transitionMedicationForPrescription(completed, rx, TreatmentStatus.active, day + 2)
        assertTrue(restored.isArchived)
        assertNull(restored.archivedByPrescriptionID)
    }

    @Test fun treatmentTransitionsPreserveIndependentCompletion() {
        val rx = "rx-1"
        val independentTime = day - 10_000L
        val precompleted = Medication(name = "Child", prescriptionID = rx, completedAt = independentTime)
        val archived = transitionMedicationForPrescription(precompleted, rx, TreatmentStatus.archived, day)
        val restored = transitionMedicationForPrescription(archived, rx, TreatmentStatus.active, day + 1)
        assertEquals(independentTime, restored.completedAt)
        assertNull(restored.completedByPrescriptionID)
    }

    @Test fun sameDayLifecycleCutoffKeepsEarlierHistoryAndStopsLaterDoses() {
        val morning = DoseEngine.atTime(day, TimeOfDay(8, 0))
        val noon = DoseEngine.atTime(day, TimeOfDay(12, 0))
        val medication = Medication(
            name = "Course", completedAt = noon, completedByPrescriptionID = "rx",
            schedule = DoseSchedule(kind = ScheduleKind.fixedTimes, times = listOf(TimeOfDay(8, 0), TimeOfDay(16, 0)), startDate = day),
        )
        val doses = DoseEngine.doses(day, listOf(medication), MealTimes(), emptyList(), noon)
        assertEquals(listOf(morning), doses.map { it.time })
    }

    @Test fun sameDayArchiveCutoffKeepsEarlierHistoryAndStopsLaterDoses() {
        val noon = DoseEngine.atTime(day, TimeOfDay(12, 0))
        val medication = Medication(
            name = "Course", isArchived = true, archivedAt = noon, archivedByPrescriptionID = "rx",
            schedule = DoseSchedule(kind = ScheduleKind.fixedTimes, times = listOf(TimeOfDay(8, 0), TimeOfDay(16, 0)), startDate = day),
        )
        assertEquals(1, DoseEngine.doses(day, listOf(medication), MealTimes(), emptyList(), noon).size)
    }

    @Test fun dayIntervalExtendsStockForecast() {
        val medication = Medication(
            inventoryEnabled = true, stock = 10.0,
            schedule = DoseSchedule(kind = ScheduleKind.fixedTimes, times = listOf(TimeOfDay(8, 0)), dayInterval = 2),
        )
        assertEquals(20, medication.daysOfStockRemaining)
    }

    @Test fun finiteCourseUsesDateAwareEffectiveStatus() {
        val ended = Prescription(status = TreatmentStatus.active, endDate = DoseEngine.addDays(day, -1))
        assertEquals(TreatmentStatus.completed, ended.effectiveStatus(day))
        assertEquals(TreatmentStatus.active, ended.effectiveStatus(DoseEngine.addDays(day, -1)))
        assertEquals(TreatmentStatus.completed, Medication(prescriptionID = ended.id).effectiveStatus(ended, day))
    }

    @Test fun completedCourseArchivesAndRestoresAsCompleted() {
        val archived = Prescription(
            status = TreatmentStatus.archived,
            statusBeforeArchive = TreatmentStatus.completed,
        )
        assertEquals(TreatmentStatus.completed, resolvedPrescriptionStatus(archived, TreatmentStatus.active))
    }

    @Test fun unresolvedSnoozeSurvivesBulkRescheduling() {
        val medicationId = "med"
        assertTrue(shouldPreserveManagedAlarm(NotificationScheduler.KIND_SNOOZE, medicationId, day, emptyList()))
        val resolved = DoseLog(medicationID = medicationId, scheduledAt = day, status = DoseStatus.taken)
        assertFalse(shouldPreserveManagedAlarm(NotificationScheduler.KIND_SNOOZE, medicationId, day, listOf(resolved)))
        assertFalse(shouldPreserveManagedAlarm(NotificationScheduler.KIND_DOSE, medicationId, day, emptyList()))
    }

    @Test fun priorDayDoseDefaultsLoggedTimeToOccurrenceDateAndFutureDoseClampsToNow() {
        val now = DoseEngine.addDays(day, 2)
        assertEquals(day, initialDoseActedAt(day, now))
        assertEquals(now, initialDoseActedAt(DoseEngine.addDays(now, 1), now))
    }

    @Test fun fallbackDoseFindsPersistedOccurrenceLogForIdempotentTake() {
        val medication = Medication(name = "Stocked")
        val staleDose = ScheduledDose(medication, day, null, DoseState.DUE, log = null)
        val persisted = DoseLog(medicationID = medication.id, scheduledAt = day, status = DoseStatus.taken)
        assertEquals(persisted, previousLogForDose(listOf(persisted), staleDose))
        assertEquals(DoseStatus.taken, previousLogForDose(listOf(persisted), staleDose)?.status)
        assertEquals(0.0, inventoryAdjustment(persisted, DoseStatus.taken, medication.schedule.amountPerDose), 0.0)
    }
}
