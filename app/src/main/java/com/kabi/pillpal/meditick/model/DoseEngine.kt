package com.kabi.pillpal.meditick.model

import java.util.Calendar
import kotlin.math.abs

object DoseEngine {
    const val GRACE_MINUTES = 60
    private const val MATCH_TOLERANCE_MINUTES = 120

    data class FiringTime(val time: Long, val anchor: MealAnchor? = null, val amount: Double = 1.0)

    data class Stats(
        val taken: Int = 0, val skipped: Int = 0, val missed: Int = 0, val pending: Int = 0,
    ) {
        val scheduled get() = taken + skipped + missed + pending
        val decided get() = taken + skipped + missed

        /**
         * Adherence counts doses actually taken out of everything scheduled.
         * Skipping resolves the day but does not raise the percentage, so
         * skipped doses stay in the denominator.
         */
        val ratio get() = if (scheduled == 0) 1.0 else taken.toDouble() / scheduled
        val missedOrPending get() = missed + pending
        val completionRatio get() = if (scheduled == 0) 0.0 else (taken + skipped).toDouble() / scheduled
        operator fun plus(other: Stats) = Stats(
            taken + other.taken, skipped + other.skipped, missed + other.missed, pending + other.pending,
        )
    }

    /** How a day looks on the adherence calendar. */
    enum class DayAdherence { COMPLETE, PARTIAL, MISSED, NONE }

    fun dayAdherence(doses: List<ScheduledDose>): DayAdherence {
        val day = stats(doses)
        if (day.scheduled == 0) return DayAdherence.NONE
        if (day.missed > 0) return DayAdherence.MISSED
        val resolved = day.taken + day.skipped
        if (resolved == 0) return DayAdherence.NONE
        return if (day.pending == 0) DayAdherence.COMPLETE else DayAdherence.PARTIAL
    }

    /** How taken doses landed relative to schedule, for the on-time card. */
    data class TimingSummary(
        val onTime: Int = 0, val early: Int = 0, val late: Int = 0, val skipped: Int = 0,
    ) {
        val takenTotal get() = onTime + early + late
        val ratio: Double? get() = if (takenTotal == 0) null else onTime.toDouble() / takenTotal
    }

    fun timingSummary(logs: List<DoseLog>, from: Long, windowMinutes: Int): TimingSummary {
        var onTime = 0; var early = 0; var late = 0; var skipped = 0
        val window = windowMinutes * 60_000L
        logs.filter { !it.isAsNeeded && it.scheduledAt >= from }.forEach { log ->
            if (log.status == DoseStatus.skipped) { skipped++; return@forEach }
            val drift = log.actedAt - log.scheduledAt
            when {
                abs(drift) <= window -> onTime++
                drift < 0 -> early++
                else -> late++
            }
        }
        return TimingSummary(onTime, early, late, skipped)
    }

    fun isActive(schedule: DoseSchedule, day: Long): Boolean {
        val dayStart = startOfDay(day)
        val scheduleStart = startOfDay(schedule.startDate)
        if (dayStart < scheduleStart || (schedule.endDate?.let { dayStart > startOfDay(it) } == true)) return false

        val calendar = Calendar.getInstance().apply { timeInMillis = dayStart }
        if (schedule.weekdays.isNotEmpty() && calendar.get(Calendar.DAY_OF_WEEK) !in schedule.weekdays) return false

        if (schedule.dayInterval > 1 && calendarDayDistance(scheduleStart, dayStart) % schedule.dayInterval != 0) return false

        val on = schedule.cycleActiveDays
        val off = schedule.cyclePauseDays
        if (on != null && off != null && on > 0 && off > 0) {
            val daysIn = calendarDayDistance(scheduleStart, dayStart)
            if (daysIn % (on + off) >= on) return false
        }
        return true
    }

    fun firingTimes(schedule: DoseSchedule, day: Long, mealTimes: MealTimes): List<FiringTime> {
        if (!isActive(schedule, day)) return emptyList()
        val start = startOfDay(day)
        val raw = when (schedule.kind) {
            // Per-dose specs carry their own amount and meal relation, so one
            // medication can mix fixed and meal-linked doses.
            ScheduleKind.fixedTimes, ScheduleKind.mealBased -> schedule.resolvedDoses.map { spec ->
                val anchor = spec.anchor
                if (anchor == null) {
                    FiringTime(atTime(start, spec.time), null, spec.amount)
                } else {
                    FiringTime(
                        atTime(start, mealTimes.time(anchor.slot)) + anchor.signedOffset * 60_000L,
                        anchor,
                        spec.amount,
                    )
                }
            }.sortedBy { it.time }
            ScheduleKind.interval -> buildList {
                if (schedule.intervalHours <= 0) return@buildList
                var cursor = schedule.intervalStart.totalMinutes
                while (cursor <= schedule.intervalEnd.totalMinutes) {
                    add(FiringTime(atTime(start, TimeOfDay(cursor / 60, cursor % 60)), null, schedule.amountPerDose))
                    cursor += schedule.intervalHours * 60
                }
            }
            ScheduleKind.asNeeded -> emptyList()
        }
        return raw.distinctBy { it.time / 60_000L }
    }

    fun doses(
        day: Long,
        medications: List<Medication>,
        mealTimes: MealTimes,
        logs: List<DoseLog>,
        now: Long = System.currentTimeMillis(),
    ): List<ScheduledDose> = buildList {
        medications.filterNot { it.isArchived && it.archivedAt == null }.forEach { medication ->
            val firing = firingTimes(medication.schedule, day, mealTimes).filter { occurrence ->
                (!medication.isArchived || medication.archivedAt?.let { occurrence.time < it } == true) &&
                    (medication.completedAt?.let { occurrence.time < it } != false)
            }
            if (firing.isEmpty()) return@forEach
            val tolerance = MATCH_TOLERANCE_MINUTES * 60_000L
            val medLogs = logs.filter {
                !it.isAsNeeded && it.medicationID == medication.id &&
                    it.scheduledAt >= firing.first().time - tolerance &&
                    it.scheduledAt <= firing.last().time + tolerance
            }
            val claimed = mutableSetOf<String>()
            firing.forEach { occurrence ->
                val exact = medLogs.firstOrNull {
                    it.id !in claimed && abs(it.scheduledAt - occurrence.time) < 60_000L
                }
                val fuzzy = exact ?: medLogs.filter { log ->
                    log.id !in claimed && firing.none { abs(it.time - log.scheduledAt) < 60_000L } &&
                        abs(log.scheduledAt - occurrence.time) <= tolerance
                }.minByOrNull { abs(it.scheduledAt - occurrence.time) }
                fuzzy?.let { claimed += it.id }

                val state = when {
                    fuzzy?.status == DoseStatus.taken -> DoseState.TAKEN
                    fuzzy?.status == DoseStatus.skipped -> DoseState.SKIPPED
                    occurrence.time > now -> DoseState.UPCOMING
                    now <= occurrence.time + GRACE_MINUTES * 60_000L -> DoseState.DUE
                    else -> DoseState.MISSED
                }
                add(ScheduledDose(medication, occurrence.time, occurrence.anchor, state, fuzzy, occurrence.amount))
            }
        }
    }.sortedBy { it.time }

    fun stats(doses: List<ScheduledDose>): Stats {
        var taken = 0; var skipped = 0; var missed = 0; var pending = 0
        doses.forEach { when (it.state) {
            DoseState.TAKEN -> taken++
            DoseState.SKIPPED -> skipped++
            DoseState.MISSED -> missed++
            DoseState.UPCOMING, DoseState.DUE -> pending++
        } }
        return Stats(taken, skipped, missed, pending)
    }

    fun stats(
        fromDay: Long, toDay: Long, medications: List<Medication>, mealTimes: MealTimes,
        logs: List<DoseLog>, now: Long = System.currentTimeMillis(),
    ): Stats {
        var total = Stats()
        var day = startOfDay(fromDay)
        val end = startOfDay(toDay)
        while (day <= end) {
            total += stats(doses(day, medications, mealTimes, logs, now))
            day = addDays(day, 1)
        }
        return total
    }

    /**
     * Consecutive days where every scheduled dose was *resolved*. A resolved
     * dose can be taken or skipped, so deliberately skipping keeps the day
     * complete; only a missed dose breaks the run. Quiet days don't break it.
     */
    fun currentStreak(data: AppData, now: Long = System.currentTimeMillis(), maxDays: Int = 365): Int {
        var streak = 0
        var day = startOfDay(now)
        repeat(maxDays) { index ->
            val stats = stats(doses(day, data.medications, data.mealTimes, data.logs, now))
            val resolved = stats.taken + stats.skipped
            val complete = stats.missed == 0 && stats.pending == 0 && resolved > 0
            val empty = stats.scheduled == 0 || (resolved == 0 && stats.missed == 0)
            when {
                complete -> streak++
                empty || (index == 0 && stats.missed == 0) -> Unit
                else -> return streak
            }
            day = addDays(day, -1)
        }
        return streak
    }

    fun bestStreak(data: AppData, maxDays: Int = 365): Int {
        var best = 0; var current = 0
        var day = addDays(startOfToday(), -(maxDays - 1))
        repeat(maxDays) {
            val value = stats(doses(day, data.medications, data.mealTimes, data.logs, System.currentTimeMillis()))
            val resolved = value.taken + value.skipped
            if (resolved > 0 && value.missed == 0 && value.pending == 0) {
                current++; best = maxOf(best, current)
            } else if (value.scheduled > 0 && value.pending == 0) current = 0
            day = addDays(day, 1)
        }
        return best
    }

    /** The slot accounting for the most missed doses — the pattern line. */
    data class SlotPattern(val slot: Int, val missed: Int, val total: Int)

    /**
     * Buckets missed doses by day part (0 morning, 1 midday, 2 evening,
     * 3 bedtime) and returns the worst one, or null when nothing was missed.
     */
    fun hardestSlot(
        fromDay: Long, toDay: Long, medications: List<Medication>, mealTimes: MealTimes,
        logs: List<DoseLog>, now: Long = System.currentTimeMillis(),
    ): SlotPattern? {
        val missedBySlot = IntArray(4)
        var total = 0
        var day = startOfDay(fromDay)
        val end = startOfDay(toDay)
        while (day <= end) {
            doses(day, medications, mealTimes, logs, now)
                .filter { it.state == DoseState.MISSED }
                .forEach { dose ->
                    val hour = Calendar.getInstance().apply { timeInMillis = dose.time }.get(Calendar.HOUR_OF_DAY)
                    val slot = when {
                        hour < 11 -> 0
                        hour < 16 -> 1
                        hour < 21 -> 2
                        else -> 3
                    }
                    missedBySlot[slot]++
                    total++
                }
            day = addDays(day, 1)
        }
        if (total == 0) return null
        val worst = missedBySlot.indices.maxByOrNull { missedBySlot[it] } ?: return null
        return SlotPattern(worst, missedBySlot[worst], total)
    }

    fun onTimeRate(logs: List<DoseLog>, windowMinutes: Int, from: Long): Double {
        val taken = logs.filter { !it.isAsNeeded && it.status == DoseStatus.taken && it.scheduledAt >= from }
        if (taken.isEmpty()) return 1.0
        return taken.count { abs(it.actedAt - it.scheduledAt) <= windowMinutes * 60_000L }.toDouble() / taken.size
    }

    fun startOfDay(value: Long): Long = Calendar.getInstance().run {
        timeInMillis = value
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    fun addDays(value: Long, amount: Int): Long = Calendar.getInstance().run {
        timeInMillis = value; add(Calendar.DAY_OF_YEAR, amount); timeInMillis
    }

    fun atTime(day: Long, time: TimeOfDay): Long = Calendar.getInstance().run {
        timeInMillis = day
        set(Calendar.HOUR_OF_DAY, time.hour); set(Calendar.MINUTE, time.minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    /** Difference between local calendar dates, deliberately independent of DST-length days. */
    fun calendarDayDistance(from: Long, to: Long): Int {
        fun utcOrdinal(value: Long): Long {
            val local = Calendar.getInstance().apply { timeInMillis = value }
            return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).run {
                clear()
                set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                timeInMillis / 86_400_000L
            }
        }
        return (utcOrdinal(to) - utcOrdinal(from)).toInt()
    }
}
