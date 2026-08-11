# Phase 3: Weekly Commitment Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the app's central weekly-commitment loop — weekly run target/floor with floor-risk warning and streak tracking, data-driven motivation card, monthly consistency grid, adaptive (weekly-recomputed) calorie budget, and an auto-generated weekly review — per `SPEC.md` §3.6, §3.7 (minus its optional daily notification), §3.9, §3.10. Also folds in, first, a partial §4.1: a battery-optimization onboarding checklist item that protects the already-shipped Phase 2 Health Connect background sync from Vivo/OriginOS's aggressive app-killing, which has never been addressed or tested on-device.

**Architecture:** New pure-calculation objects (`WeekBoundary`, `WeeklyCommitmentCalc`, `AdaptiveBudgetCalc`, `MotivationLine`) mirror the existing `CalorieMath`/`DayBoundary`/`RollingAverage` style — fully unit-testable, no Android dependencies. Three new repositories (`WeeklyCommitmentRepository`, `AdaptiveBudgetRepository`, `WeeklyReviewRepository`) sit on top of the existing `ExerciseSessionDao`, `FoodEntryDao`, `WeighInDao`, following the existing manual-DI repository pattern (constructor-injected DAOs, no framework). A fourth, `ConsistencyRepository`, assembles per-day grid data from the same three DAOs. One new Room entity (`WeeklyReviewEntity`) stores generated weekly-review history; `AppSettingsEntity` gains four new fields (weekly target/floor, manual budget override, last-seen-review watermark) via the existing destructive-migration convention.

**Tech Stack:** No new external dependencies. Uses only what Phase 1/2 already brought in: Room, Kotlin coroutines/Flow, Compose Material 3, `java.time`. Battery-optimization detection uses `android.os.PowerManager` (framework API, no library).

## Global Constraints

- Package `com.suprxsidh.deficit`, manual DI via `AppContainer` (no Hilt), ViewModels via `viewModelFactory { initializer { ... } }` in each `*Screen.kt` — follow the exact existing pattern for every new ViewModel.
- Toolchain: every task's implementer MUST run `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` in their shell before the first `./gradlew` call (the wrapper's own bootstrap needs this on `PATH`/`JAVA_HOME` before Gradle ever reads `gradle.properties`). `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`.
- `DeficitDatabase` is currently at `version = 4` with only `fallbackToDestructiveMigration()`, no real `Migration` objects. This phase bumps it to `version = 5` for the new entity + `AppSettingsEntity` fields. Keep using `fallbackToDestructiveMigration()` — do not introduce real migrations. Nothing has ever been sideloaded to the Vivo X200T, so there is still no on-device data to lose.
- Reuse existing calc utilities, do not reimplement: `CalorieMath.bmr`/`.tdee`/`.softBudgetKcal` (for `AdaptiveBudgetCalc`), `DayBoundary.logicalDate` (everywhere "today" is computed — 3:00 AM boundary), `RollingAverage.sevenDayRollingAverage`/`WeighInPoint` (for the rolling-average weight `AdaptiveBudgetCalc` recomputes from).
- Test stack: JUnit4 4.13.2, Robolectric 4.13 (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`) for anything touching Room or Android framework classes; plain JUnit for pure calc classes (matches `CalorieMathTest`/`DayBoundaryTest` — no Robolectric needed there). `kotlinx-coroutines-test`'s `runTest` for suspend functions. Current baseline is 126/126 passing across 26 classes — every task in this plan adds tests, never removes or skips existing ones.
- **No daily streaks, anywhere.** The weekly run floor is the app's only commitment (SPEC.md §3.7). The "consecutive weeks floor intact" stat is a *weekly* streak — it is the sole exception and must never be described as a "streak" of days. Never introduce day-level streak language in any new UI copy.
- **Deliberate simplification — "a run" identification.** `ExerciseSessionEntity.exerciseType` stores `androidx.health.connect.client.records.ExerciseSessionRecord.exerciseType`'s `Int` constant stringified (see `HealthConnectDataSource.kt:49`), e.g. `"56"` for `EXERCISE_TYPE_RUNNING`. This phase counts a session toward the weekly run target/floor when its `exerciseType` matches `EXERCISE_TYPE_RUNNING` (56) **or** `EXERCISE_TYPE_RUNNING_TREADMILL` (57) — both are "a run" under SPEC.md §3.7's plain-language use of the word, and the user's watch/Samsung Health may tag a treadmill session distinctly. No other exercise type counts. `WeeklyCommitmentCalc`/`WeeklyCommitmentRepository` hold this as a small constant set, not a hardcoded single value, so a future exercise type can be added in one place.
- **Deliberate simplification — "under soft budget" and "average daily deficit".** The existing dashboard budget bar (`DashboardScreen.kt`) compares `todayBufferedTotal` directly against `profile.softBudgetKcal` — it does **not** add credited exercise calories back into the available budget (`kcalCredited` is shown as separate informational text only). This phase's consistency-grid "under budget" indicator and weekly-review "average daily deficit" use the exact same definition (`bufferedKcal <= softBudgetKcal` at the time; deficit = `softBudgetKcal - bufferedKcal`) for consistency with what the user already sees on the dashboard every day. Changing that combined-budget behavior is a separate, out-of-scope decision. "Average daily deficit" is averaged only over days that have at least one food entry that week (a day with zero logged entries is not a deficit data point, not a real fast).
- **Battery-optimization detection/deep-link uses no new permission.** `PowerManager.isIgnoringBatteryOptimizations(packageName)` requires no manifest permission. The onboarding step deep-links to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (the general system list where the user finds Deficit and disables optimization + separately enables Vivo's i Manager auto-start, which has no programmatic API) rather than requesting `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` directly — matches the existing HC setup step's "send the user to do it manually, detect after" pattern instead of adding a new permission this single-user app doesn't need.
- AlarmManager / `SCHEDULE_EXACT_ALARM` exact-alarm infrastructure is explicitly **not** part of this phase (there are no reminders yet to schedule) — do not add it speculatively.

---

## File Structure

**New files:**
```
app/src/main/java/com/suprxsidh/deficit/
├── data/
│   ├── calc/
│   │   ├── WeekBoundary.kt                    [Task 1]  — Monday-Sunday week math
│   │   ├── WeeklyCommitmentCalc.kt             [Task 1]  — floor-risk, broken-week, streak
│   │   ├── AdaptiveBudgetCalc.kt               [Task 2]  — weekly TDEE/budget recompute
│   │   └── MotivationLine.kt                   [Task 3]  — deterministic motivation copy
│   ├── db/
│   │   ├── entity/
│   │   │   └── WeeklyReviewEntity.kt           [Task 4]
│   │   └── dao/
│   │       └── WeeklyReviewDao.kt              [Task 4]
│   └── repository/
│       ├── WeeklyCommitmentRepository.kt       [Task 5]
│       ├── AdaptiveBudgetRepository.kt          [Task 6]
│       ├── WeeklyReviewRepository.kt            [Task 7]
│       └── ConsistencyRepository.kt             [Task 8]
├── system/
│   └── BatteryOptimization.kt                  [Task 12] — isIgnoringBatteryOptimizations + intent
└── ui/
    ├── dashboard/
    │   ├── WeeklyCommitmentCard.kt              [Task 10]
    │   ├── MotivationCard.kt                    [Task 10]
    │   └── WeeklyReviewCard.kt                  [Task 10]
    └── consistency/
        ├── ConsistencyScreen.kt                 [Task 11]
        └── ConsistencyViewModel.kt              [Task 11]

app/src/test/java/com/suprxsidh/deficit/
├── data/
│   ├── calc/
│   │   ├── WeekBoundaryTest.kt                  [Task 1]
│   │   ├── WeeklyCommitmentCalcTest.kt          [Task 1]
│   │   ├── AdaptiveBudgetCalcTest.kt            [Task 2]
│   │   └── MotivationLineTest.kt                [Task 3]
│   ├── db/dao/
│   │   └── WeeklyReviewDaoTest.kt               [Task 4]
│   └── repository/
│       ├── WeeklyCommitmentRepositoryTest.kt    [Task 5]
│       ├── AdaptiveBudgetRepositoryTest.kt       [Task 6]
│       ├── WeeklyReviewRepositoryTest.kt         [Task 7]
│       └── ConsistencyRepositoryTest.kt          [Task 8]
├── system/
│   └── BatteryOptimizationTest.kt               [Task 12]
└── ui/
    ├── dashboard/
    │   └── DashboardViewModelTest.kt (extend)    [Task 10]
    ├── consistency/
    │   └── ConsistencyViewModelTest.kt           [Task 11]
    ├── settings/
    │   └── SettingsViewModelTest.kt (extend)      [Task 9]
    └── onboarding/
        └── OnboardingViewModelTest.kt (extend)    [Task 12]
```

**Modified files:**
```
app/src/main/java/com/suprxsidh/deficit/
├── data/
│   ├── AppContainer.kt                          [Task 9]  — wire 4 new repositories
│   ├── db/
│   │   ├── DeficitDatabase.kt                    [Task 4]  — version 4→5, register entity/dao
│   │   ├── entity/AppSettingsEntity.kt            [Task 4]  — +5 fields
│   │   └── dao/FoodEntryDao.kt                    [Task 7]  — + getForDateRange(startDate, endDate)
│   └── repository/SettingsRepository.kt          [Task 4]  — getters/setters for the 5 new fields
├── ui/
│   ├── dashboard/
│   │   ├── DashboardViewModel.kt                 [Task 10]
│   │   └── DashboardScreen.kt                    [Task 10]
│   ├── settings/
│   │   ├── SettingsViewModel.kt                  [Task 9]
│   │   └── SettingsScreen.kt                     [Task 9]
│   ├── onboarding/
│   │   ├── OnboardingViewModel.kt                [Task 12]
│   │   └── OnboardingScreen.kt                   [Task 12]
│   └── nav/
│       ├── Routes.kt                              [Task 11] — + CONSISTENCY
│       └── DeficitNavHost.kt                      [Task 11]
└── MainActivity.kt                                [Task 11] — + Consistency bottom-nav tab
```

---

### Task 1: Week boundary + weekly commitment calc (pure functions)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/WeekBoundary.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/WeeklyCommitmentCalc.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/WeekBoundaryTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/WeeklyCommitmentCalcTest.kt`

**Interfaces:**
- Produces: `WeekBoundary.weekStart(date: LocalDate): LocalDate`, `WeekBoundary.weekEnd(date: LocalDate): LocalDate`, `WeekBoundary.daysLeftInclusive(date: LocalDate): Int` (days remaining in the Mon-Sun week, counting `date` itself — e.g. Thursday → 4).
- Produces: `enum class FloorState { OK, AT_RISK, IMPOSSIBLE }`, `enum class WeekOutcome { BROKEN, FLOOR_MET, TARGET_MET }`, `WeeklyCommitmentCalc.floorState(runsThisWeek: Int, floor: Int, daysLeftInclusive: Int): FloorState`, `WeeklyCommitmentCalc.weekOutcome(runsThisWeek: Int, floor: Int, target: Int): WeekOutcome`, `WeeklyCommitmentCalc.consecutiveFloorIntactStreak(completedWeekOutcomesMostRecentFirst: List<WeekOutcome>): Int`.
- Later tasks (5, 7, 10) consume all of the above directly — no other module computes week boundaries or floor state.

**Judgment call baked into this task (document as a comment, not just here):** SPEC.md §3.7 says "any run on any day counts equally" and defines floor-risk as `runsRemaining to reach floor > daysLeft − 1`. For that "mathematically impossible" language to mean anything, a "run" toward the target/floor must be a *day with at least one qualifying run*, not a raw session count — otherwise running twice in one day could satisfy the floor with days to spare, and "impossible" would never truly trigger. `WeeklyCommitmentRepository` (Task 5) will count *distinct days* with a qualifying run, and pass that count into `floorState`/`weekOutcome` here. These calc functions themselves stay agnostic to how the run count was derived — that keeps this file a pure, trivially-testable function of three integers.

- [ ] **Step 1: Write the failing test for `WeekBoundary`**

```kotlin
package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekBoundaryTest {
    // 2024-01-01 is a Monday (fixed reference point, unambiguous to verify).
    @Test
    fun `weekStart and weekEnd for a Monday`() {
        val monday = LocalDate.of(2024, 1, 1)
        assertEquals(monday, WeekBoundary.weekStart(monday))
        assertEquals(LocalDate.of(2024, 1, 7), WeekBoundary.weekEnd(monday))
    }

    @Test
    fun `weekStart and weekEnd for a mid-week day resolve to the same Mon-Sun span`() {
        val thursday = LocalDate.of(2024, 1, 4)
        assertEquals(LocalDate.of(2024, 1, 1), WeekBoundary.weekStart(thursday))
        assertEquals(LocalDate.of(2024, 1, 7), WeekBoundary.weekEnd(thursday))
    }

    @Test
    fun `weekStart and weekEnd for a Sunday resolve to the week that is ending`() {
        val sunday = LocalDate.of(2024, 1, 7)
        assertEquals(LocalDate.of(2024, 1, 1), WeekBoundary.weekStart(sunday))
        assertEquals(sunday, WeekBoundary.weekEnd(sunday))
    }

    @Test
    fun `daysLeftInclusive counts today through Sunday`() {
        assertEquals(7, WeekBoundary.daysLeftInclusive(LocalDate.of(2024, 1, 1))) // Monday
        assertEquals(4, WeekBoundary.daysLeftInclusive(LocalDate.of(2024, 1, 4))) // Thursday
        assertEquals(1, WeekBoundary.daysLeftInclusive(LocalDate.of(2024, 1, 7))) // Sunday
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.WeekBoundaryTest"`
Expected: FAIL — `WeekBoundary` is unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.data.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object WeekBoundary {
    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun weekEnd(date: LocalDate): LocalDate = weekStart(date).plusDays(6)

    fun daysLeftInclusive(date: LocalDate): Int =
        ChronoUnit.DAYS.between(date, weekEnd(date)).toInt() + 1
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.WeekBoundaryTest"`
Expected: PASS

- [ ] **Step 5: Write the failing test for `WeeklyCommitmentCalc`**

```kotlin
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
```

- [ ] **Step 6: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.WeeklyCommitmentCalcTest"`
Expected: FAIL — `WeeklyCommitmentCalc`/`FloorState`/`WeekOutcome` unresolved.

- [ ] **Step 7: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.data.calc

enum class FloorState { OK, AT_RISK, IMPOSSIBLE }

enum class WeekOutcome { BROKEN, FLOOR_MET, TARGET_MET }

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

    fun weekOutcome(runsThisWeek: Int, floor: Int, target: Int): WeekOutcome = when {
        runsThisWeek < floor -> WeekOutcome.BROKEN
        runsThisWeek >= target -> WeekOutcome.TARGET_MET
        else -> WeekOutcome.FLOOR_MET
    }

    fun consecutiveFloorIntactStreak(completedWeekOutcomesMostRecentFirst: List<WeekOutcome>): Int {
        var streak = 0
        for (outcome in completedWeekOutcomesMostRecentFirst) {
            if (outcome == WeekOutcome.BROKEN) break
            streak++
        }
        return streak
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.WeeklyCommitmentCalcTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/calc/WeekBoundary.kt \
        app/src/main/java/com/suprxsidh/deficit/data/calc/WeeklyCommitmentCalc.kt \
        app/src/test/java/com/suprxsidh/deficit/data/calc/WeekBoundaryTest.kt \
        app/src/test/java/com/suprxsidh/deficit/data/calc/WeeklyCommitmentCalcTest.kt
git commit -m "Task 1: week boundary math and weekly floor/target calc"
```

---

### Task 2: Adaptive budget calc (pure function)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/AdaptiveBudgetCalc.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/AdaptiveBudgetCalcTest.kt`

**Interfaces:**
- Consumes: `CalorieMath.bmr(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Double`, `CalorieMath.tdee(bmr: Double): Double`, `CalorieMath.softBudgetKcal(tdee: Double): Int` (all already exist, unmodified).
- Produces: `AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvgWeightKg: Double, heightCm: Double, age: Int, sex: Sex): Int`.
- Consumed by: `AdaptiveBudgetRepository` (Task 6), which supplies the rolling-average weight and decides whether to apply the result or defer to a manual override.

SPEC.md §3.9: "Recompute TDEE weekly from the 7-day rolling average weight ... keeping the −500 deficit and the 1,500 kcal floor." This is exactly `CalorieMath`'s existing formula chain, just fed the rolling average instead of a single weigh-in — no new math, only a named entry point so callers don't have to remember to chain three functions correctly.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveBudgetCalcTest {
    @Test
    fun `recompute matches the existing BMR-TDEE-budget chain`() {
        val expectedBmr = CalorieMath.bmr(weightKg = 78.0, heightCm = 178.0, age = 29, sex = Sex.MALE)
        val expectedBudget = CalorieMath.softBudgetKcal(CalorieMath.tdee(expectedBmr))

        assertEquals(
            expectedBudget,
            AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvgWeightKg = 78.0, heightCm = 178.0, age = 29, sex = Sex.MALE)
        )
    }

    @Test
    fun `a lower rolling-average weight lowers the budget but never below 1500`() {
        val higher = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvgWeightKg = 90.0, heightCm = 178.0, age = 29, sex = Sex.MALE)
        val lower = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvgWeightKg = 60.0, heightCm = 178.0, age = 29, sex = Sex.MALE)

        assertTrue(lower < higher)
        assertTrue(lower >= 1500)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.AdaptiveBudgetCalcTest"`
Expected: FAIL — `AdaptiveBudgetCalc` unresolved. (Note: the second test needs `import org.junit.Assert.assertTrue` — add it alongside `assertEquals`.)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.data.calc

object AdaptiveBudgetCalc {
    fun recomputeSoftBudgetKcal(rollingAvgWeightKg: Double, heightCm: Double, age: Int, sex: Sex): Int {
        val bmr = CalorieMath.bmr(rollingAvgWeightKg, heightCm, age, sex)
        val tdee = CalorieMath.tdee(bmr)
        return CalorieMath.softBudgetKcal(tdee)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.AdaptiveBudgetCalcTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/calc/AdaptiveBudgetCalc.kt \
        app/src/test/java/com/suprxsidh/deficit/data/calc/AdaptiveBudgetCalcTest.kt
git commit -m "Task 2: adaptive budget recompute from rolling-average weight"
```

---

### Task 3: Motivation line generator (pure function)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/MotivationLine.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/MotivationLineTest.kt`

**Interfaces:**
- Produces: `enum class MotivationCategory { FLOOR_AT_RISK, WEEKLY_PROGRESS, FLOOR_INTACT_STREAK, WEIGHT_TREND, TOTAL_RUNS }`, `data class MotivationInputs(floorAtRisk: Boolean, runsThisWeek: Int, weeklyTarget: Int, daysLeftInclusive: Int, floorIntactStreakWeeks: Int, rollingWeightChangeKg: Double?, totalRunsLogged: Int)`, `MotivationLine.dailyLine(inputs: MotivationInputs, previousCategory: MotivationCategory?): Pair<MotivationCategory, String>`.
- Consumed by: `WeeklyCommitmentRepository` (Task 5) assembles `MotivationInputs`; `DashboardViewModel` (Task 10) calls `dailyLine` with yesterday's category (persisted as `AppSettingsEntity.lastMotivationCategory`, added in Task 4) and stores the returned category back via `SettingsRepository` for tomorrow's call.

**Design note — resolving an ordering tension in SPEC.md §3.7:** the spec lists "floor at risk" as the *first* priority for the motivation card's stat pool, but also says "rotate deterministically; never show the same stat two days in a row if another is available" — applied literally across all five tiers, that means a genuinely still-at-risk floor could get skipped in favor of a lower-priority stat on the second day. That's fine and intentional: the floor-risk *warning banner* (§3.7's separate "switch the weekly widget to a warning state" requirement, built in Task 10 alongside the week-state display) is the always-visible, never-skipped channel for that urgency — the motivation card is supplementary color commentary, never the sole place urgent floor-risk information lives. This task follows the spec's rotation rule literally and uniformly across all five categories.

- [ ] **Step 1: Write the failing test**

```kotlin
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.MotivationLineTest"`
Expected: FAIL — `MotivationLine`/`MotivationInputs`/`MotivationCategory` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
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
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.calc.MotivationLineTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/calc/MotivationLine.kt \
        app/src/test/java/com/suprxsidh/deficit/data/calc/MotivationLineTest.kt
git commit -m "Task 3: deterministic data-driven motivation line generator"
```

---

### Task 4: WeeklyReviewEntity + Dao, AppSettingsEntity fields, DB version bump

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/WeeklyReviewEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/WeeklyReviewDao.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/AppSettingsEntity.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/DeficitDatabase.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/repository/SettingsRepository.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/WeeklyReviewDaoTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/SettingsRepositoryTest.kt` (extend existing file)

**Interfaces:**
- Produces: `WeeklyReviewEntity(id: Long = 0, weekStartDate: String, runsCompleted: Int, runFloor: Int, runTarget: Int, daysLogged: Int, avgDailyDeficitKcal: Double?, rollingWeightChangeKg: Double?, budgetAdjustedToKcal: Int?, outcome: String, generatedAt: Long)` — `weekStartDate` is the Monday ISO date from `WeekBoundary.weekStart` (Task 1); `outcome` stores a `WeekOutcome` (Task 1) enum name.
- Produces: `WeeklyReviewDao.upsertByWeekStart(review: WeeklyReviewEntity)`, `WeeklyReviewDao.getByWeekStart(weekStartDate: String): WeeklyReviewEntity?`, `WeeklyReviewDao.observeAll(): Flow<List<WeeklyReviewEntity>>` (ordered by `weekStartDate` DESC — most recent first, for the history list).
- Modifies `AppSettingsEntity` — adds `weeklyRunTarget: Int = 4`, `weeklyRunFloor: Int = 3`, `manualBudgetOverrideKcal: Int? = null`, `lastReviewSeenWeekStart: String? = null`, `lastMotivationCategory: String? = null`.
- Produces on `SettingsRepository`: `observeWeeklyRunTarget(): Flow<Int>`, `observeWeeklyRunFloor(): Flow<Int>`, `suspend fun setWeeklyRunTarget(target: Int)`, `suspend fun setWeeklyRunFloor(floor: Int)`, `suspend fun getManualBudgetOverrideKcal(): Int?`, `suspend fun setManualBudgetOverrideKcal(kcal: Int?)`, `suspend fun getLastReviewSeenWeekStart(): String?`, `suspend fun setLastReviewSeenWeekStart(weekStart: String)`, `suspend fun getLastMotivationCategory(): String?`, `suspend fun setLastMotivationCategory(category: String)`.
- Consumed by: `WeeklyCommitmentRepository`'s callers (Task 10, target/floor), `AdaptiveBudgetRepository` (Task 6, override), `WeeklyReviewRepository` (Task 7, seen watermark), `MotivationLine`'s caller (Task 10, last category), and the Settings screen (Task 9, target/floor/override UI).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyReviewDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: WeeklyReviewDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.weeklyReviewDao()
    }

    @After
    fun tearDown() = db.close()

    private fun review(weekStartDate: String, runsCompleted: Int = 3) = WeeklyReviewEntity(
        weekStartDate = weekStartDate,
        runsCompleted = runsCompleted,
        runFloor = 3,
        runTarget = 4,
        daysLogged = 5,
        avgDailyDeficitKcal = 420.0,
        rollingWeightChangeKg = -0.5,
        budgetAdjustedToKcal = null,
        outcome = "FLOOR_MET",
        generatedAt = 1_000L
    )

    @Test
    fun `getByWeekStart returns null when absent`() = runTest {
        assertNull(dao.getByWeekStart("2024-01-01"))
    }

    @Test
    fun `upsertByWeekStart inserts then replaces the same week`() = runTest {
        dao.upsertByWeekStart(review("2024-01-01", runsCompleted = 2))
        dao.upsertByWeekStart(review("2024-01-01", runsCompleted = 4))

        val stored = dao.getByWeekStart("2024-01-01")
        assertEquals(4, stored?.runsCompleted)
    }

    @Test
    fun `observeAll orders by weekStartDate descending`() = runTest {
        dao.upsertByWeekStart(review("2024-01-01"))
        dao.upsertByWeekStart(review("2024-01-08"))

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("2024-01-08", all[0].weekStartDate)
        assertEquals("2024-01-01", all[1].weekStartDate)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.db.dao.WeeklyReviewDaoTest"`
Expected: FAIL — `WeeklyReviewEntity`/`WeeklyReviewDao`/`db.weeklyReviewDao()` unresolved.

- [ ] **Step 3: Create the entity**

```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_review", indices = [Index(value = ["weekStartDate"], unique = true)])
data class WeeklyReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartDate: String,
    val runsCompleted: Int,
    val runFloor: Int,
    val runTarget: Int,
    val daysLogged: Int,
    val avgDailyDeficitKcal: Double?,
    val rollingWeightChangeKg: Double?,
    val budgetAdjustedToKcal: Int?,
    val outcome: String,
    val generatedAt: Long
)
```

- [ ] **Step 4: Create the DAO**

```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertByWeekStart(review: WeeklyReviewEntity)

    @Query("SELECT * FROM weekly_review WHERE weekStartDate = :weekStartDate LIMIT 1")
    suspend fun getByWeekStart(weekStartDate: String): WeeklyReviewEntity?

    @Query("SELECT * FROM weekly_review ORDER BY weekStartDate DESC")
    fun observeAll(): Flow<List<WeeklyReviewEntity>>
}
```

Note: `@Insert(onConflict = REPLACE)` on the unique `weekStartDate` index deletes-and-reinserts on conflict, which changes the row's `id` — that's fine here, nothing holds a foreign key to `WeeklyReviewEntity.id`, and the test above only asserts on `weekStartDate`/`runsCompleted`, not `id`.

- [ ] **Step 5: Add the five new fields to `AppSettingsEntity`**

```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val geminiApiKey: String? = null,
    val weeklyRunTarget: Int = 4,
    val weeklyRunFloor: Int = 3,
    val manualBudgetOverrideKcal: Int? = null,
    val lastReviewSeenWeekStart: String? = null,
    val lastMotivationCategory: String? = null
)
```

- [ ] **Step 6: Register the entity/DAO and bump the DB version**

In `DeficitDatabase.kt`, add `WeeklyReviewEntity::class` to the `entities` array, bump `version = 4` to `version = 5`, and add:

```kotlin
    abstract fun weeklyReviewDao(): WeeklyReviewDao
```

alongside the other `abstract fun ...Dao()` declarations. Add the matching imports (`com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity`, `com.suprxsidh.deficit.data.db.dao.WeeklyReviewDao`). Leave `fallbackToDestructiveMigration()` as the only migration strategy — do not write a real `Migration`.

- [ ] **Step 7: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.db.dao.WeeklyReviewDaoTest"`
Expected: PASS

- [ ] **Step 8: Write the failing test for the new `SettingsRepository` accessors**

Add to `app/src/test/java/com/suprxsidh/deficit/data/repository/SettingsRepositoryTest.kt` (this file already exists from Phase 2 — add these as new `@Test` methods in the existing class, don't replace it):

```kotlin
    @Test
    fun `weekly run target and floor default to 4 and 3, and are settable`() = runTest {
        assertEquals(4, repo.observeWeeklyRunTarget().first())
        assertEquals(3, repo.observeWeeklyRunFloor().first())

        repo.setWeeklyRunTarget(5)
        repo.setWeeklyRunFloor(4)

        assertEquals(5, repo.observeWeeklyRunTarget().first())
        assertEquals(4, repo.observeWeeklyRunFloor().first())
    }

    @Test
    fun `manual budget override is null by default and round-trips through set and clear`() = runTest {
        assertEquals(null, repo.getManualBudgetOverrideKcal())

        repo.setManualBudgetOverrideKcal(1900)
        assertEquals(1900, repo.getManualBudgetOverrideKcal())

        repo.setManualBudgetOverrideKcal(null)
        assertEquals(null, repo.getManualBudgetOverrideKcal())
    }

    @Test
    fun `last-seen review watermark and last motivation category round-trip`() = runTest {
        assertEquals(null, repo.getLastReviewSeenWeekStart())
        repo.setLastReviewSeenWeekStart("2024-01-08")
        assertEquals("2024-01-08", repo.getLastReviewSeenWeekStart())

        assertEquals(null, repo.getLastMotivationCategory())
        repo.setLastMotivationCategory("WEEKLY_PROGRESS")
        assertEquals("WEEKLY_PROGRESS", repo.getLastMotivationCategory())
    }
```

(Uses this test file's existing `repo`/`db` fields and `import org.junit.Assert.assertEquals` / `kotlinx.coroutines.test.runTest` / `kotlinx.coroutines.flow.first` — add any of those imports not already present.)

- [ ] **Step 9: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.SettingsRepositoryTest"`
Expected: FAIL — the new methods don't exist on `SettingsRepository` yet.

- [ ] **Step 10: Add the accessors to `SettingsRepository`**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.db.dao.AppSettingsDao
import com.suprxsidh.deficit.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingsDao) {
    suspend fun getGeminiApiKey(): String? = dao.get()?.geminiApiKey

    fun observeGeminiApiKey(): Flow<String?> = dao.observe().map { it?.geminiApiKey }

    suspend fun setGeminiApiKey(key: String?) {
        upsertCopy { it.copy(geminiApiKey = key) }
    }

    fun observeWeeklyRunTarget(): Flow<Int> = dao.observe().map { it?.weeklyRunTarget ?: 4 }

    fun observeWeeklyRunFloor(): Flow<Int> = dao.observe().map { it?.weeklyRunFloor ?: 3 }

    suspend fun setWeeklyRunTarget(target: Int) = upsertCopy { it.copy(weeklyRunTarget = target) }

    suspend fun setWeeklyRunFloor(floor: Int) = upsertCopy { it.copy(weeklyRunFloor = floor) }

    suspend fun getManualBudgetOverrideKcal(): Int? = dao.get()?.manualBudgetOverrideKcal

    suspend fun setManualBudgetOverrideKcal(kcal: Int?) = upsertCopy { it.copy(manualBudgetOverrideKcal = kcal) }

    suspend fun getLastReviewSeenWeekStart(): String? = dao.get()?.lastReviewSeenWeekStart

    suspend fun setLastReviewSeenWeekStart(weekStart: String) = upsertCopy { it.copy(lastReviewSeenWeekStart = weekStart) }

    suspend fun getLastMotivationCategory(): String? = dao.get()?.lastMotivationCategory

    suspend fun setLastMotivationCategory(category: String) = upsertCopy { it.copy(lastMotivationCategory = category) }

    private suspend fun upsertCopy(mutate: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = dao.get() ?: AppSettingsEntity(id = 1)
        dao.upsert(mutate(current))
    }
}
```

Note: this replaces the old `setGeminiApiKey`'s `dao.upsert(AppSettingsEntity(id = 1, geminiApiKey = key))` (which silently reset every other field to its default on every call) with the new `upsertCopy` helper that reads-then-copies — that old implementation would otherwise wipe `weeklyRunTarget`/`weeklyRunFloor`/etc. back to defaults the next time someone changes the Gemini key. Run the full existing `SettingsRepositoryTest` (not just the new methods) after this change to confirm the Gemini-key tests still pass with the new read-modify-write behavior.

- [ ] **Step 11: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.SettingsRepositoryTest"`
Expected: PASS (all methods in the file, old and new)

- [ ] **Step 12: Run the full test suite to confirm the version bump didn't break anything**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test`
Expected: PASS (all 126 pre-existing tests plus the new ones — `AppSettingsEntity`'s new fields all have defaults, so no existing test constructing it without them breaks).

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/db/entity/WeeklyReviewEntity.kt \
        app/src/main/java/com/suprxsidh/deficit/data/db/dao/WeeklyReviewDao.kt \
        app/src/main/java/com/suprxsidh/deficit/data/db/entity/AppSettingsEntity.kt \
        app/src/main/java/com/suprxsidh/deficit/data/db/DeficitDatabase.kt \
        app/src/main/java/com/suprxsidh/deficit/data/repository/SettingsRepository.kt \
        app/src/test/java/com/suprxsidh/deficit/data/db/dao/WeeklyReviewDaoTest.kt \
        app/src/test/java/com/suprxsidh/deficit/data/repository/SettingsRepositoryTest.kt
git commit -m "Task 4: weekly review entity/DAO, settings fields + accessors, DB v4->v5"
```

---

### Task 5: WeeklyCommitmentRepository

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/WeeklyCommitmentRepository.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/WeeklyCommitmentRepositoryTest.kt`

**Interfaces:**
- Consumes: `ExerciseSessionDao.observeAll(): Flow<List<ExerciseSessionEntity>>` (already exists), `DayBoundary.logicalDate`, `WeekBoundary.weekStart`/`weekEnd`/`daysLeftInclusive`, `WeeklyCommitmentCalc.floorState`/`weekOutcome`/`consecutiveFloorIntactStreak` (Task 1).
- Produces: `data class WeeklyCommitmentState(runsThisWeek: Int, target: Int, floor: Int, daysLeftInclusive: Int, floorState: FloorState)`, `WeeklyCommitmentRepository(exerciseSessionDao: ExerciseSessionDao, clock: () -> LocalDateTime = { LocalDateTime.now() })` with `observeCurrentWeekState(target: Int, floor: Int): Flow<WeeklyCommitmentState>`, `suspend fun runsCompletedForWeek(weekStart: LocalDate): Int`, `suspend fun consecutiveFloorIntactStreakWeeks(target: Int, floor: Int): Int`, `suspend fun totalRunDaysAllTime(): Int` (distinct qualifying-run days ever — feeds `MotivationLine`'s lowest-priority tier in Task 10).
- Consumed by: `DashboardViewModel` (Task 10) calls `observeCurrentWeekState` and `consecutiveFloorIntactStreakWeeks`; `WeeklyReviewRepository` (Task 7) calls `runsCompletedForWeek` for the week that just ended.

**Judgment call (continues Task 1's note):** a session counts as "a run" when `exerciseType` is `"56"` (`EXERCISE_TYPE_RUNNING`) or `"57"` (`EXERCISE_TYPE_RUNNING_TREADMILL`) — the two Health Connect running types. Distinct **days** with a qualifying run count toward the target/floor (not raw session count) — two runs in one day still count as 1, matching the "impossible" framing in `WeeklyCommitmentCalc`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyCommitmentRepositoryTest {
    private lateinit var db: DeficitDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun runSession(date: String, hcRecordId: String, exerciseType: String = "56") = ExerciseSessionEntity(
        hcRecordId = hcRecordId, date = date, exerciseType = exerciseType, startTimeEpochMs = 0L,
        durationMin = 30, distanceM = 5000.0, avgPaceSecPerKm = null, avgHr = null, maxHr = null,
        kcalReal = 300, kcalCredited = 150
    )

    @Test
    fun `observeCurrentWeekState counts distinct running days this week, ignores non-running types`() = runTest {
        // 2024-01-01 is a Monday.
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-1")) // Monday, running
        db.exerciseSessionDao().insert(runSession("2024-01-02", "hc-2")) // Tuesday, second run same-ish week
        db.exerciseSessionDao().insert(runSession("2024-01-02", "hc-3")) // Tuesday again -> still 1 day
        db.exerciseSessionDao().insert(runSession("2024-01-03", "hc-4", exerciseType = "8")) // e.g. cycling -> ignored
        db.exerciseSessionDao().insert(runSession("2023-12-25", "hc-5")) // previous week -> ignored

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao(), clock = { LocalDateTime.of(2024, 1, 4, 8, 0) }) // Thursday
        val state = repo.observeCurrentWeekState(target = 4, floor = 3).first()

        assertEquals(2, state.runsThisWeek)
        assertEquals(4, state.daysLeftInclusive) // Thu..Sun
        assertEquals(FloorState.OK, state.floorState) // 1 more run needed, 4 days left
    }

    @Test
    fun `runsCompletedForWeek counts a completed week independent of the current clock`() = runTest {
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-1"))
        db.exerciseSessionDao().insert(runSession("2024-01-05", "hc-2"))

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao())
        assertEquals(2, repo.runsCompletedForWeek(LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun `consecutiveFloorIntactStreakWeeks stops at the first broken week going backward`() = runTest {
        // Week of 2024-01-15 (current, in progress, excluded from the streak): no runs yet.
        // Week of 2024-01-08: 3 runs -> floor met.
        db.exerciseSessionDao().insert(runSession("2024-01-08", "hc-1"))
        db.exerciseSessionDao().insert(runSession("2024-01-09", "hc-2"))
        db.exerciseSessionDao().insert(runSession("2024-01-10", "hc-3"))
        // Week of 2024-01-01: only 1 run -> broken.
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-4"))

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao(), clock = { LocalDateTime.of(2024, 1, 15, 8, 0) })
        assertEquals(1, repo.consecutiveFloorIntactStreakWeeks(target = 4, floor = 3))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepositoryTest"`
Expected: FAIL — `WeeklyCommitmentRepository` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.calc.WeekBoundary
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.calc.WeeklyCommitmentCalc
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

data class WeeklyCommitmentState(
    val runsThisWeek: Int,
    val target: Int,
    val floor: Int,
    val daysLeftInclusive: Int,
    val floorState: FloorState
)

class WeeklyCommitmentRepository(
    private val exerciseSessionDao: ExerciseSessionDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    companion object {
        private val RUN_EXERCISE_TYPES = setOf("56", "57") // EXERCISE_TYPE_RUNNING, EXERCISE_TYPE_RUNNING_TREADMILL
        private const val STREAK_LOOKBACK_WEEKS = 104 // 2 years; the streak scan stops at the first broken week anyway
    }

    private fun runDaysInRange(sessions: List<ExerciseSessionEntity>, start: LocalDate, end: LocalDate): Set<LocalDate> =
        sessions.asSequence()
            .filter { it.exerciseType in RUN_EXERCISE_TYPES }
            .map { LocalDate.parse(it.date) }
            .filter { !it.isBefore(start) && !it.isAfter(end) }
            .toSet()

    fun observeCurrentWeekState(target: Int, floor: Int): Flow<WeeklyCommitmentState> =
        exerciseSessionDao.observeAll().map { sessions ->
            val today = DayBoundary.logicalDate(clock())
            val start = WeekBoundary.weekStart(today)
            val end = WeekBoundary.weekEnd(today)
            val runsThisWeek = runDaysInRange(sessions, start, end).size
            val daysLeft = WeekBoundary.daysLeftInclusive(today)
            WeeklyCommitmentState(
                runsThisWeek = runsThisWeek,
                target = target,
                floor = floor,
                daysLeftInclusive = daysLeft,
                floorState = WeeklyCommitmentCalc.floorState(runsThisWeek, floor, daysLeft)
            )
        }

    suspend fun runsCompletedForWeek(weekStart: LocalDate): Int {
        val sessions = exerciseSessionDao.observeAll().first()
        return runDaysInRange(sessions, weekStart, weekStart.plusDays(6)).size
    }

    suspend fun consecutiveFloorIntactStreakWeeks(target: Int, floor: Int): Int {
        val sessions = exerciseSessionDao.observeAll().first()
        val currentWeekStart = WeekBoundary.weekStart(DayBoundary.logicalDate(clock()))
        val outcomes = mutableListOf<WeekOutcome>()
        var weekStart = currentWeekStart.minusWeeks(1) // most recent COMPLETED week
        for (i in 0 until STREAK_LOOKBACK_WEEKS) {
            val runsThisWeek = runDaysInRange(sessions, weekStart, weekStart.plusDays(6)).size
            val outcome = WeeklyCommitmentCalc.weekOutcome(runsThisWeek, floor, target)
            outcomes += outcome
            if (outcome == WeekOutcome.BROKEN) break
            weekStart = weekStart.minusWeeks(1)
        }
        return WeeklyCommitmentCalc.consecutiveFloorIntactStreak(outcomes)
    }

    suspend fun totalRunDaysAllTime(): Int {
        val sessions = exerciseSessionDao.observeAll().first()
        return sessions.filter { it.exerciseType in RUN_EXERCISE_TYPES }.map { it.date }.toSet().size
    }
}
```

Also add this test to `WeeklyCommitmentRepositoryTest` alongside the others in Step 1 (append inside the class, before the closing brace):

```kotlin
    @Test
    fun `totalRunDaysAllTime counts distinct running days across all history`() = runTest {
        db.exerciseSessionDao().insert(runSession("2023-06-01", "hc-1"))
        db.exerciseSessionDao().insert(runSession("2023-06-01", "hc-2")) // same day -> still 1
        db.exerciseSessionDao().insert(runSession("2024-01-01", "hc-3"))
        db.exerciseSessionDao().insert(runSession("2024-01-02", "hc-4", exerciseType = "8")) // ignored

        val repo = WeeklyCommitmentRepository(db.exerciseSessionDao())
        assertEquals(2, repo.totalRunDaysAllTime())
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/repository/WeeklyCommitmentRepository.kt \
        app/src/test/java/com/suprxsidh/deficit/data/repository/WeeklyCommitmentRepositoryTest.kt
git commit -m "Task 5: weekly commitment repository (run-day counting, floor state, streak)"
```

---

### Task 6: AdaptiveBudgetRepository

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/AdaptiveBudgetRepository.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/AdaptiveBudgetRepositoryTest.kt`

**Interfaces:**
- Consumes: `UserProfileDao.get(): UserProfileEntity?` / `.upsert(profile: UserProfileEntity)`, `WeighInDao.observeAll(): Flow<List<WeighInEntity>>`, `RollingAverage.sevenDayRollingAverage`/`WeighInPoint`, `AdaptiveBudgetCalc.recomputeSoftBudgetKcal` (Task 2), `SettingsRepository.getManualBudgetOverrideKcal()`/`setManualBudgetOverrideKcal()` (Task 4).
- Produces: `AdaptiveBudgetRepository(userProfileDao: UserProfileDao, weighInDao: WeighInDao, settingsRepository: SettingsRepository)` with `suspend fun recomputeIfNoOverride(): Int?` (returns the new budget if it changed and was applied, `null` if skipped or unchanged) and `suspend fun setManualOverride(kcal: Int)` / `suspend fun clearManualOverride()`.
- Consumed by: `WeeklyReviewRepository` (Task 7) calls `recomputeIfNoOverride()` once per generated review; the Settings screen (Task 9) calls `setManualOverride`/`clearManualOverride`.

**Design note — single source of truth for the operative budget.** Rather than introduce a second "effective budget" lookup that the dashboard would need to start calling instead of `profile.softBudgetKcal`, a manual override *writes straight through* to `UserProfileEntity.softBudgetKcal` (the field the dashboard already reads — unchanged from Phase 1) and is remembered separately in `AppSettingsEntity.manualBudgetOverrideKcal` only so the weekly auto-recompute knows to skip itself while it's set. Clearing the override immediately triggers a fresh recompute so the budget doesn't stay stuck at the old override value until the next Monday.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveBudgetRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repo: AdaptiveBudgetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository(db.appSettingsDao())
        repo = AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedProfile(weightKg: Double, softBudgetKcal: Int) {
        db.userProfileDao().upsert(
            UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = weightKg, age = 29, sex = Sex.MALE.name,
                goalWeightKg = weightKg - 10, softBudgetKcal = softBudgetKcal, createdAt = 0L
            )
        )
    }

    @Test
    fun `recompute is a no-op with no profile or no weigh-ins`() = runTest {
        assertNull(repo.recomputeIfNoOverride())
        seedProfile(weightKg = 80.0, softBudgetKcal = 1850)
        assertNull(repo.recomputeIfNoOverride()) // still no weigh-ins to average
    }

    @Test
    fun `recompute updates the profile's budget when the rolling average has moved it`() = runTest {
        seedProfile(weightKg = 90.0, softBudgetKcal = 2000) // stale, set as if from a much higher starting weight
        for (i in 0..6) {
            db.weighInDao().upsert(WeighInEntity(date = "2024-01-0${i + 1}", weightKg = 78.0))
        }

        val newBudget = repo.recomputeIfNoOverride()
        assertEquals(newBudget, db.userProfileDao().get()?.softBudgetKcal)
        assert(newBudget != null && newBudget < 2000)
    }

    @Test
    fun `recompute is skipped while a manual override is set`() = runTest {
        seedProfile(weightKg = 90.0, softBudgetKcal = 2000)
        for (i in 0..6) {
            db.weighInDao().upsert(WeighInEntity(date = "2024-01-0${i + 1}", weightKg = 78.0))
        }
        settingsRepository.setManualBudgetOverrideKcal(1900)

        assertNull(repo.recomputeIfNoOverride())
        assertEquals(2000, db.userProfileDao().get()?.softBudgetKcal) // untouched, override still governs
    }

    @Test
    fun `setManualOverride writes straight through to the profile budget, clear triggers a fresh recompute`() = runTest {
        seedProfile(weightKg = 78.0, softBudgetKcal = 1850)
        for (i in 0..6) {
            db.weighInDao().upsert(WeighInEntity(date = "2024-01-0${i + 1}", weightKg = 78.0))
        }

        repo.setManualOverride(1700)
        assertEquals(1700, db.userProfileDao().get()?.softBudgetKcal)
        assertEquals(1700, settingsRepository.getManualBudgetOverrideKcal())

        repo.clearManualOverride()
        assertNull(settingsRepository.getManualBudgetOverrideKcal())
        assertEquals(1850, db.userProfileDao().get()?.softBudgetKcal) // recomputed from the still-78kg rolling average
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepositoryTest"`
Expected: FAIL — `AdaptiveBudgetRepository` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.AdaptiveBudgetCalc
import com.suprxsidh.deficit.data.calc.RollingAverage
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.calc.WeighInPoint
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class AdaptiveBudgetRepository(
    private val userProfileDao: UserProfileDao,
    private val weighInDao: WeighInDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun recomputeIfNoOverride(): Int? {
        if (settingsRepository.getManualBudgetOverrideKcal() != null) return null
        val profile = userProfileDao.get() ?: return null
        val points = weighInDao.observeAll().first().map { WeighInPoint(LocalDate.parse(it.date), it.weightKg) }
        val rollingAvg = RollingAverage.sevenDayRollingAverage(points).lastOrNull()?.second ?: return null
        val newBudget = AdaptiveBudgetCalc.recomputeSoftBudgetKcal(rollingAvg, profile.heightCm, profile.age, Sex.valueOf(profile.sex))
        if (newBudget == profile.softBudgetKcal) return null
        userProfileDao.upsert(profile.copy(softBudgetKcal = newBudget))
        return newBudget
    }

    suspend fun setManualOverride(kcal: Int) {
        settingsRepository.setManualBudgetOverrideKcal(kcal)
        val profile = userProfileDao.get() ?: return
        userProfileDao.upsert(profile.copy(softBudgetKcal = kcal))
    }

    suspend fun clearManualOverride() {
        settingsRepository.setManualBudgetOverrideKcal(null)
        recomputeIfNoOverride()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/repository/AdaptiveBudgetRepository.kt \
        app/src/test/java/com/suprxsidh/deficit/data/repository/AdaptiveBudgetRepositoryTest.kt
git commit -m "Task 6: adaptive budget repository with manual-override precedence"
```

---

### Task 7: WeeklyReviewRepository

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/FoodEntryDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/WeeklyReviewRepository.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/FoodEntryDaoTest.kt` (extend existing file)
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/WeeklyReviewRepositoryTest.kt`

**Interfaces:**
- Adds to `FoodEntryDao`: `suspend fun getForDateRange(startDate: String, endDate: String): List<FoodEntryEntity>` (inclusive, ISO date strings — dates sort lexicographically, so a plain `BETWEEN` is correct).
- Consumes: `WeeklyCommitmentRepository.runsCompletedForWeek` (Task 5), `AdaptiveBudgetRepository.recomputeIfNoOverride` (Task 6), `WeeklyReviewDao` (Task 4), `RollingAverage.sevenDayRollingAverage`/`WeighInPoint`, `WeekBoundary`, `WeeklyCommitmentCalc.weekOutcome` (Task 1), `SettingsRepository`'s target/floor/watermark accessors (Task 4).
- Produces: `WeeklyReviewRepository(weeklyReviewDao, weeklyCommitmentRepository, adaptiveBudgetRepository, foodEntryDao, weighInDao, userProfileDao, settingsRepository, clock)` with `suspend fun generateForCompletedWeekIfDue(): WeeklyReviewEntity?`, `fun observeHistory(): Flow<List<WeeklyReviewEntity>>`, `suspend fun unseenReview(): WeeklyReviewEntity?`, `suspend fun markReviewSeen(weekStartDate: String)`.
- Consumed by: `DashboardViewModel` (Task 10) calls `generateForCompletedWeekIfDue()` once on load (mirrors how `MainActivity`/`DashboardViewModel` already trigger Health Connect sync opportunistically on open) and `unseenReview()`/`markReviewSeen()` for the first-open card; a future history screen would call `observeHistory()` (out of scope to build in this phase, but the method exists and is tested since `generateForCompletedWeekIfDue` writes into the same table it reads).

**Judgment calls:**
1. **Day-1 guard.** Without a check, opening the app for the very first time would generate a review for the week *before* the user ever installed it — always a "broken" week (0 runs, 0 days logged) that has nothing to do with real usage. `generateForCompletedWeekIfDue` fetches the profile first and skips entirely (returns `null`, writes no row) if the completed week ended before the profile's `createdAt` date.
2. **Rolling-average weight change for the week** = the 7-day rolling average as of that week's Sunday, minus the rolling average as of the day before that week's Monday (the last known values on/before each boundary, since weigh-ins rarely land exactly on those two calendar dates). This captures how much the trend moved *during* that specific week, distinct from `WeightRepository.observeTotalChangeSinceStart()`'s all-time figure.
3. **Average daily deficit** is grouped by day, using `profile.softBudgetKcal - dayTotal`, averaged only across days with at least one logged entry — consistent with the Global Constraints note (matches the existing dashboard bar's definition, no exercise-credit add-back).

- [ ] **Step 1: Write the failing test for the `FoodEntryDao` range query**

Add to `app/src/test/java/com/suprxsidh/deficit/data/db/dao/FoodEntryDaoTest.kt` (existing file — add as a new `@Test`):

```kotlin
    @Test
    fun `getForDateRange returns entries within the inclusive range, ordered by date then time`() = runTest {
        dao.insert(FoodEntryEntity(date = "2024-01-01", name = "a", rawKcal = 100, bufferedKcal = 110, source = "manual", offBarcode = null, loggedAt = 1L))
        dao.insert(FoodEntryEntity(date = "2024-01-03", name = "b", rawKcal = 200, bufferedKcal = 220, source = "manual", offBarcode = null, loggedAt = 2L))
        dao.insert(FoodEntryEntity(date = "2024-01-10", name = "c", rawKcal = 300, bufferedKcal = 330, source = "manual", offBarcode = null, loggedAt = 3L)) // outside range

        val inRange = dao.getForDateRange("2024-01-01", "2024-01-07")
        assertEquals(2, inRange.size)
        assertEquals("a", inRange[0].name)
        assertEquals("b", inRange[1].name)
    }
```

(Add `import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity` if the file doesn't already import it under that exact name — it should, since this file already constructs `FoodEntryEntity` for its other tests.)

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.db.dao.FoodEntryDaoTest"`
Expected: FAIL — `getForDateRange` unresolved.

- [ ] **Step 3: Add the query to `FoodEntryDao`**

```kotlin
    @Query("SELECT * FROM food_entry WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, loggedAt ASC")
    suspend fun getForDateRange(startDate: String, endDate: String): List<FoodEntryEntity>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.db.dao.FoodEntryDaoTest"`
Expected: PASS

- [ ] **Step 5: Write the failing test for `WeeklyReviewRepository`**

```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyReviewRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repo: WeeklyReviewRepository

    // 2024-01-08 is a Monday; "now" is the following Monday, so the week of 2024-01-08..14 just ended.
    private val clock = { LocalDateTime.of(2024, 1, 15, 8, 0) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository(db.appSettingsDao())
        val weeklyCommitmentRepository = WeeklyCommitmentRepository(db.exerciseSessionDao(), clock)
        val adaptiveBudgetRepository = AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository)
        repo = WeeklyReviewRepository(
            db.weeklyReviewDao(), weeklyCommitmentRepository, adaptiveBudgetRepository,
            db.foodEntryDao(), db.weighInDao(), db.userProfileDao(), settingsRepository, clock
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedProfile(createdAt: Long) {
        db.userProfileDao().upsert(
            UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name,
                goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = createdAt
            )
        )
    }

    @Test
    fun `generates a review for the week that just ended, with runs, days logged, and deficit`() = runTest {
        seedProfile(createdAt = 0L) // long before the reviewed week, so the guard doesn't skip it
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-1", date = "2024-01-08", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-08", name = "lunch", rawKcal = 500, bufferedKcal = 550, source = "manual", offBarcode = null, loggedAt = 1L))
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-09", name = "dinner", rawKcal = 400, bufferedKcal = 440, source = "manual", offBarcode = null, loggedAt = 2L))

        val review = repo.generateForCompletedWeekIfDue()

        assertEquals("2024-01-08", review?.weekStartDate)
        assertEquals(1, review?.runsCompleted)
        assertEquals(2, review?.daysLogged)
        // avg of (1850-550) and (1850-440) = avg of 1300 and 1410
        assertEquals(1355.0, requireNotNull(review?.avgDailyDeficitKcal), 0.01)
        assertEquals(WeekOutcome.BROKEN.name, review?.outcome) // 1 run < default floor of 3
    }

    @Test
    fun `calling twice does not regenerate, returns the same stored review`() = runTest {
        seedProfile(createdAt = 0L)
        val first = repo.generateForCompletedWeekIfDue()
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-new", date = "2024-01-09", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        val second = repo.generateForCompletedWeekIfDue()
        assertEquals(first?.runsCompleted, second?.runsCompleted) // unaffected by the run added after generation
    }

    @Test
    fun `skips generation entirely for a week that predates onboarding`() = runTest {
        // Profile created 2024-01-14 (Sunday), inside the reviewed week -> the whole week predates the app.
        seedProfile(createdAt = LocalDateTime.of(2024, 1, 14, 9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertNull(repo.generateForCompletedWeekIfDue())
        assertEquals(0, db.weeklyReviewDao().observeAll().first().size)
    }

    @Test
    fun `unseenReview and markReviewSeen round-trip`() = runTest {
        seedProfile(createdAt = 0L)
        val generated = repo.generateForCompletedWeekIfDue()

        assertEquals(generated?.weekStartDate, repo.unseenReview()?.weekStartDate)
        repo.markReviewSeen(requireNotNull(generated).weekStartDate)
        assertNull(repo.unseenReview())
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.WeeklyReviewRepositoryTest"`
Expected: FAIL — `WeeklyReviewRepository` unresolved.

- [ ] **Step 7: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.RollingAverage
import com.suprxsidh.deficit.data.calc.WeekBoundary
import com.suprxsidh.deficit.data.calc.WeeklyCommitmentCalc
import com.suprxsidh.deficit.data.calc.WeighInPoint
import com.suprxsidh.deficit.data.db.dao.FoodEntryDao
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.dao.WeeklyReviewDao
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WeeklyReviewRepository(
    private val weeklyReviewDao: WeeklyReviewDao,
    private val weeklyCommitmentRepository: WeeklyCommitmentRepository,
    private val adaptiveBudgetRepository: AdaptiveBudgetRepository,
    private val foodEntryDao: FoodEntryDao,
    private val weighInDao: WeighInDao,
    private val userProfileDao: UserProfileDao,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private fun rollingAvgOnOrBefore(series: List<Pair<LocalDate, Double>>, date: LocalDate): Double? =
        series.filter { !it.first.isAfter(date) }.maxByOrNull { it.first }?.second

    private suspend fun rollingWeightChangeForWeek(weekStart: LocalDate, weekEnd: LocalDate): Double? {
        val points = weighInDao.observeAll().first().map { WeighInPoint(LocalDate.parse(it.date), it.weightKg) }
        val series = RollingAverage.sevenDayRollingAverage(points)
        val before = rollingAvgOnOrBefore(series, weekStart.minusDays(1))
        val after = rollingAvgOnOrBefore(series, weekEnd)
        return if (before == null || after == null) null else after - before
    }

    suspend fun generateForCompletedWeekIfDue(): WeeklyReviewEntity? {
        val profile = userProfileDao.get() ?: return null
        val today = DayBoundary.logicalDate(clock())
        val weekStart = WeekBoundary.weekStart(today).minusWeeks(1)
        val weekEnd = weekStart.plusDays(6)

        val createdAtDate = DayBoundary.logicalDate(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(profile.createdAt), ZoneId.systemDefault())
        )
        if (weekEnd.isBefore(createdAtDate)) return null

        val existing = weeklyReviewDao.getByWeekStart(weekStart.toString())
        if (existing != null) return existing

        val target = settingsRepository.observeWeeklyRunTarget().first()
        val floor = settingsRepository.observeWeeklyRunFloor().first()
        val runs = weeklyCommitmentRepository.runsCompletedForWeek(weekStart)

        val entries = foodEntryDao.getForDateRange(weekStart.toString(), weekEnd.toString())
        val byDay = entries.groupBy { it.date }
        val daysLogged = byDay.size
        val avgDeficit = if (daysLogged == 0) null else
            byDay.values.map { dayEntries -> profile.softBudgetKcal - dayEntries.sumOf { it.bufferedKcal } }.average()

        val rollingChange = rollingWeightChangeForWeek(weekStart, weekEnd)

        val budgetBefore = profile.softBudgetKcal
        val newBudget = adaptiveBudgetRepository.recomputeIfNoOverride()
        val budgetAdjustedTo = newBudget?.takeIf { it != budgetBefore }

        val outcome = WeeklyCommitmentCalc.weekOutcome(runs, floor, target)

        val review = WeeklyReviewEntity(
            weekStartDate = weekStart.toString(),
            runsCompleted = runs,
            runFloor = floor,
            runTarget = target,
            daysLogged = daysLogged,
            avgDailyDeficitKcal = avgDeficit,
            rollingWeightChangeKg = rollingChange,
            budgetAdjustedToKcal = budgetAdjustedTo,
            outcome = outcome.name,
            generatedAt = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        weeklyReviewDao.upsertByWeekStart(review)
        return review
    }

    fun observeHistory(): Flow<List<WeeklyReviewEntity>> = weeklyReviewDao.observeAll()

    suspend fun unseenReview(): WeeklyReviewEntity? {
        val latest = weeklyReviewDao.observeAll().first().firstOrNull() ?: return null
        val lastSeen = settingsRepository.getLastReviewSeenWeekStart()
        return if (latest.weekStartDate != lastSeen) latest else null
    }

    suspend fun markReviewSeen(weekStartDate: String) = settingsRepository.setLastReviewSeenWeekStart(weekStartDate)
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.WeeklyReviewRepositoryTest"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/db/dao/FoodEntryDao.kt \
        app/src/main/java/com/suprxsidh/deficit/data/repository/WeeklyReviewRepository.kt \
        app/src/test/java/com/suprxsidh/deficit/data/db/dao/FoodEntryDaoTest.kt \
        app/src/test/java/com/suprxsidh/deficit/data/repository/WeeklyReviewRepositoryTest.kt
git commit -m "Task 7: weekly review generation with day-1 guard and unseen-card tracking"
```

---

### Task 8: ConsistencyRepository

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/ConsistencyRepository.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/ConsistencyRepositoryTest.kt`

**Interfaces:**
- Consumes: `ExerciseSessionDao.observeAll()`, `FoodEntryDao.getForDateRange` (Task 7), `UserProfileDao.get()`, `WeekBoundary.weekStart` (Task 1), `WeeklyCommitmentCalc.weekOutcome` (Task 1).
- Produces: `data class DayConsistency(date: LocalDate, ran: Boolean, loggedFood: Boolean, underBudget: Boolean, deficitKcal: Double?)`, `data class WeekSummary(weekStart: LocalDate, daysRan: Int, daysLogged: Int, avgDeficitKcal: Double?, outcome: WeekOutcome?)`, `ConsistencyRepository(exerciseSessionDao, foodEntryDao, userProfileDao)` with `suspend fun dailyConsistencyForMonth(month: YearMonth): List<DayConsistency>` and `suspend fun weeklySummariesForMonth(month: YearMonth, target: Int, floor: Int, today: LocalDate): List<WeekSummary>`.
- Consumed by: `ConsistencyViewModel` (Task 11).

**Judgment call — only fully-elapsed weeks get an outcome.** SPEC.md §3.7 says a completed week below floor is marked broken; a week still in progress hasn't failed yet, so `WeekSummary.outcome` is `null` (not `BROKEN`) for any week whose end date is on or after the caller-supplied `today` — the grid UI (Task 11) renders no broken/highlight marker for a week that hasn't finished. "Ran" reuses the same running-type filter as Task 5 (`"56"`/`"57"`), consistent with the weekly floor's own definition of a run.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsistencyRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var repo: ConsistencyRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ConsistencyRepository(db.exerciseSessionDao(), db.foodEntryDao(), db.userProfileDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `dailyConsistencyForMonth marks ran, loggedFood, and underBudget per day`() = runTest {
        db.userProfileDao().upsert(
            UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1800, createdAt = 0L)
        )
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-1", date = "2024-01-08", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-08", name = "under", rawKcal = 1600, bufferedKcal = 1600, source = "manual", offBarcode = null, loggedAt = 1L))
        db.foodEntryDao().insert(FoodEntryEntity(date = "2024-01-09", name = "over", rawKcal = 2000, bufferedKcal = 2000, source = "manual", offBarcode = null, loggedAt = 2L))

        val days = repo.dailyConsistencyForMonth(YearMonth.of(2024, 1))
        val jan8 = days.first { it.date == LocalDate.of(2024, 1, 8) }
        val jan9 = days.first { it.date == LocalDate.of(2024, 1, 9) }
        val jan10 = days.first { it.date == LocalDate.of(2024, 1, 10) }

        assertEquals(true, jan8.ran)
        assertEquals(true, jan8.loggedFood)
        assertEquals(true, jan8.underBudget)

        assertEquals(false, jan9.ran)
        assertEquals(true, jan9.loggedFood)
        assertEquals(false, jan9.underBudget)

        assertEquals(false, jan10.loggedFood)
        assertNull(jan10.deficitKcal)
    }

    @Test
    fun `weeklySummariesForMonth only assigns an outcome to fully-elapsed weeks`() = runTest {
        db.userProfileDao().upsert(
            UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1800, createdAt = 0L)
        )
        // Week of 2024-01-08..14: no runs at all -> would be BROKEN once elapsed.
        val summaries = repo.weeklySummariesForMonth(YearMonth.of(2024, 1), target = 4, floor = 3, today = LocalDate.of(2024, 1, 10))

        val currentWeek = summaries.first { it.weekStart == LocalDate.of(2024, 1, 8) }
        assertNull(currentWeek.outcome) // Jan 10 falls inside this week -> not yet judged

        val pastWeek = summaries.first { it.weekStart == LocalDate.of(2024, 1, 1) }
        assertEquals(WeekOutcome.BROKEN, pastWeek.outcome) // fully before "today" and had 0 runs
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.ConsistencyRepositoryTest"`
Expected: FAIL — `ConsistencyRepository` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.WeekBoundary
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.calc.WeeklyCommitmentCalc
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.FoodEntryDao
import com.suprxsidh.deficit.data.db.dao.UserProfileDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

data class DayConsistency(
    val date: LocalDate,
    val ran: Boolean,
    val loggedFood: Boolean,
    val underBudget: Boolean,
    val deficitKcal: Double?
)

data class WeekSummary(
    val weekStart: LocalDate,
    val daysRan: Int,
    val daysLogged: Int,
    val avgDeficitKcal: Double?,
    val outcome: WeekOutcome?
)

class ConsistencyRepository(
    private val exerciseSessionDao: ExerciseSessionDao,
    private val foodEntryDao: FoodEntryDao,
    private val userProfileDao: UserProfileDao
) {
    companion object {
        private val RUN_EXERCISE_TYPES = setOf("56", "57")
    }

    suspend fun dailyConsistencyForMonth(month: YearMonth): List<DayConsistency> {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val sessions = exerciseSessionDao.observeAll().first()
        val entries = foodEntryDao.getForDateRange(start.toString(), end.toString())
        val budget = userProfileDao.get()?.softBudgetKcal
        val runDays = sessions.filter { it.exerciseType in RUN_EXERCISE_TYPES }.map { LocalDate.parse(it.date) }.toSet()
        val entriesByDay = entries.groupBy { it.date }

        return (0 until end.dayOfMonth).map { offset ->
            val date = start.plusDays(offset.toLong())
            val dayEntries = entriesByDay[date.toString()].orEmpty()
            val loggedFood = dayEntries.isNotEmpty()
            val deficitInt = if (loggedFood && budget != null) budget - dayEntries.sumOf { it.bufferedKcal } else null
            DayConsistency(
                date = date,
                ran = date in runDays,
                loggedFood = loggedFood,
                underBudget = deficitInt != null && deficitInt >= 0,
                deficitKcal = deficitInt?.toDouble()
            )
        }
    }

    suspend fun weeklySummariesForMonth(month: YearMonth, target: Int, floor: Int, today: LocalDate): List<WeekSummary> {
        val days = dailyConsistencyForMonth(month)
        val currentWeekStart = WeekBoundary.weekStart(today)
        return days.groupBy { WeekBoundary.weekStart(it.date) }
            .toSortedMap()
            .map { (weekStart, weekDays) ->
                val daysRan = weekDays.count { it.ran }
                val logged = weekDays.filter { it.loggedFood }
                val weekEnd = weekStart.plusDays(6)
                val outcome = if (weekEnd.isBefore(currentWeekStart)) {
                    WeeklyCommitmentCalc.weekOutcome(daysRan, floor, target)
                } else {
                    null
                }
                WeekSummary(
                    weekStart = weekStart,
                    daysRan = daysRan,
                    daysLogged = logged.size,
                    avgDeficitKcal = logged.mapNotNull { it.deficitKcal }.takeIf { it.isNotEmpty() }?.average(),
                    outcome = outcome
                )
            }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.data.repository.ConsistencyRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/repository/ConsistencyRepository.kt \
        app/src/test/java/com/suprxsidh/deficit/data/repository/ConsistencyRepositoryTest.kt
git commit -m "Task 8: consistency repository for the monthly grid and weekly summary row"
```

---

### Task 9: Wire AppContainer + Settings screen (weekly target/floor, budget override)

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/AppContainer.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/settings/SettingsViewModelTest.kt` (extend existing file)

**Interfaces:**
- Consumes: `WeeklyCommitmentRepository` (Task 5), `AdaptiveBudgetRepository` (Task 6), `WeeklyReviewRepository` (Task 7), `ConsistencyRepository` (Task 8) — all constructed here for the first time and exposed as `AppContainer` fields, mirroring the existing `val xRepository = XRepository(...)` style.
- Produces on `SettingsViewModel`: `weeklyRunTarget: StateFlow<Int>`, `weeklyRunFloor: StateFlow<Int>`, `manualBudgetOverrideKcal: StateFlow<Int?>`, `fun saveWeeklyRunTarget(target: Int)`, `fun saveWeeklyRunFloor(floor: Int)`, `fun saveManualBudgetOverride(kcal: Int)`, `fun clearManualBudgetOverride()`.
- Consumed by: `SettingsScreen` (this task, UI); `DashboardViewModel` (Task 10) reads `app.container.weeklyCommitmentRepository`/`.weeklyReviewRepository` directly from `AppContainer`, same pattern `DashboardScreen` already uses for `app.container.healthConnectRepository`.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/com/suprxsidh/deficit/ui/settings/SettingsViewModelTest.kt` (existing file — the `setUp()` below replaces the existing one to add the new dependency; the existing Gemini-key test is unchanged):

```kotlin
    private lateinit var adaptiveBudgetRepository: com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).build()
        val settingsRepository = SettingsRepository(db.appSettingsDao())
        adaptiveBudgetRepository = com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository(
            db.userProfileDao(), db.weighInDao(), settingsRepository
        )
        viewModel = SettingsViewModel(settingsRepository, adaptiveBudgetRepository)
    }

    @Test
    fun `weekly target and floor default to 4 and 3, saving updates observed state`() = runTest {
        backgroundScope.launch { viewModel.weeklyRunTarget.collect {} }
        backgroundScope.launch { viewModel.weeklyRunFloor.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, viewModel.weeklyRunTarget.value)
        assertEquals(3, viewModel.weeklyRunFloor.value)

        viewModel.saveWeeklyRunTarget(5)
        viewModel.saveWeeklyRunFloor(4)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.weeklyRunTarget.value)
        assertEquals(4, viewModel.weeklyRunFloor.value)
    }

    @Test
    fun `saving and clearing the manual budget override updates observed state`() = runTest {
        db.userProfileDao().upsert(
            com.suprxsidh.deficit.data.db.entity.UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, age = 29,
                sex = com.suprxsidh.deficit.data.calc.Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L
            )
        )
        backgroundScope.launch { viewModel.manualBudgetOverrideKcal.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.manualBudgetOverrideKcal.value)

        viewModel.saveManualBudgetOverride(1700)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1700, viewModel.manualBudgetOverrideKcal.value)

        viewModel.clearManualBudgetOverride()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.manualBudgetOverrideKcal.value)
    }
```

(Also delete the old duplicate `@Before fun setUp()` further down in the file so there is only the one above; keep the existing `saveGeminiApiKey` test as-is.)

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.settings.SettingsViewModelTest"`
Expected: FAIL — `SettingsViewModel` doesn't take a second constructor argument yet, and the new members don't exist.

- [ ] **Step 3: Update `SettingsViewModel`**

```kotlin
package com.suprxsidh.deficit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val adaptiveBudgetRepository: AdaptiveBudgetRepository
) : ViewModel() {

    val geminiApiKey: StateFlow<String?> =
        settingsRepository.observeGeminiApiKey().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weeklyRunTarget: StateFlow<Int> =
        settingsRepository.observeWeeklyRunTarget().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 4)

    val weeklyRunFloor: StateFlow<Int> =
        settingsRepository.observeWeeklyRunFloor().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    private val _manualBudgetOverrideKcal = MutableStateFlow<Int?>(null)
    val manualBudgetOverrideKcal: StateFlow<Int?> = _manualBudgetOverrideKcal.asStateFlow()

    init {
        viewModelScope.launch {
            _manualBudgetOverrideKcal.value = settingsRepository.getManualBudgetOverrideKcal()
        }
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(key.trim().ifBlank { null }) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(null) }
    }

    fun saveWeeklyRunTarget(target: Int) {
        viewModelScope.launch { settingsRepository.setWeeklyRunTarget(target) }
    }

    fun saveWeeklyRunFloor(floor: Int) {
        viewModelScope.launch { settingsRepository.setWeeklyRunFloor(floor) }
    }

    fun saveManualBudgetOverride(kcal: Int) {
        viewModelScope.launch {
            adaptiveBudgetRepository.setManualOverride(kcal)
            _manualBudgetOverrideKcal.value = kcal
        }
    }

    fun clearManualBudgetOverride() {
        viewModelScope.launch {
            adaptiveBudgetRepository.clearManualOverride()
            _manualBudgetOverrideKcal.value = null
        }
    }
}
```

- [ ] **Step 4: Update the `viewModelFactory` call site in `SettingsScreen`**

In `SettingsScreen.kt`, change:
```kotlin
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app.container.settingsRepository) }
    })
```
to:
```kotlin
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app.container.settingsRepository, app.container.adaptiveBudgetRepository) }
    })
```

- [ ] **Step 5: Add weekly-goal and budget-override sections to `SettingsScreen`**

Add below the existing Gemini API key section (same file, same `Column`):

```kotlin
        Spacer(Modifier.height(32.dp))
        Text("Weekly run goal", style = MaterialTheme.typography.titleMedium)
        Text("Target and hard floor for the Monday-Sunday week. Any run on any day counts equally.")
        Spacer(Modifier.height(12.dp))

        val target by viewModel.weeklyRunTarget.collectAsState()
        val floor by viewModel.weeklyRunFloor.collectAsState()
        var targetInput by remember(target) { mutableStateOf(target.toString()) }
        var floorInput by remember(floor) { mutableStateOf(floor.toString()) }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = targetInput, onValueChange = { targetInput = it },
                label = { Text("Target") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = floorInput, onValueChange = { floorInput = it },
                label = { Text("Floor") }, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            targetInput.toIntOrNull()?.let { viewModel.saveWeeklyRunTarget(it) }
            floorInput.toIntOrNull()?.let { viewModel.saveWeeklyRunFloor(it) }
        }) { Text("Save weekly goal") }

        Spacer(Modifier.height(32.dp))
        Text("Calorie budget override", style = MaterialTheme.typography.titleMedium)
        Text("Manually set the daily budget. Overrides the automatic weekly recompute until cleared.")
        Spacer(Modifier.height(12.dp))

        val override by viewModel.manualBudgetOverrideKcal.collectAsState()
        var overrideInput by remember(override) { mutableStateOf(override?.toString() ?: "") }

        OutlinedTextField(
            value = overrideInput, onValueChange = { overrideInput = it },
            label = { Text("Daily budget (kcal)") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = { overrideInput.toIntOrNull()?.let { viewModel.saveManualBudgetOverride(it) } }) {
                Text("Save override")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                viewModel.clearManualBudgetOverride()
                overrideInput = ""
            }) { Text("Clear override") }
        }
```

- [ ] **Step 6: Add the four new repositories to `AppContainer`**

```kotlin
    val weeklyCommitmentRepository = WeeklyCommitmentRepository(database.exerciseSessionDao())
    val adaptiveBudgetRepository = AdaptiveBudgetRepository(database.userProfileDao(), database.weighInDao(), settingsRepository)
    val weeklyReviewRepository = WeeklyReviewRepository(
        weeklyReviewDao = database.weeklyReviewDao(),
        weeklyCommitmentRepository = weeklyCommitmentRepository,
        adaptiveBudgetRepository = adaptiveBudgetRepository,
        foodEntryDao = database.foodEntryDao(),
        weighInDao = database.weighInDao(),
        userProfileDao = database.userProfileDao(),
        settingsRepository = settingsRepository
    )
    val consistencyRepository = ConsistencyRepository(database.exerciseSessionDao(), database.foodEntryDao(), database.userProfileDao())
```
placed after the existing `val settingsRepository = ...` line (all four depend on it or on DAOs already available at that point), with matching imports (`com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepository`, `.AdaptiveBudgetRepository`, `.WeeklyReviewRepository`, `.ConsistencyRepository`).

- [ ] **Step 7: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.settings.SettingsViewModelTest"`
Expected: PASS

- [ ] **Step 8: Build the app to confirm `AppContainer` and `SettingsScreen` compile end-to-end**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/AppContainer.kt \
        app/src/main/java/com/suprxsidh/deficit/ui/settings/SettingsViewModel.kt \
        app/src/main/java/com/suprxsidh/deficit/ui/settings/SettingsScreen.kt \
        app/src/test/java/com/suprxsidh/deficit/ui/settings/SettingsViewModelTest.kt
git commit -m "Task 9: wire weekly-commitment repositories, settings UI for target/floor/override"
```

---

### Task 10: Dashboard UI — weekly commitment, motivation, and weekly review card

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardScreen.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/WeeklyCommitmentCard.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/MotivationCard.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/WeeklyReviewCard.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModelTest.kt` (extend existing file)

**Interfaces:**
- Consumes: `WeeklyCommitmentRepository` (Task 5), `WeeklyReviewRepository` (Task 7), `SettingsRepository`'s target/floor/motivation-category accessors (Task 4), `MotivationLine`/`MotivationInputs`/`MotivationCategory` (Task 3), `WeightRepository.observeTotalChangeSinceStart()` (already exists).
- Produces on `DashboardViewModel`: `weeklyCommitmentState: StateFlow<WeeklyCommitmentState?>`, `motivationLine: StateFlow<String?>`, `unseenWeeklyReview: StateFlow<WeeklyReviewEntity?>`, `fun dismissWeeklyReview()`.
- `DashboardViewModel`'s constructor grows to take `weeklyCommitmentRepository: WeeklyCommitmentRepository`, `weeklyReviewRepository: WeeklyReviewRepository`, `settingsRepository: SettingsRepository` (inserted before the existing trailing `clock` parameter) — every existing call site (`DashboardScreen`, `DashboardViewModelTest`) must be updated.

- [ ] **Step 1: Write the failing test**

Update `app/src/test/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModelTest.kt`'s `buildViewModel` helper (existing file) to build and pass the three new dependencies:

```kotlin
    private fun buildViewModel(
        clock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 10, 12, 0) },
        healthConnectAvailability: Int = HealthConnectClient.SDK_AVAILABLE,
        hasPermissions: suspend () -> Boolean = { true }
    ): DashboardViewModel {
        val settingsRepository = com.suprxsidh.deficit.data.repository.SettingsRepository(db.appSettingsDao())
        val weeklyCommitmentRepository = com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepository(db.exerciseSessionDao(), clock)
        val adaptiveBudgetRepository = com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository)
        val weeklyReviewRepository = com.suprxsidh.deficit.data.repository.WeeklyReviewRepository(
            db.weeklyReviewDao(), weeklyCommitmentRepository, adaptiveBudgetRepository,
            db.foodEntryDao(), db.weighInDao(), db.userProfileDao(), settingsRepository, clock
        )
        return DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao(), clock = clock),
            UserProfileRepository(db.userProfileDao(), db.weighInDao()),
            WeightRepository(db.weighInDao()),
            HealthConnectRepository(NoOpHealthDataSource(), db.exerciseSessionDao(), db.syncStateDao(), db.weighInDao(), clock),
            healthConnectAvailability,
            hasPermissions,
            weeklyCommitmentRepository,
            weeklyReviewRepository,
            settingsRepository,
            clock
        )
    }
```

Add these new tests to the same file (append inside the class):

```kotlin
    @Test
    fun `weeklyCommitmentState reflects this week's run days against the default target and floor`() = runTest(testDispatcher) {
        // 2026-08-10 is the Monday of the week the fixture clock sits in.
        healthDao.insert(
            ExerciseSessionEntity(
                hcRecordId = "hc-1", date = "2026-08-10", exerciseType = "56", startTimeEpochMs = 1L,
                durationMin = 30, distanceM = 5000.0, avgPaceSecPerKm = 360.0, avgHr = 150, maxHr = 170, kcalReal = 350, kcalCredited = 175
            )
        )
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
        backgroundScope.launch { viewModel.weeklyCommitmentState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.weeklyCommitmentState.value
        assertEquals(1, state?.runsThisWeek)
        assertEquals(4, state?.target)
        assertEquals(3, state?.floor)
    }

    @Test
    fun `motivationLine is populated after load`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("0/4 runs this week · 7 days left.", viewModel.motivationLine.value)
    }

    @Test
    fun `unseenWeeklyReview surfaces a freshly generated review, dismissing clears it`() = runTest(testDispatcher) {
        db.userProfileDao().upsert(
            com.suprxsidh.deficit.data.db.entity.UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name,
                goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L
            )
        )
        // "Now" is Monday 2024-01-15 -> the week of 2024-01-08..14 just ended.
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2024, 1, 15, 8, 0) })
        testDispatcher.scheduler.advanceUntilIdle()

        val review = viewModel.unseenWeeklyReview.value
        assertEquals("2024-01-08", review?.weekStartDate)

        viewModel.dismissWeeklyReview()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.unseenWeeklyReview.value)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel`'s constructor doesn't accept the three new arguments yet, and the new members don't exist.

- [ ] **Step 3: Update `DashboardViewModel`**

```kotlin
package com.suprxsidh.deficit.ui.dashboard

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.calc.MotivationCategory
import com.suprxsidh.deficit.data.calc.MotivationInputs
import com.suprxsidh.deficit.data.calc.MotivationLine
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import com.suprxsidh.deficit.data.repository.FoodRepository
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import com.suprxsidh.deficit.data.repository.SettingsRepository
import com.suprxsidh.deficit.data.repository.UserProfileRepository
import com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepository
import com.suprxsidh.deficit.data.repository.WeeklyCommitmentState
import com.suprxsidh.deficit.data.repository.WeeklyReviewRepository
import com.suprxsidh.deficit.data.repository.WeightRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    private val weightRepository: WeightRepository,
    private val healthConnectRepository: HealthConnectRepository?,
    private val healthConnectAvailability: Int,
    private val hasHealthConnectPermissions: suspend () -> Boolean,
    private val weeklyCommitmentRepository: WeeklyCommitmentRepository,
    private val weeklyReviewRepository: WeeklyReviewRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> =
        userProfileRepository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rollingAverageSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaysRun: StateFlow<ExerciseSessionEntity?> =
        (healthConnectRepository?.observeExerciseSessions() ?: flowOf(emptyList()))
            .map { sessions ->
                val today = DayBoundary.logicalDate(clock()).toString()
                sessions.filter { it.date == today }.maxByOrNull { it.startTimeEpochMs }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weeklyCommitmentState: StateFlow<WeeklyCommitmentState?> =
        settingsRepository.observeWeeklyRunTarget()
            .combine(settingsRepository.observeWeeklyRunFloor()) { target, floor -> target to floor }
            .flatMapLatest { (target, floor) -> weeklyCommitmentRepository.observeCurrentWeekState(target, floor) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _motivationLine = MutableStateFlow<String?>(null)
    val motivationLine: StateFlow<String?> = _motivationLine.asStateFlow()

    private val _unseenWeeklyReview = MutableStateFlow<WeeklyReviewEntity?>(null)
    val unseenWeeklyReview: StateFlow<WeeklyReviewEntity?> = _unseenWeeklyReview.asStateFlow()

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.UNAVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    init {
        viewModelScope.launch {
            _healthConnectStatus.value = try {
                when {
                    healthConnectAvailability != HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.UNAVAILABLE
                    !hasHealthConnectPermissions() -> HealthConnectStatus.PERMISSIONS_NEEDED
                    else -> HealthConnectStatus.OK
                }
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to read Health Connect permission state", e)
                HealthConnectStatus.PERMISSIONS_NEEDED
            }
        }

        viewModelScope.launch {
            try {
                weeklyReviewRepository.generateForCompletedWeekIfDue()
                _unseenWeeklyReview.value = weeklyReviewRepository.unseenReview()
            } catch (e: Exception) {
                // A DB or clock hiccup here must not block the rest of the dashboard from loading.
                Log.w("DashboardViewModel", "Failed to generate or check the weekly review", e)
            }
        }

        viewModelScope.launch {
            try {
                val target = settingsRepository.observeWeeklyRunTarget().first()
                val floor = settingsRepository.observeWeeklyRunFloor().first()
                val state = weeklyCommitmentRepository.observeCurrentWeekState(target, floor).first()
                val streak = weeklyCommitmentRepository.consecutiveFloorIntactStreakWeeks(target, floor)
                val totalRuns = weeklyCommitmentRepository.totalRunDaysAllTime()
                val weightChange = weightRepository.observeTotalChangeSinceStart().first()
                val previousCategory = settingsRepository.getLastMotivationCategory()
                    ?.let { runCatching { MotivationCategory.valueOf(it) }.getOrNull() }

                val inputs = MotivationInputs(
                    floorAtRisk = state.floorState != FloorState.OK,
                    runsThisWeek = state.runsThisWeek,
                    weeklyTarget = state.target,
                    daysLeftInclusive = state.daysLeftInclusive,
                    floorIntactStreakWeeks = streak,
                    rollingWeightChangeKg = weightChange,
                    totalRunsLogged = totalRuns
                )
                val (category, line) = MotivationLine.dailyLine(inputs, previousCategory)
                settingsRepository.setLastMotivationCategory(category.name)
                _motivationLine.value = line
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to compute the motivation line", e)
            }
        }
    }

    fun dismissWeeklyReview() {
        viewModelScope.launch {
            _unseenWeeklyReview.value?.let { weeklyReviewRepository.markReviewSeen(it.weekStartDate) }
            _unseenWeeklyReview.value = null
        }
    }
}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
```

- [ ] **Step 4: Update the `DashboardScreen` call site and add the three cards**

In `DashboardScreen.kt`, update the `viewModelFactory` block:

```kotlin
    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                app.container.foodRepository,
                app.container.userProfileRepository,
                app.container.weightRepository,
                app.container.healthConnectRepository,
                app.container.healthConnectAvailability,
                app.container::hasHealthConnectPermissions,
                app.container.weeklyCommitmentRepository,
                app.container.weeklyReviewRepository,
                app.container.settingsRepository
            )
        }
    })
```

Then, inside the `Column`, right after the existing `LinearProgressIndicator`/budget-bar block and before the `Text("Weight (7-day average)"...)` line, add:

```kotlin
        val unseenReview by viewModel.unseenWeeklyReview.collectAsState()
        unseenReview?.let { WeeklyReviewCard(it, onDismiss = viewModel::dismissWeeklyReview) }

        val weeklyState by viewModel.weeklyCommitmentState.collectAsState()
        weeklyState?.let { WeeklyCommitmentCard(it) }

        val motivationLine by viewModel.motivationLine.collectAsState()
        motivationLine?.let { MotivationCard(it) }
```

- [ ] **Step 5: Create `WeeklyCommitmentCard`**

```kotlin
package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.data.calc.FloorState
import com.suprxsidh.deficit.data.repository.WeeklyCommitmentState

@Composable
fun WeeklyCommitmentCard(state: WeeklyCommitmentState) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${state.runsThisWeek}/${state.target} runs this week · ${state.daysLeftInclusive} days left",
                style = MaterialTheme.typography.titleMedium
            )
            when (state.floorState) {
                FloorState.AT_RISK -> Text(
                    "Run today or tomorrow to protect your floor.",
                    color = MaterialTheme.colorScheme.error
                )
                FloorState.IMPOSSIBLE -> Text(
                    "This week's floor is out of reach. Refocus on next week.",
                    color = MaterialTheme.colorScheme.error
                )
                FloorState.OK -> {}
            }
        }
    }
}
```

- [ ] **Step 6: Create `MotivationCard`**

```kotlin
package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MotivationCard(line: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(line, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
```

- [ ] **Step 7: Create `WeeklyReviewCard`**

```kotlin
package com.suprxsidh.deficit.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.db.entity.WeeklyReviewEntity
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WeeklyReviewCard(review: WeeklyReviewEntity, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last week", style = MaterialTheme.typography.titleMedium)
            Text("${review.runsCompleted}/${review.runFloor} floor · ${review.runsCompleted}/${review.runTarget} target")
            Text("${review.daysLogged} days logged")
            review.avgDailyDeficitKcal?.let { Text("Average deficit: ${it.roundToInt()} kcal/day") }
            review.rollingWeightChangeKg?.let {
                val direction = if (it < 0) "down" else "up"
                Text("Rolling average $direction ${"%.1f".format(abs(it))} kg")
            }
            review.budgetAdjustedToKcal?.let {
                Text("Budget adjusted to $it — your body burns less as weight drops, this keeps the deficit real.")
            }
            if (review.outcome == WeekOutcome.BROKEN.name) {
                Text("Floor missed last week. Fresh week, fresh start.", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: PASS

- [ ] **Step 9: Build the app to confirm the Compose changes compile**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/dashboard/ \
        app/src/test/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Task 10: dashboard weekly commitment, motivation, and weekly-review cards"
```

---

### Task 11: Consistency screen + navigation wiring

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/consistency/ConsistencyViewModel.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/consistency/ConsistencyScreen.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/Routes.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/MainActivity.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/consistency/ConsistencyViewModelTest.kt`

**Interfaces:**
- Consumes: `ConsistencyRepository` (Task 8), `SettingsRepository`'s target/floor accessors (Task 4), `DayBoundary.logicalDate`.
- Produces: `ConsistencyViewModel(consistencyRepository, settingsRepository, clock)` with `month: StateFlow<YearMonth>`, `days: StateFlow<List<DayConsistency>>`, `weeks: StateFlow<List<WeekSummary>>`, `fun previousMonth()`, `fun nextMonth()`.
- Adds `Routes.CONSISTENCY = "consistency"`, registers it in `DeficitNavHost`, and adds a fourth `BottomDestination` ("Consistency", a calendar-style icon) to `MainActivity`'s `BOTTOM_DESTINATIONS`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.ui.consistency

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.calc.Sex
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.UserProfileEntity
import com.suprxsidh.deficit.data.repository.ConsistencyRepository
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsistencyViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: ConsistencyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).allowMainThreadQueries().build()
        db.userProfileDao().upsert(
            UserProfileEntity(heightCm = 178.0, weightKgAtStart = 80.0, age = 29, sex = Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L)
        )
        db.exerciseSessionDao().insert(
            ExerciseSessionEntity(hcRecordId = "hc-1", date = "2024-01-08", exerciseType = "56", startTimeEpochMs = 0L,
                durationMin = 30, distanceM = null, avgPaceSecPerKm = null, avgHr = null, maxHr = null, kcalReal = 300, kcalCredited = 150)
        )
        val settingsRepository = SettingsRepository(db.appSettingsDao())
        val consistencyRepository = ConsistencyRepository(db.exerciseSessionDao(), db.foodEntryDao(), db.userProfileDao())
        viewModel = ConsistencyViewModel(consistencyRepository, settingsRepository, clock = { LocalDateTime.of(2024, 1, 10, 8, 0) })
    }

    @After
    fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test
    fun `loads the current month on init, showing the seeded run day`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.days.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(YearMonth.of(2024, 1), viewModel.month.value)
        assertEquals(31, viewModel.days.value.size)
        assertEquals(true, viewModel.days.value.first { it.date.toString() == "2024-01-08" }.ran)
    }

    @Test
    fun `nextMonth and previousMonth reload for the adjacent month`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.days.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.nextMonth()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2024, 2), viewModel.month.value)
        assertEquals(29, viewModel.days.value.size) // 2024 is a leap year

        viewModel.previousMonth()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2024, 1), viewModel.month.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.consistency.ConsistencyViewModelTest"`
Expected: FAIL — `ConsistencyViewModel` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.ui.consistency

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.calc.DayBoundary
import com.suprxsidh.deficit.data.repository.ConsistencyRepository
import com.suprxsidh.deficit.data.repository.DayConsistency
import com.suprxsidh.deficit.data.repository.SettingsRepository
import com.suprxsidh.deficit.data.repository.WeekSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth

class ConsistencyViewModel(
    private val consistencyRepository: ConsistencyRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.from(DayBoundary.logicalDate(clock())))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _days = MutableStateFlow<List<DayConsistency>>(emptyList())
    val days: StateFlow<List<DayConsistency>> = _days.asStateFlow()

    private val _weeks = MutableStateFlow<List<WeekSummary>>(emptyList())
    val weeks: StateFlow<List<WeekSummary>> = _weeks.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val target = settingsRepository.observeWeeklyRunTarget().first()
                val floor = settingsRepository.observeWeeklyRunFloor().first()
                val today = DayBoundary.logicalDate(clock())
                _days.value = consistencyRepository.dailyConsistencyForMonth(_month.value)
                _weeks.value = consistencyRepository.weeklySummariesForMonth(_month.value, target, floor, today)
            } catch (e: Exception) {
                Log.w("ConsistencyViewModel", "Failed to load the consistency grid", e)
            }
        }
    }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
        load()
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
        load()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.consistency.ConsistencyViewModelTest"`
Expected: PASS

- [ ] **Step 5: Create `ConsistencyScreen`**

```kotlin
package com.suprxsidh.deficit.ui.consistency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.calc.WeekOutcome
import com.suprxsidh.deficit.data.repository.DayConsistency
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ConsistencyScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: ConsistencyViewModel = viewModel(factory = viewModelFactory {
        initializer { ConsistencyViewModel(app.container.consistencyRepository, app.container.settingsRepository) }
    })

    val month by viewModel.month.collectAsState()
    val days by viewModel.days.collectAsState()
    val weeks by viewModel.weeks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = viewModel::previousMonth) { Text("< Prev") }
            Text("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = viewModel::nextMonth) { Text("Next >") }
        }
        Spacer(Modifier.height(16.dp))

        val byWeekStart = days.groupBy { com.suprxsidh.deficit.data.calc.WeekBoundary.weekStart(it.date) }.toSortedMap()
        byWeekStart.forEach { (weekStart, weekDays) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                val byDate = weekDays.associateBy { it.date }
                for (offset in 0..6) {
                    val date = weekStart.plusDays(offset.toLong())
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        byDate[date]?.let { DayCell(it) } ?: (if (date.month == month.month) DayCell(DayConsistency(date, false, false, false, null)) else Unit)
                    }
                }
            }
            val summary = weeks.firstOrNull { it.weekStart == weekStart }
            summary?.let {
                val brokenNote = if (it.outcome == WeekOutcome.BROKEN) " · floor missed" else ""
                Text(
                    "${it.daysRan} ran · ${it.daysLogged} logged$brokenNote",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DayCell(day: DayConsistency) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
        Row {
            Dot(active = day.ran)
            Dot(active = day.loggedFood)
            Dot(active = day.underBudget)
        }
    }
}

@Composable
private fun Dot(active: Boolean) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .height(6.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
    )
}
```

Note: the empty-cell branch above (`date.month == month.month`) pads out-of-range days within the first/last week row for months that don't start on a Monday or end on a Sunday, without rendering a phantom `DayCell` for a day belonging to a different month.

- [ ] **Step 6: Add the route and nav entry**

In `Routes.kt`, add:
```kotlin
    const val CONSISTENCY = "consistency"
```

In `DeficitNavHost.kt`, add the import for `ConsistencyScreen` and register it:
```kotlin
        composable(Routes.CONSISTENCY) { ConsistencyScreen() }
```

- [ ] **Step 7: Add the bottom-nav tab**

In `MainActivity.kt`, add an icon import (`androidx.compose.material.icons.filled.DateRange`) and extend `BOTTOM_DESTINATIONS`:
```kotlin
private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.DASHBOARD, "Today", Icons.Default.Home),
    BottomDestination(Routes.FOOD_LOG, "Food", Icons.AutoMirrored.Filled.List),
    BottomDestination(Routes.WEIGHT, "Weight", Icons.Default.Info),
    BottomDestination(Routes.CONSISTENCY, "History", Icons.Default.DateRange)
)
```

- [ ] **Step 8: Build the app to confirm the Compose + navigation changes compile**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/consistency/ \
        app/src/main/java/com/suprxsidh/deficit/ui/nav/Routes.kt \
        app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt \
        app/src/main/java/com/suprxsidh/deficit/MainActivity.kt \
        app/src/test/java/com/suprxsidh/deficit/ui/consistency/ConsistencyViewModelTest.kt
git commit -m "Task 11: consistency grid screen, nav route, bottom-nav tab"
```

---

### Task 12: Battery-optimization onboarding step + dashboard status recheck-on-resume

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/system/BatteryOptimization.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/system/BatteryOptimizationTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardScreen.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModelTest.kt` (extend existing file)

**Interfaces:**
- Produces: `BatteryOptimization.isIgnoringBatteryOptimizations(context: Context): Boolean`, `BatteryOptimization.batterySettingsIntent(): Intent`.
- `DashboardViewModel`'s constructor gains a fourth lambda, `isIgnoringBatteryOptimizations: () -> Boolean` (inserted alongside `hasHealthConnectPermissions`, before `weeklyCommitmentRepository`), and its ad-hoc health-status-only `init` logic is replaced by a reusable `fun refreshDeviceStatuses()` that updates both `healthConnectStatus` and the new `batteryOptimizationIgnored: StateFlow<Boolean>`. `DashboardScreen` calls `refreshDeviceStatuses()` from a lifecycle `ON_RESUME` observer, so returning from the system battery-settings screen (or from Health Connect's settings) refreshes both banners — fixing, as a byproduct, Phase 2's parked Minor item that `healthConnectStatus` was "computed once and not rechecked after granting permission via the settings deep link."

**Verified against the actual Robolectric version in this project (4.13):** `org.robolectric.shadows.ShadowPowerManager` exposes `setIgnoringBatteryOptimizations(String packageName, boolean)` — confirmed by inspecting the class file in the resolved Gradle dependency, not assumed from memory. Use that exact method name in the test; a differently-named method (e.g. `setIsIgnoringBatteryOptimizations`) does not exist on this version and will fail to compile.

- [ ] **Step 1: Write the failing test for `BatteryOptimization`**

```kotlin
package com.suprxsidh.deficit.system

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryOptimizationTest {

    @Test
    fun `isIgnoringBatteryOptimizations reflects the PowerManager state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadow = Shadows.shadowOf(powerManager)

        assertFalse(BatteryOptimization.isIgnoringBatteryOptimizations(context))

        shadow.setIgnoringBatteryOptimizations(context.packageName, true)
        assertTrue(BatteryOptimization.isIgnoringBatteryOptimizations(context))
    }

    @Test
    fun `batterySettingsIntent targets the ignore-battery-optimizations settings screen`() {
        assertEquals(
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            BatteryOptimization.batterySettingsIntent().action
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.system.BatteryOptimizationTest"`
Expected: FAIL — `BatteryOptimization` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.suprxsidh.deficit.system

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimization {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.system.BatteryOptimizationTest"`
Expected: PASS

- [ ] **Step 5: Write the failing test for `DashboardViewModel`'s device-status recheck**

Update `buildViewModel` in `DashboardViewModelTest.kt` once more to thread the new lambda and to replace the old inline health-status computation with a call to `refreshDeviceStatuses()` — add a parameter and pass a default:

```kotlin
    private fun buildViewModel(
        clock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 10, 12, 0) },
        healthConnectAvailability: Int = HealthConnectClient.SDK_AVAILABLE,
        hasPermissions: suspend () -> Boolean = { true },
        isIgnoringBatteryOptimizations: () -> Boolean = { false }
    ): DashboardViewModel {
        val settingsRepository = com.suprxsidh.deficit.data.repository.SettingsRepository(db.appSettingsDao())
        val weeklyCommitmentRepository = com.suprxsidh.deficit.data.repository.WeeklyCommitmentRepository(db.exerciseSessionDao(), clock)
        val adaptiveBudgetRepository = com.suprxsidh.deficit.data.repository.AdaptiveBudgetRepository(db.userProfileDao(), db.weighInDao(), settingsRepository)
        val weeklyReviewRepository = com.suprxsidh.deficit.data.repository.WeeklyReviewRepository(
            db.weeklyReviewDao(), weeklyCommitmentRepository, adaptiveBudgetRepository,
            db.foodEntryDao(), db.weighInDao(), db.userProfileDao(), settingsRepository, clock
        )
        return DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao(), clock = clock),
            UserProfileRepository(db.userProfileDao(), db.weighInDao()),
            WeightRepository(db.weighInDao()),
            HealthConnectRepository(NoOpHealthDataSource(), db.exerciseSessionDao(), db.syncStateDao(), db.weighInDao(), clock),
            healthConnectAvailability,
            hasPermissions,
            isIgnoringBatteryOptimizations,
            weeklyCommitmentRepository,
            weeklyReviewRepository,
            settingsRepository,
            clock
        )
    }
```

Add this test:

```kotlin
    @Test
    fun `refreshDeviceStatuses updates both battery and health connect status`() = runTest(testDispatcher) {
        var ignoringBattery = false
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 10, 12, 0) },
            isIgnoringBatteryOptimizations = { ignoringBattery }
        )
        backgroundScope.launch { viewModel.batteryOptimizationIgnored.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.batteryOptimizationIgnored.value)

        ignoringBattery = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }
```

- [ ] **Step 6: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel`'s constructor doesn't accept the fourth lambda yet, `batteryOptimizationIgnored`/`refreshDeviceStatuses` don't exist.

- [ ] **Step 7: Update `DashboardViewModel`**

Add the new constructor parameter (insert right after `hasHealthConnectPermissions`, before `weeklyCommitmentRepository`):
```kotlin
    private val isIgnoringBatteryOptimizations: () -> Boolean,
```

Replace the `_healthConnectStatus`/`init` block that sets it with:
```kotlin
    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.UNAVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

    fun refreshDeviceStatuses() {
        _batteryOptimizationIgnored.value = isIgnoringBatteryOptimizations()
        viewModelScope.launch {
            _healthConnectStatus.value = try {
                when {
                    healthConnectAvailability != HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.UNAVAILABLE
                    !hasHealthConnectPermissions() -> HealthConnectStatus.PERMISSIONS_NEEDED
                    else -> HealthConnectStatus.OK
                }
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to read Health Connect permission state", e)
                HealthConnectStatus.PERMISSIONS_NEEDED
            }
        }
    }

    init {
        refreshDeviceStatuses()

        viewModelScope.launch {
            try {
                weeklyReviewRepository.generateForCompletedWeekIfDue()
                _unseenWeeklyReview.value = weeklyReviewRepository.unseenReview()
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to generate or check the weekly review", e)
            }
        }

        viewModelScope.launch {
            try {
                val target = settingsRepository.observeWeeklyRunTarget().first()
                val floor = settingsRepository.observeWeeklyRunFloor().first()
                val state = weeklyCommitmentRepository.observeCurrentWeekState(target, floor).first()
                val streak = weeklyCommitmentRepository.consecutiveFloorIntactStreakWeeks(target, floor)
                val totalRuns = weeklyCommitmentRepository.totalRunDaysAllTime()
                val weightChange = weightRepository.observeTotalChangeSinceStart().first()
                val previousCategory = settingsRepository.getLastMotivationCategory()
                    ?.let { runCatching { MotivationCategory.valueOf(it) }.getOrNull() }

                val inputs = MotivationInputs(
                    floorAtRisk = state.floorState != FloorState.OK,
                    runsThisWeek = state.runsThisWeek,
                    weeklyTarget = state.target,
                    daysLeftInclusive = state.daysLeftInclusive,
                    floorIntactStreakWeeks = streak,
                    rollingWeightChangeKg = weightChange,
                    totalRunsLogged = totalRuns
                )
                val (category, line) = MotivationLine.dailyLine(inputs, previousCategory)
                settingsRepository.setLastMotivationCategory(category.name)
                _motivationLine.value = line
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to compute the motivation line", e)
            }
        }
    }
```
(This `init` block is unchanged from Task 10 except for the first line, which now calls `refreshDeviceStatuses()` instead of inlining the old health-status-only logic.)
(This removes the old standalone health-status `init { viewModelScope.launch { ... } }` block from Task 10's version — its logic now lives inside `refreshDeviceStatuses()`, called once from `init` and again from `DashboardScreen`'s resume observer.)

- [ ] **Step 8: Update the `DashboardScreen` call site, add the resume observer and the battery banner**

Update the `viewModelFactory` block to pass the new lambda:
```kotlin
    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                app.container.foodRepository,
                app.container.userProfileRepository,
                app.container.weightRepository,
                app.container.healthConnectRepository,
                app.container.healthConnectAvailability,
                app.container::hasHealthConnectPermissions,
                { com.suprxsidh.deficit.system.BatteryOptimization.isIgnoringBatteryOptimizations(app) },
                app.container.weeklyCommitmentRepository,
                app.container.weeklyReviewRepository,
                app.container.settingsRepository
            )
        }
    })
```

Add the resume observer (near the top of `DashboardScreen`, after `val context = LocalContext.current`):
```kotlin
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshDeviceStatuses()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        androidx.compose.runtime.DisposableEffectResult { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

Add the battery banner right after the existing `when (hcStatus) { ... }` block, before the `Button(onClick = onQuickAdd, ...)`:
```kotlin
        val batteryIgnored by viewModel.batteryOptimizationIgnored.collectAsState()
        if (!batteryIgnored) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Battery optimization can silently stop background sync on this device.")
                    TextButton(onClick = {
                        context.startActivity(com.suprxsidh.deficit.system.BatteryOptimization.batterySettingsIntent())
                    }) { Text("Fix battery settings") }
                }
            }
        }
```

- [ ] **Step 9: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: PASS

- [ ] **Step 10: Add the onboarding step**

In `OnboardingScreen.kt`, extend the step enum and the `when`:
```kotlin
private enum class OnboardingStep {
    PROFILE_ENTRY,
    HEALTH_CONNECT_SETUP,
    BATTERY_OPTIMIZATION_SETUP
}
```
Change `HEALTH_CONNECT_SETUP`'s `onContinue` from `onComplete` to `{ step = OnboardingStep.BATTERY_OPTIMIZATION_SETUP }`, and add a new branch:
```kotlin
        OnboardingStep.BATTERY_OPTIMIZATION_SETUP -> {
            val context = LocalContext.current
            var ignored by remember {
                mutableStateOf(com.suprxsidh.deficit.system.BatteryOptimization.isIgnoringBatteryOptimizations(context))
            }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        ignored = com.suprxsidh.deficit.system.BatteryOptimization.isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                androidx.compose.runtime.DisposableEffectResult { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            BatteryOptimizationSetupStep(
                ignored = ignored,
                onOpenSettings = { context.startActivity(com.suprxsidh.deficit.system.BatteryOptimization.batterySettingsIntent()) },
                onContinue = onComplete
            )
        }
```
And the composable, alongside `HealthConnectSetupStep`:
```kotlin
@Composable
private fun BatteryOptimizationSetupStep(
    ignored: Boolean,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Protect background sync", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("This phone's battery settings can silently stop Deficit from syncing runs and weight in the background.")
        Text("1. Set Deficit's battery usage to unrestricted, or disable battery optimization for it.")
        Text("2. In Vivo's i Manager, add Deficit to auto-start apps.")
        Spacer(Modifier.height(24.dp))
        if (ignored) {
            Text("Battery optimization disabled ✓")
        } else {
            Button(onClick = onOpenSettings) { Text("Open battery settings") }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onContinue) { Text(if (ignored) "Continue" else "Skip for now") }
    }
}
```

- [ ] **Step 11: Run the full test suite**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew test`
Expected: PASS — every test from Phase 1, Phase 2, and Tasks 1-12 of this phase.

- [ ] **Step 12: Build the debug APK**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/system/ \
        app/src/test/java/com/suprxsidh/deficit/system/BatteryOptimizationTest.kt \
        app/src/main/java/com/suprxsidh/deficit/ui/onboarding/OnboardingScreen.kt \
        app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModel.kt \
        app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardScreen.kt \
        app/src/test/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Task 12: battery-optimization onboarding step and resume-triggered status recheck"
```

---

## Self-Review

**Spec coverage.** All five in-scope items have a task chain: §4.1 partial (battery-optimization checklist) → Task 12; §3.7 weekly commitment & motivation (minus the daily notification) → Tasks 1, 5, 9, 10; §3.6 consistency view → Tasks 8, 11; §3.9 adaptive budget → Tasks 2, 6, 9, and surfaced in Task 7's review generation; §3.10 weekly review → Tasks 4, 7, 10. No scope item lacks an implementing task; no task implements something outside the five items (AlarmManager, notifications, the widget, routines, and backup/export were checked against and confirmed absent).

**Placeholder scan.** No `TBD`/`TODO`/"implement later"/"add appropriate handling"/"similar to Task N without repeating code" patterns remain — the one spot that originally elided a code block (Task 12 Step 7's `init` block) was filled in with the literal code during this review.

**Type/signature consistency across tasks, checked explicitly:**
- `WeeklyCommitmentRepository`'s constructor and method set (Task 5: `observeCurrentWeekState`, `runsCompletedForWeek`, `consecutiveFloorIntactStreakWeeks`, `totalRunDaysAllTime`) match every call site in Tasks 7, 9, 10.
- `SettingsRepository`'s five new accessor pairs (added in Task 4, not Task 9 — moved during this review once Task 6 was found to need them before Task 9 would have defined them) match every call site in Tasks 6, 7, 9, 10, 11.
- `DashboardViewModel`'s constructor parameter list was extended twice (Task 10 adds three params, Task 12 inserts a fourth) — both extensions were cross-checked against each other and against the final `buildViewModel` test helper and `DashboardScreen`'s `viewModelFactory` call site; all three list the same ten (Task 10) then eleven (Task 12) parameters in the same order.
- `AppSettingsEntity`'s field count (File Structure said "+5 fields") was corrected from an initial "+4" once `lastMotivationCategory` was identified as needed for Task 3/10's repeat-avoidance rule.
- The `repeat(...) { ... return@repeat ... }` early-exit bug in Task 5's original `consecutiveFloorIntactStreakWeeks` draft (Kotlin's `repeat` doesn't support breaking via `return@repeat` — it would have re-added the same broken week 104 times) was caught and replaced with a `for` loop with `break` during this review.
- `SettingsRepository.setGeminiApiKey`'s pre-existing implementation (`dao.upsert(AppSettingsEntity(id = 1, geminiApiKey = key))`) was found to silently reset every other field to its default on every call — a real bug this phase would have triggered the first time someone changed their Gemini key after setting a weekly target. Task 4 replaces it with a read-modify-write `upsertCopy` helper used by every accessor.

**Judgment calls made in this plan, collected in one place for the reviewer:** (1) a "run" toward the weekly target/floor is a Health Connect session whose `exerciseType` is `"56"` or `"57"`, counted per distinct calendar day, not per session (Global Constraints, Task 1/5); (2) "under soft budget" and "average daily deficit" use the existing dashboard's plain `bufferedKcal` vs. `softBudgetKcal` comparison, not adding back credited exercise calories (Global Constraints); (3) the motivation card's "never repeat two days running" rule is applied uniformly to all five priority tiers, including floor-at-risk — safe because the floor-risk *banner* is a separate, always-visible element (Task 3); (4) a manual budget override writes straight through to `UserProfileEntity.softBudgetKcal` rather than introducing a second "effective budget" lookup (Task 6); (5) `generateForCompletedWeekIfDue` skips entirely for any week that predates the user's onboarding date, so day-1 users never get a phantom "broken" review for a week before they installed the app (Task 7); (6) a `WeekSummary.outcome` is `null`, not `BROKEN`, for any week that hasn't fully elapsed yet (Task 8).
