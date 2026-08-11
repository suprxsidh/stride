package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyCommitmentCalcTest {
    @Test
    fun `floorState is OK when the floor is already met`() {
        assertEquals(FloorState.OK, WeeklyCommitmentCalc.floorState(runsThisWeek = 3, floor = 3, daysLeftInclusive = 5))
        assertEquals(FloorState.OK, WeeklyCommitmentCalc.floorState(runsThisWeek = 5, floor = 3, daysLeftInclusive = 1))
    }

    @Test
    fun `floorState is OK when there is still slack in the remaining days`() {
        // 3 runs still needed, 4 days left -> comfortable.
        assertEquals(FloorState.OK, WeeklyCommitmentCalc.floorState(runsThisWeek = 0, floor = 3, daysLeftInclusive = 4))
    }

    @Test
    fun `floorState is AT_RISK when remaining runs exactly equal remaining days`() {
        // 3 runs still needed, exactly 3 days left -> must run every remaining day.
        assertEquals(FloorState.AT_RISK, WeeklyCommitmentCalc.floorState(runsThisWeek = 0, floor = 3, daysLeftInclusive = 3))
    }

    @Test
    fun `floorState is IMPOSSIBLE when remaining runs exceed remaining days`() {
        assertEquals(FloorState.IMPOSSIBLE, WeeklyCommitmentCalc.floorState(runsThisWeek = 0, floor = 3, daysLeftInclusive = 2))
    }

    @Test
    fun `weekOutcome classifies broken, floor-met, and target-met weeks`() {
        assertEquals(WeekOutcome.BROKEN, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 2, floor = 3, target = 4))
        assertEquals(WeekOutcome.FLOOR_MET, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 3, floor = 3, target = 4))
        assertEquals(WeekOutcome.TARGET_MET, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 4, floor = 3, target = 4))
        assertEquals(WeekOutcome.TARGET_MET, WeeklyCommitmentCalc.weekOutcome(runsThisWeek = 6, floor = 3, target = 4))
    }

    @Test
    fun `consecutiveFloorIntactStreak counts from most-recent week until the first broken week`() {
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
