# Stride completeness pass — implementation plan

Spec: `docs/superpowers/specs/2026-09-23-completeness-pass-design.md` (read it first — this
plan only adds task boundaries/sequencing, it does not repeat the design). Execution: one fresh
implementer subagent per task, committing when done; orchestrator reviews the diff directly
(no separate reviewer subagent this pass, per explicit user request to minimize tokens). Run
tasks in order — 2 and 3 both touch `OnboardingScreen.kt`'s `ProfileEntryStep`.

## Task 1 — History + nav transitions (spec §2, §7)

Implement spec §2 (History) and §7 (nav transitions) in full. Files: `FoodRepository.kt`,
`FoodLogScreen.kt`, `FoodLogViewModel.kt`, `Routes.kt`, `StrideNavHost.kt`, new
`ui/theme/MotionTokens.kt`. Add/extend tests: `FoodRepositoryTest`, `FoodLogViewModelTest`.
Do not touch Dashboard, onboarding, Gemini models, or anything reminder-related.
Verify: `./gradlew testDebugUnitTest assembleDebug` green. Commit with a clear message.

## Task 2 — Rolling deficit + protein floor (spec §3, §4)

Implement spec §3 (Feature B) and §4 (Feature C) in full. Files: new
`data/calc/RollingDeficit.kt`, `FoodRepository.kt` (additive — do not remove Task 1's
additions), `DashboardViewModel.kt`, `DashboardScreen.kt`, `ai/gemini/GeminiModels.kt`,
`ai/gemini/GeminiFoodEstimator.kt`, `FoodEntryEntity.kt` + DAO + migration, `UserProfileEntity.kt`
(add `proteinFloorG` only — birthdate change is Task 3's job, do not touch `age` here),
`OnboardingScreen.kt`'s `ProfileEntryStep` (add the protein-floor field only). Add/extend tests:
`RollingDeficitTest`, `GeminiFoodEstimatorTest`/`GeminiFoodRepositoryTest`, `DashboardViewModelTest`.
Verify: `./gradlew testDebugUnitTest assembleDebug` green. Commit.

## Task 3 — Adaptive budget wiring + birthdate (spec §5)

Implement spec §5 (Feature D) in full. Files: `UserProfileEntity.kt` (`age` → `birthDate`,
migration), `AdaptiveBudgetCalc.kt`, `AdaptiveBudgetRepository.kt`, `WeightRepository.kt` or
`WeightViewModel.kt` (wire the recompute call — pick whichever avoids a circular dependency,
state which one and why in the commit message), `DashboardViewModel.kt` (foreground recompute
check), `OnboardingScreen.kt`'s `ProfileEntryStep` (age input → date picker). `CalorieMath.bmr`
keeps its existing `age: Int` signature — compute the int at the call site from `birthDate`.
Add/extend tests: `AdaptiveBudgetRepositoryTest`, `AdaptiveBudgetCalcTest`,
`UserProfileEntityTest`/migration test if one pattern exists already (check `app/src/test` for
prior migration test precedent before inventing a new pattern). Verify:
`./gradlew testDebugUnitTest assembleDebug` green. Commit.

## Task 4 — Reminders (spec §6)

Implement spec §6 (Feature E) in full — this is the largest task, budget accordingly. New files
under a new `reminders/` package: `ReminderAlarmReceiver.kt`, `BootCompletedReceiver.kt`,
`ReminderScheduler.kt` (wraps `AlarmManager`), `ReminderEntity.kt` + DAO + repository,
`NotificationChannels.kt` (creates the 3 channels), `ReminderNotificationBuilder.kt` (builds
each type's notification incl. `RemoteInput` actions). Modify: `AndroidManifest.xml` (receivers,
`SCHEDULE_EXACT_ALARM` + `POST_NOTIFICATIONS` permissions), `OnboardingScreen.kt` (new
`ReminderSetupStep`, placed after `BatteryOptimizationSetupStep`), `MainActivity.kt` (handle the
direct-reply/camera notification actions and the "open weigh-in screen" tap target),
`WeightRepository.kt`/`FoodRepository.kt` call sites for the background-logged direct-reply
path (reuse `GeminiFoodEstimator` + the existing pending-draft queue on failure — do not build a
second retry mechanism). Add/extend tests per spec §9's `ReminderAlarmReceiverTest` /
`ShadowAlarmManager`-based scheduling tests, plus direct-reply parsing tests. Verify:
`./gradlew testDebugUnitTest assembleDebug` green. Commit. Flag explicitly in the commit message
and final report that this is unverified on a real device/OS (matches every prior phase's
stated limitation for OS-integration code).

## After all 4 tasks

Orchestrator reads the full worktree diff end-to-end (one consolidated pass, standing in for
this project's usual "final whole-branch review" — done directly rather than via another
subagent, per the token-minimization request), fixes anything small directly, re-runs
`./gradlew testDebugUnitTest assembleDebug` on the merge-ready state, then hands off to
`superpowers:finishing-a-development-branch` conventions (merge to master, delete worktree/
branch) followed by a version bump, release build, and GitHub release per the user's explicit
request to push to their personal GitHub and hand back the release link.
