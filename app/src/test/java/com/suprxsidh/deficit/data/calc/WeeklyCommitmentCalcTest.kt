package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyCommitmentCalcTest {
    @Test
    fun `floorState OK runs still attainable`() {
        assertEquals(FloorState.OK, WeeklyCommitmentCalc.floorState(runsThisWeek = 0, floor = 3, daysLeftInclusive = 7))
        assertEquals(FloorState.OK, WeeklyCommitmentCalc.floorState(runsThisWeek = 2, floor = 3, daysLeftInclusive = 2))
    }

    @Test
    fun `floorState AT_RISK must run every remaining day`() {
        assertEquals(FloorState.AT_RISK, WeeklyCommitmentCalc.floorState(runsThisWeek = 1, floor = 3, daysLeftInclusive = 3))
    }

    @Test
    fun `floorState IMPOSSIBLE remaining runs exceed remaining days`() {
        assertEquals(FloorState.IMPOSSIBLE, WeeklyCommitmentCalc.floorState(runsThisWeek = 0, floor = 3, daysLeftInclusive = 2))
    }

    @Test
    fun `weekOutcome classifies broken, floor-met, target-met weeks`() {
        assertEquals(WeekOutcome.BROKEN, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 2, floor = 3, target = 4))
        assertEquals(WeekOutcome.FLOOR_MET, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 3, floor = 3, target = 4))
        assertEquals(WeekOutcome.TARGET_MET, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 4, floor = 3, target = 4))
        assertEquals(WeekOutcome.TARGET_MET, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 6, floor = 3, target = 4))
    }

    @Test
    fun `consecutiveFloorIntactStreak counts most-recent week until first broken week`() {
        assertEquals(
            3,
            WeeklyCommitmentCalc.consecutiveFloorIntactStreak(
                listOf(WeekOutcome.TARGET_MET, WeekOutcome.FLOOR_MET, WeekOutcome.TARGET_MET, WeekOutcome.BROKEN, WeekOutcome.FLOOR_MET)
            )
        )
        assertEquals(0, WeeklyCommitmentCalc.consecutiveFloorIntactStreak(listOf(WeekOutcome.BROKEN, WeekOutcome.TARGET_MET)))
        assertEquals(0, WeeklyCommitmentCalc.consecutiveFloorIntactStreak(emptyList()))
    }
}
