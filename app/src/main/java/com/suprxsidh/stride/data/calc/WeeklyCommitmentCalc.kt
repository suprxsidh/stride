package com.suprxsidh.stride.data.calc

enum class FloorState {
    OK,
    AT_RISK,
    IMPOSSIBLE
}

enum class WeekOutcome {
    BROKEN,
    FLOOR_MET,
    TARGET_MET
}

object WeeklyCommitmentCalc {
    fun floorState(runsThisWeek: Int, floor: Int, daysLeftInclusive: Int): FloorState {
        val remaining = (floor - runsThisWeek).coerceAtLeast(0)
        return when {
            remaining == 0 -> FloorState.OK
            remaining > daysLeftInclusive -> FloorState.IMPOSSIBLE
            remaining == daysLeftInclusive -> FloorState.AT_RISK
            else -> FloorState.OK
        }
    }

    fun weekOutcome(runsThisWeek: Int, floor: Int, target: Int): WeekOutcome {
        return when {
            runsThisWeek < floor -> WeekOutcome.BROKEN
            runsThisWeek < target -> WeekOutcome.FLOOR_MET
            else -> WeekOutcome.TARGET_MET
        }
    }

    fun consecutiveFloorIntactStreak(completedWeekOutcomesMostRecentFirst: List<WeekOutcome>): Int {
        var count = 0
        for (outcome in completedWeekOutcomesMostRecentFirst) {
            if (outcome == WeekOutcome.BROKEN) {
                break
            }
            count++
        }
        return count
    }
}
