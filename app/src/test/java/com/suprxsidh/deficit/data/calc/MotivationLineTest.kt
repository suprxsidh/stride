package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class MotivationLineTest {
    private val richInputs = MotivationInputs(
        floorAtRisk = true,
        runsThisWeek = 2,
        weeklyTarget = 4,
        daysLeftInclusive = 3,
        floorIntactStreakWeeks = 5,
        rollingWeightChangeKg = -1.8,
        totalRunsLogged = 14
    )

    @Test
    fun `top eligible category wins when it was not shown yesterday`() {
        val (category, line) = MotivationLine.dailyLine(richInputs, previousCategory = null)
        assertEquals(MotivationCategory.FLOOR_AT_RISK, category)
        assertEquals("Run today or tomorrow to protect your floor.", line)
    }

    @Test
    fun `repeating yesterday's top category is skipped in favor of the next one when an alternative exists`() {
        val (category, _) = MotivationLine.dailyLine(richInputs, previousCategory = MotivationCategory.FLOOR_AT_RISK)
        assertEquals(MotivationCategory.WEEKLY_PROGRESS, category)
    }

    @Test
    fun `the only eligible category repeats when there is no alternative`() {
        val sparse = MotivationInputs(
            floorAtRisk = false,
            runsThisWeek = 0,
            weeklyTarget = 4,
            daysLeftInclusive = 7,
            floorIntactStreakWeeks = 0,
            rollingWeightChangeKg = null,
            totalRunsLogged = 0
        )
        val (category, line) = MotivationLine.dailyLine(sparse, previousCategory = MotivationCategory.WEEKLY_PROGRESS)
        assertEquals(MotivationCategory.WEEKLY_PROGRESS, category)
        assertEquals("0/4 runs this week · 7 days left.", line)
    }

    @Test
    fun `weight trend line reports direction and one decimal place`() {
        val up = richInputs.copy(floorAtRisk = false, rollingWeightChangeKg = 0.6, floorIntactStreakWeeks = 0)
        val (_, line) = MotivationLine.dailyLine(up, previousCategory = MotivationCategory.WEEKLY_PROGRESS)
        assertEquals("Rolling average up 0.6 kg since you started.", line)
    }

    @Test
    fun `floor-intact streak pluralizes correctly`() {
        val oneWeek = richInputs.copy(floorAtRisk = false, floorIntactStreakWeeks = 1)
        val (_, line) = MotivationLine.dailyLine(oneWeek, previousCategory = MotivationCategory.WEEKLY_PROGRESS)
        assertEquals("Floor unbroken 1 week running.", line)
    }

    // renderIfEligible backs DashboardViewModel's "one line per logical day" fix: on a
    // same-day reload it re-renders the already-persisted category against current inputs
    // instead of re-rolling dailyLine's rotation logic.
    @Test
    fun `renderIfEligible re-renders an eligible category without rolling to a different one`() {
        val line = MotivationLine.renderIfEligible(richInputs, MotivationCategory.FLOOR_AT_RISK)
        assertEquals("Run today or tomorrow to protect your floor.", line)
    }

    @Test
    fun `renderIfEligible returns null when the stored category is no longer eligible`() {
        // WEIGHT_TREND requires a non-null, non-zero rollingWeightChangeKg; if that stat
        // vanished intra-day, the caller must be told to fall back to a fresh pick rather
        // than crashing on WEIGHT_TREND's non-null assertion or showing a stale line.
        val noWeightData = richInputs.copy(rollingWeightChangeKg = null)
        val line = MotivationLine.renderIfEligible(noWeightData, MotivationCategory.WEIGHT_TREND)
        assertEquals(null, line)
    }
}
