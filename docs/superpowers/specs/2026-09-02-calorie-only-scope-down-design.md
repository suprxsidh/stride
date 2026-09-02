# Stride/Deficit calorie-only scope-down — implementation spec

Status: approved in chat 2026-09-02. Execution mode: `superpowers:subagent-driven-development`
(per project `CLAUDE.md`).

## 1. Goal

Reduce the app to just a calorie counter: log food (photo or text) via Gemini, see today's
logged calories vs. a daily budget derived from height/weight/age/sex at onboarding, and let
Health Connect (Samsung Health) supply calories burned so the budget/plan stays a real deficit.
Weekly running commitment, consistency tracking, run analytics, and Open Food Facts search are
cut for now — exercise logging will be scoped separately later. Phase 4 (notifications, widget,
backup, guided routines) was never built, so there is nothing to remove for it.

## 2. Keep (no behavior change, only trimming coupled code)

- Onboarding → height/weight/age/sex/goal weight → BMR (Mifflin-St Jeor) → TDEE × 1.2 → soft
  budget (`data/calc/CalorieMath.kt`, `data/db/entity/UserProfileEntity.kt`,
  `data/repository/UserProfileRepository.kt`, `ui/onboarding/*`).
- Gemini food logging, photo or text (`ai/gemini/*`, `data/repository/GeminiFoodRepository.kt`,
  `data/repository/FoodRepository.kt`, `data/db/entity/FoodEntryEntity.kt`,
  `data/db/entity/CustomFoodEntity.kt`, `data/db/entity/PendingDraftEntity.kt` + DAOs), including
  the +10% buffer and the offline pending-draft retry queue.
- Health Connect sync, trimmed to `TotalCaloriesBurnedRecord` (calories burned) and `WeightRecord`
  (read + write) only (`data/repository/HealthConnectRepository.kt`, `health/*`).
- Weekly adaptive budget recalculation off the 7-day rolling weigh-in average — this is the
  weight-loss plan itself (`data/repository/AdaptiveBudgetRepository.kt`,
  `data/calc/RollingAverage.kt`, `data/calc/WeekBoundary.kt`).
- Weigh-ins: manual entry + HC round-trip (`data/repository/WeightRepository.kt`,
  `data/db/entity/WeighInEntity.kt`, `ui/weight/*`).
- Settings, `ui/theme/*` (visual redesign is orthogonal to this cut), `MainActivity.kt`,
  `StrideApp.kt`, `data/AppContainer.kt`, `data/db/StrideDatabase.kt` (all trimmed, not removed).
- `system/BatteryOptimization.kt` — still needed for the HC sync `WorkManager` job to survive
  OriginOS's background-kill behavior.

## 3. Cut

- Weekly running commitment + consistency grid + weekly review:
  `data/calc/WeeklyCommitmentCalc.kt`, `data/repository/WeeklyCommitmentRepository.kt`,
  `data/repository/ConsistencyRepository.kt`, `data/repository/WeeklyReviewRepository.kt`,
  their entities (`WeeklyReviewEntity.kt`) and DAOs, `ui/dashboard/WeeklyCommitmentCard.kt`,
  `ui/dashboard/WeeklyReviewCard.kt`, any `ui/consistency/*` screen.
- Run tracking/analytics: `data/calc/RunAnalytics.kt`, `data/calc/RunTypes.kt`,
  `data/db/entity/ExerciseSessionEntity.kt` + DAO, `ui/health/RunDetailScreen.kt`,
  `ui/health/RunDetailViewModel.kt`. Trim `HealthConnectRepository` to drop
  `ExerciseSessionRecord`/`DistanceRecord`/`SpeedRecord`/`HeartRateRecord` reads.
- Open Food Facts barcode/search logging path: `food/off/*`, `data/db/entity/OffCacheEntity.kt` +
  DAO, and the OFF half of `ui/food/FoodLogScreen.kt`/`FoodLogViewModel.kt`. Gemini becomes the
  only logging path.
- `ui/dashboard/MotivationCard.kt` (tied to commitment streaks, meaningless without them).
- Any nav routes in `ui/nav/Routes.kt`/`StrideNavHost.kt` pointing at the above screens.

## 4. Data model / migration

Room entities removed: `ExerciseSessionEntity`, `WeeklyReviewEntity`, `OffCacheEntity`. Add a Room
migration that drops their tables; this is a destructive-but-scoped migration (single-user,
sideloaded debug app — acceptable to bump the DB version and drop rather than preserve unused
history). `SyncStateEntity`/DAO stays (still needed for the calories/weight HC watermark), but
drop any exercise-sync-specific watermark rows it tracked.

## 5. Dashboard (resulting UI)

Single screen: today's logged (buffered) calories vs. soft budget as a simple bar, "credited from
Health Connect: X kcal burned today" line, quick-add / camera / type-it entry point. Nothing else.

## 6. Error handling

No new error paths — this is subtraction. Existing Gemini-offline pending-draft retry and HC
permission-denied handling carry over unchanged for the code that remains.

## 7. Testing

Update/delete Robolectric unit tests for every removed class (`WeeklyCommitmentCalcTest`,
`ConsistencyRepositoryTest`, `RunAnalyticsTest`, `OffRepositoryTest`, etc. — exact names per
existing `app/src/test` tree). `DashboardViewModelTest` and `HealthConnectRepositoryTest` need
updating for the trimmed scope, not deletion. `./gradlew testDebugUnitTest` and
`./gradlew assembleDebug` must both pass before merge, matching this project's existing bar.

## 8. Out of scope (deferred, not deleted from history)

Exercise/running logging, weekly commitment, consistency, notifications, widget, backup, guided
routines — all remain in git history on `master` and are re-scoped later, not rebuilt from
scratch. Note this in `future_plans.md`.
