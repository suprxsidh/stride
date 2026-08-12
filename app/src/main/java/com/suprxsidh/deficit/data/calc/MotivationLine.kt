package com.suprxsidh.deficit.data.calc

import kotlin.math.abs

enum class MotivationCategory { FLOOR_AT_RISK, WEEKLY_PROGRESS, FLOOR_INTACT_STREAK, WEIGHT_TREND, TOTAL_RUNS }

data class MotivationInputs(
    val floorAtRisk: Boolean,
    val runsThisWeek: Int,
    val weeklyTarget: Int,
    val daysLeftInclusive: Int,
    val floorIntactStreakWeeks: Int,
    val rollingWeightChangeKg: Double?,
    val totalRunsLogged: Int
)

object MotivationLine {

    private fun eligibleInPriorityOrder(inputs: MotivationInputs): List<MotivationCategory> {
        val result = mutableListOf<MotivationCategory>()
        if (inputs.floorAtRisk) result += MotivationCategory.FLOOR_AT_RISK
        result += MotivationCategory.WEEKLY_PROGRESS
        if (inputs.floorIntactStreakWeeks >= 1) result += MotivationCategory.FLOOR_INTACT_STREAK
        if (inputs.rollingWeightChangeKg != null && inputs.rollingWeightChangeKg != 0.0) result += MotivationCategory.WEIGHT_TREND
        if (inputs.totalRunsLogged > 0) result += MotivationCategory.TOTAL_RUNS
        return result
    }

    private fun render(inputs: MotivationInputs, category: MotivationCategory): String = when (category) {
        MotivationCategory.FLOOR_AT_RISK -> "Run today or tomorrow to protect your floor."
        MotivationCategory.WEEKLY_PROGRESS ->
            "${inputs.runsThisWeek}/${inputs.weeklyTarget} runs this week · ${inputs.daysLeftInclusive} days left."
        MotivationCategory.FLOOR_INTACT_STREAK -> {
            val weeks = inputs.floorIntactStreakWeeks
            "Floor unbroken $weeks week${if (weeks == 1) "" else "s"} running."
        }
        MotivationCategory.WEIGHT_TREND -> {
            val change = inputs.rollingWeightChangeKg!!
            val direction = if (change < 0) "down" else "up"
            "Rolling average $direction ${"%.1f".format(abs(change))} kg since you started."
        }
        MotivationCategory.TOTAL_RUNS -> "${inputs.totalRunsLogged} runs logged since you started."
    }

    fun dailyLine(inputs: MotivationInputs, previousCategory: MotivationCategory?): Pair<MotivationCategory, String> {
        val eligible = eligibleInPriorityOrder(inputs)
        val top = eligible.first()
        val category = if (top == previousCategory && eligible.size > 1) eligible[1] else top
        return category to render(inputs, category)
    }

    /**
     * Re-renders the line for a [category] chosen earlier today, without re-rolling the
     * category selection (used to keep the same day's motivation line stable across cold
     * starts). Returns null if [category] is no longer eligible for [inputs] — e.g. a
     * WEIGHT_TREND category persisted earlier but the rolling weight change has since become
     * unavailable — so the caller can fall back to picking a fresh category instead of crashing
     * or showing a nonsensical line.
     */
    fun renderIfEligible(inputs: MotivationInputs, category: MotivationCategory): String? =
        if (category in eligibleInPriorityOrder(inputs)) render(inputs, category) else null
}
