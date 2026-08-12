package com.suprxsidh.deficit.ui.consistency

import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.repository.WeekSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers the final-review fixes to ConsistencyScreen's per-row summary line:
 *  - a grid row whose week has no matching [WeekSummary] (because
 *    ConsistencyRepository.weeklySummariesForMonth deliberately excludes weeks whose
 *    weekStart falls outside the target month) must render an explicit placeholder
 *    instead of silently showing nothing.
 *  - the rendered line must include "/7" denominators on both counts and the average
 *    deficit clause per SPEC §3.6, omitting the deficit clause when there's no data for it.
 */
class ConsistencyScreenTest {

    @Test
    fun `null summary (out-of-month weekStart) renders an explicit not-enough-data placeholder`() {
        assertEquals("Not enough data yet", weekSummaryText(null))
    }

    @Test
    fun `summary line includes 7-denominators for both ran and logged counts`() {
        val summary = WeekSummary(
            weekStart = LocalDate.of(2024, 1, 8),
            daysRan = 5,
            daysLogged = 6,
            avgDeficitKcal = 150.0,
            outcome = null
        )
        val text = weekSummaryText(summary)
        assertTrue("expected '5/7 ran' in: $text", text.contains("5/7 ran"))
        assertTrue("expected '6/7 logged' in: $text", text.contains("6/7 logged"))
    }

    @Test
    fun `summary line appends whole-number avg deficit when present`() {
        val summary = WeekSummary(
            weekStart = LocalDate.of(2024, 1, 8),
            daysRan = 5,
            daysLogged = 6,
            avgDeficitKcal = 149.6,
            outcome = null
        )
        assertEquals("5/7 ran · 6/7 logged · avg deficit ~150 kcal", weekSummaryText(summary))
    }

    @Test
    fun `summary line omits avg deficit clause when null instead of printing 'null'`() {
        val summary = WeekSummary(
            weekStart = LocalDate.of(2024, 1, 8),
            daysRan = 0,
            daysLogged = 0,
            avgDeficitKcal = null,
            outcome = null
        )
        val text = weekSummaryText(summary)
        assertEquals("0/7 ran · 0/7 logged", text)
        assertTrue(!text.contains("null"))
    }

    @Test
    fun `summary line still appends the outcome note after the avg deficit clause`() {
        val summary = WeekSummary(
            weekStart = LocalDate.of(2024, 1, 8),
            daysRan = 4,
            daysLogged = 4,
            avgDeficitKcal = 200.0,
            outcome = WeekOutcome.TARGET_MET
        )
        assertEquals("4/7 ran · 4/7 logged · avg deficit ~200 kcal · target met", weekSummaryText(summary))
    }
}
