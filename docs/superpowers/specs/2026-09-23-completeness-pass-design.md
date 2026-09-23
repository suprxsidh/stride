# Stride completeness pass — implementation spec

Status: approved in chat 2026-09-23. Execution mode: subagent-driven, one fresh implementer
subagent per task group below, orchestrator (not a separate reviewer subagent) reviews each
diff before proceeding, per explicit user request to minimize token spend on this pass.

## 1. Goal

Close the gaps the user hit using the calorie-only cut day-to-day: no way to look back at past
days, no sense of multi-day trend, no protein target, a budget that never actually adapts, no
reminders, and default (janky-feeling) screen transitions. Six independent pieces, grouped into
4 implementation tasks by shared files to minimize subagent overhead.

## 2. Feature A — History (browse + edit/delete past days)

`FoodRepository.observeTodayEntries()`/`todayKeyFlow()` are hardcoded to "today" (via
`DayBoundary.logicalDate`, the existing 3 AM boundary rule — reuse it, do not reintroduce a
different boundary). Add:
- `FoodRepository.observeEntriesForDate(date: LocalDate): Flow<List<FoodEntryEntity>>` and
  `observeBufferedTotalForDate(date: LocalDate): Flow<Int>` (parallel to the existing
  today-only functions, not replacing them — dashboard still wants "today" specifically).
- `FoodRepository.updateFoodEntry(entry: FoodEntryEntity)` (delete already exists via
  `deleteFoodEntry`; there is no update path today).
- `FoodLogScreen`/`FoodLogViewModel`: accept an optional `LocalDate` (default today), add a
  prev-day/next-day arrow pair + a date label header. Editing an entry opens the existing
  quick-add-style edit UI pre-filled; next/prev day never goes past whatever data exists (no
  artificial floor/ceiling needed — Room just returns empty list for dateless days).
- Nav: `Routes.FOOD_LOG` needs an optional date arg (nav-compose optional argument, default
  "today" sentinel) so `StrideNavHost` can pass a specific date when navigating from a future
  history entry point (not required for v1 — Dashboard's quick-add still always opens today).

## 3. Feature B — 7-day rolling net-deficit card

New pure function `data/calc/RollingDeficit.kt`: given a list of `(date, budgetKcal,
bufferedConsumedKcal)` for the trailing 7 logical days, return `sum(budget - consumed)`. Needs
per-day consumed totals across a date range — add `FoodRepository.observeBufferedTotalsForRange(
start: LocalDate, end: LocalDate): Flow<Map<LocalDate, Int>>` (one query, grouped by date, not
7 separate flows). New `DashboardViewModel` state field + a new `RollingDeficitCard` composable
on `DashboardScreen`, placed under the existing today budget bar. Missing days count as
`budget - 0` (i.e. a full day of budget banked, not skipped) — matches "the rolling average is
robust to gaps" precedent already established for weight in `SPEC.md` §3.15.

## 4. Feature C — Protein floor

- `ai/gemini/GeminiModels.kt`: add `proteinG: Double` to `GeminiFoodItem`, `totalProteinG:
  Double` to `GeminiFoodEstimate`; update `FOOD_ESTIMATE_SCHEMA` (both levels) and the
  `PROMPT` in `GeminiFoodEstimator.kt` to request a protein-in-grams estimate per item, same
  "estimate high when ambiguous" rule already used for kcal.
- `data/db/entity/FoodEntryEntity.kt`: add `proteinG: Double = 0.0` (Room migration bump,
  destructive per project convention — same pattern as every prior schema change here).
- `UserProfileEntity`: add `proteinFloorG: Double`. Onboarding's `ProfileEntryStep` gets one
  more numeric field with a sane default (`1.6 * weightKg`, editable) — no new onboarding
  screen.
- Dashboard: a protein progress bar/line next to the calorie budget bar, same visual language
  (reuse `PunchCardRow`/`StartLineProgress` per the existing design system, not a new component
  type). Uses `observeTodayBufferedTotal()`'s sibling for protein (add
  `FoodRepository.observeTodayProteinTotal(): Flow<Double>`).

## 5. Feature D — Adaptive budget actually adapts (small fix, not a new feature)

`AdaptiveBudgetRepository.recomputeIfNoOverride()` and `AdaptiveBudgetCalc` already do the
weight-based recompute — it is simply never called except from `clearManualOverride()`. Two
changes:
1. Call `recomputeIfNoOverride()` from `WeightRepository.logWeighIn()` right after a weigh-in
   is persisted (inject `AdaptiveBudgetRepository` there, or move the call to whichever layer
   already has both — avoid a circular dependency; if `WeightRepository` can't hold it, call it
   from `WeightViewModel` right after `logWeighIn` returns).
2. Replace `UserProfileEntity.age: Int` with `birthDate: LocalDate` (destructive migration,
   same convention). `AdaptiveBudgetCalc.recomputeSoftBudgetKcal` takes the computed
   `Period.between(birthDate, LocalDate.now()).years` instead of a stored int, so budget drifts
   correctly as the user ages, not just as weight changes. Onboarding's `ProfileEntryStep`
   swaps its age input for a date picker. `CalorieMath.bmr` keeps taking `age: Int` — compute it
   at the call site, don't change that pure function's signature.
3. Also call `recomputeIfNoOverride()` once per app foreground (e.g. `DashboardViewModel.init`)
   so an age-driven change (which has no weigh-in event to trigger off) surfaces within a day of
   crossing a birthday, not only on the next weigh-in.

## 6. Feature E — Reminders (weigh-in, meal, snack)

Ground-zero: no `AlarmManager`, `NotificationChannel`, or `POST_NOTIFICATIONS` handling exists
anywhere in the app today. Reuse the already-designed (never-built) plan in `SPEC.md` §3.14,
§3.15, §4.1, §4.2 rather than re-deriving it:

- **Scheduling: `AlarmManager` exact alarms only** (`setExactAndAllowWhileIdle`, requesting
  `SCHEDULE_EXACT_ALARM`), per SPEC §4.1 — WorkManager is explicitly ruled out for
  time-of-day reminders on this OEM (it's already reserved for HC sync). One `BroadcastReceiver`
  (`ReminderAlarmReceiver`) handles all three types by an intent extra (`REMINDER_TYPE`), fired
  and rescheduled for +24h on receipt, plus a `BootCompletedReceiver` that reschedules everything
  on device restart (alarms don't survive reboot otherwise).
- **Channels**: one `NotificationChannel` per type (`weigh_in`, `meal`, `snack`) so any can be
  muted at the OS level independently, per SPEC §140's stated reasoning (still valid even though
  that section's macro ban is superseded by Feature C above — only the "one channel per type"
  design principle carries over).
- **Weigh-in** (SPEC §3.15): default 7:00 AM, user-editable time in Settings/onboarding.
  Notification has a `RemoteInput` direct-reply action — typed number is parsed, logged via
  `WeightRepository.logWeighIn` (which now also triggers Feature D's recompute), written to HC.
  Smart-suppressed: skip firing if `WeightRepository` already has an entry for today.
  Tapping the body (not the reply field) opens `WeightScreen`.
- **Meal nudges** (SPEC §3.14 + §4.2): defaults breakfast 9:00/lunch 13:30/dinner 20:30, each
  editable, add/remove supported. Fires a lead-time (default 45 min) before the meal time.
  Notification has two actions: `RemoteInput` direct-reply ("2 rotis and chole" → background
  `GeminiFoodEstimator.estimate` call → `FoodRepository.logGeminiEstimate` → a low-priority
  follow-up notification showing the counted kcal, tap-to-edit) and a "Camera" action that opens
  `MainActivity` straight into the photo-capture flow. Failed background Gemini calls fall back
  to the existing pending-draft queue (`4.3` in SPEC, already built) — do not build a second
  retry mechanism.
- **Snack nudges** (new, not in original SPEC — user-requested extension): identical mechanism
  to meal nudges (own channel, own `RemoteInput` + camera actions, own time list in
  Settings/onboarding), just a separate reminder type/label. No lead-time concept needed (a
  snack isn't a scheduled event) — fires exactly at each configured time.
- **Setup**: new onboarding step `ReminderSetupStep` (after `BatteryOptimizationSetupStep`) —
  requests `POST_NOTIFICATIONS` (API 33+) and lets the user set/accept-default times for all
  three types before finishing onboarding. Reuses `BatteryOptimization.kt`'s existing
  `isIgnoringBatteryOptimizations()` check/messaging rather than writing a second version — same
  OriginOS landmine that already bit Health Connect sync once.

This is the largest, highest-risk piece of the pass. No on-device verification is possible in
this session (matches every prior phase's stated limitation) — ship it well-tested at the unit
level (alarm-time math, direct-reply parsing, suppression logic) and flag it clearly as
needing a real-device pass before being trusted, same as HC sync was after Phase 2.

## 7. Feature F — Nav transition fix

`StrideNavHost.kt`'s `composable()` calls take zero transition args today (bare
Navigation-Compose default crossfade). Add explicit `enterTransition`/`exitTransition`/
`popEnterTransition`/`popExitTransition` per route: push = slide in from end + fade,
pop = slide out to end + fade (standard Android forward/back semantics), ~250-300ms, matching
this project's existing motion feel (nothing else in the app currently defines a duration
constant — add one, e.g. `MotionTokens.kt` in `ui/theme/`, rather than a magic number per
route).

## 8. Data model / migration summary

Room version bump (destructive, per every prior migration in this project):
- `FoodEntryEntity`: + `proteinG: Double`
- `UserProfileEntity`: + `proteinFloorG: Double`; `age: Int` → `birthDate: String` (ISO date,
  matching this project's existing convention of storing `LocalDate` as ISO strings elsewhere,
  e.g. `WeighInEntity.date`)
- New `ReminderEntity(id: Long, type: String, time: String, label: String?, enabled: Boolean)` +
  DAO/repository — checked `SettingsRepository`/`AppSettingsEntity`: it's a fixed single-row
  table (copy-on-write), which doesn't fit meal reminders' "add/remove" requirement (variable
  count). One row per reminder instance (weigh-in has exactly one row; meal/snack can have any
  number) is the right shape, not a new column set on the existing settings row.

## 9. Testing

Robolectric unit tests, matching this project's existing bar:
- `RollingDeficitTest` (gap-day handling, partial-week handling)
- `AdaptiveBudgetRepositoryTest` extended for the birthdate-driven age + the new
  weigh-in-triggers-recompute wiring
- Gemini estimate parsing tests extended for the new protein fields
- `ReminderAlarmReceiverTest` / scheduling-math tests using `WorkManagerTestInitHelper`'s
  `AlarmManager`-equivalent shadow (Robolectric's `ShadowAlarmManager`) — verify correct next-fire
  time computation and reschedule-on-boot, not real OS delivery
- `FoodLogViewModelTest` extended for date-parameterized entries + update path
- `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` must both pass before merge.

## 10. Out of scope (unchanged from the 2026-09-02 cut)

DB backup/export, making onboarding revisitable, water tracking, social features, cloud sync,
streaks, run/exercise tracking, weekly review/consistency grid, motivation lines. Macros beyond
protein (fat/carb splits, sodium, fiber) are explicitly still out per the user's own stated
reasoning in this session.

## 11. Task grouping for implementation (minimizing subagent count)

1. **History + Nav transitions** (Features A + F — both touch `ui/food/*`, `ui/nav/*`,
   share no other overlap with the rest, small enough to combine).
2. **Rolling deficit + Protein floor** (Features B + C — both touch `Dashboard*`,
   `FoodRepository`, onboarding's `ProfileEntryStep`, Gemini models/prompt).
3. **Adaptive budget wiring + birthdate migration** (Feature D — touches `UserProfileEntity`,
   `WeightRepository`/`WeightViewModel`, onboarding).
4. **Reminders** (Feature E — large, self-contained, new files mostly; touches Settings +
   onboarding for the setup step only).

Run in this order (2 and 3 both touch `ProfileEntryStep` — sequencing avoids a merge conflict
inside one file; 4 is independent of 1-3 and could theoretically run in parallel, but per
project convention subagents run one at a time in this worktree, not in parallel).
