package com.kabi.pillpal.meditick.model

import java.util.Calendar
import kotlin.math.abs

object DoseEngine {
    const val GRACE_MINUTES = 60
    private const val MATCH_TOLERANCE_MINUTES = 120

    data class FiringTime(val time: Long, val anchor: MealAnchor? = null)
    data class Stats(
        val taken: Int = 0, val skipped: Int = 0, val missed: Int = 0, val pending: Int = 0,
    ) {
        val scheduled get() = taken + skipped + missed + pending
        val decided get() = taken + skipped + missed
        val ratio get() = if (decided == 0) 1.0 else taken.toDouble() / decided
        val completionRatio get() = if (scheduled == 0) 0.0 else (taken + skipped).toDouble() / scheduled
        operator fun plus(other: Stats) = Stats(
            taken + other.taken, skipped + other.skipped, missed + other.missed, pending + other.pending,
        )
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
            ScheduleKind.fixedTimes -> schedule.times.sorted().map { FiringTime(atTime(start, it)) }
            ScheduleKind.mealBased -> schedule.mealAnchors.map { anchor ->
                FiringTime(atTime(start, mealTimes.time(anchor.slot)) + anchor.signedOffset * 60_000L, anchor)
            }.sortedBy { it.time }
            ScheduleKind.interval -> buildList {
                if (schedule.intervalHours <= 0) return@buildList
                var cursor = schedule.intervalStart.totalMinutes
                while (cursor <= schedule.intervalEnd.totalMinutes) {
                    add(FiringTime(atTime(start, TimeOfDay(cursor / 60, cursor % 60))))
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
                add(ScheduledDose(medication, occurrence.time, occurrence.anchor, state, fuzzy))
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

    fun currentStreak(data: AppData, now: Long = System.currentTimeMillis(), maxDays: Int = 365): Int {
        var streak = 0
        var day = startOfDay(now)
        repeat(maxDays) { index ->
            val stats = stats(doses(day, data.medications, data.mealTimes, data.logs, now))
            val perfect = stats.missed == 0 && stats.skipped == 0 && stats.taken > 0
            val empty = stats.scheduled == 0 || (stats.taken == 0 && stats.missed == 0 && stats.skipped == 0)
            when {
                perfect -> streak++
                empty || (index == 0 && stats.missed == 0 && stats.skipped == 0) -> Unit
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
            if (value.taken > 0 && value.missed == 0 && value.skipped == 0) {
                current++; best = maxOf(best, current)
            } else if (value.scheduled > 0 && value.pending == 0) current = 0
            day = addDays(day, 1)
        }
        return best
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
