package com.suprxsidh.stride.data.calc

import java.time.LocalDate

/**
 * Feature B (completeness pass, spec §3): the 7-day rolling net-deficit card.
 *
 * Pure function — no Room/Flow dependency, so it's trivially unit-testable. Callers (e.g.
 * [com.suprxsidh.stride.ui.dashboard.DashboardViewModel]) supply the trailing logical dates to
 * sum over, the current soft budget, and a sparse map of buffered-consumed-kcal-by-date (as
 * returned by `FoodRepository.observeBufferedTotalsForRange`, which only contains rows for days
 * that actually have logged entries).
 */
object RollingDeficit {

    /**
     * Sum of (budgetKcal - consumed) across every date in [dates]. A date missing from
     * [consumedByDate] counts as zero consumed, i.e. a full day of budget banked — matching the
     * "rolling average is robust to gaps" precedent already established for weight in
     * SPEC.md §3.15, rather than skipping gap days entirely.
     *
     * Positive result = net deficit banked over the window; negative = net surplus (ate more
     * than budgeted, on average, across the window).
     */
    fun compute(
        dates: List<LocalDate>,
        budgetKcal: Int,
        consumedByDate: Map<LocalDate, Int>
    ): Int = dates.sumOf { date -> budgetKcal - (consumedByDate[date] ?: 0) }
}
