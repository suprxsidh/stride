# deficit — session continuation

## Status (2026-08-10)
- Project scaffolded: `~/claudecode-projects/deficit`, git-initialized.
- `SPEC.md` saved (Fable plan, verbatim) — full 16-section spec, source of truth for ALL phases.
- **Phasing decision (user-approved 2026-08-10):** build in phases, not one pass. Phase 1 = "core loop" only:
  - Scaffold (Gradle, Kotlin, Compose, Material 3 black+accent theme, nav shell — SPEC.md §2, execution-plan agent 1, minus HC/notification manifest bits)
  - Room data layer for: `UserProfile`, `FoodEntry`, `CustomFood`, `WeighIn` (SPEC.md §5) — no `PendingDraft`, `ExerciseSession`, `MealTime`, `SyncState`, `AppSettings` fields tied to HC/notifications yet, no `Routine`/`RoutineStep`/`RoutineCompletion` yet
  - Food logging: quick-add (manual name+kcal) + custom foods CRUD + Open Food Facts search (SPEC.md §3.3 paths 2-4). Gemini AI path (§3.3 path 1) deferred to Phase 2.
  - Basic dashboard: buffered-vs-budget bar, weight sparkline, quick-add button (SPEC.md §3.2, minus run widget/motivation card which need HC)
  - Weight tracking + chart (SPEC.md §3.5) — manual entry only, no HC two-way sync yet
  - Onboarding (SPEC.md §3.1) — BMR/TDEE/soft-budget calc, no HC setup checklist yet
  - **Explicitly deferred to later phases:** Health Connect (§3.4), Gemini estimation (§3.3 path 1), weekly commitment/motivation (§3.7), consistency grid (§3.6), adaptive budget (§3.9), weekly review (§3.10), backup/export (§3.11), routines (§3.12), posture/meal/weigh-in notification reminders (§3.13-3.15), home screen widget (§3.8). Friction rules in §4 that depend on these (4.1, 4.2, 4.5, 4.6, 4.7) are deferred with them; 4.3/4.4/4.8/4.9 apply to Phase 1 as far as they're relevant (day-boundary logic, empty states, defaults).
- Next: superpowers:writing-plans to turn Phase 1 scope into a granular task-by-task plan, then superpowers:subagent-driven-development to execute it.

## Next 3 actions
1. Run superpowers:writing-plans against SPEC.md + this phasing note to produce Phase 1's granular, reviewable task plan.
2. Execute via superpowers:subagent-driven-development, one implementer at a time, task review + fix loop per task, final whole-branch review, then `./gradlew assembleDebug`.
3. Verify Phase 1 APK on-device with user, then return here to scope Phase 2 (Health Connect + Gemini).
