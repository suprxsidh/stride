package com.suprxsidh.stride.data.calc

/**
 * Health Connect exercise-type codes that count as a "run" for weekly-commitment and
 * consistency purposes. Shared by [com.suprxsidh.stride.data.repository.WeeklyCommitmentRepository]
 * and [com.suprxsidh.stride.data.repository.ConsistencyRepository] so the definition of
 * "a run" can't drift between the two.
 */
object RunTypes {
    // EXERCISE_TYPE_RUNNING, EXERCISE_TYPE_RUNNING_TREADMILL
    val EXERCISE_TYPES = setOf("56", "57")
}
