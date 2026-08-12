package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RunTypes.EXERCISE_TYPES was hoisted out of two private, independently-drifting copies in
 * WeeklyCommitmentRepository and ConsistencyRepository (final review finding). This pins the
 * shared constant's value; ConsistencyRepositoryTest and WeeklyCommitmentRepositoryTest already
 * exercise both repositories against exerciseType "56"/"57" and continue to pass unchanged,
 * which is the regression coverage that both call sites still agree on what counts as a run.
 */
class RunTypesTest {
    @Test
    fun `exercise types cover running and treadmill running`() {
        assertEquals(setOf("56", "57"), RunTypes.EXERCISE_TYPES)
    }
}
