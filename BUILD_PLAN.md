# stride — session continuation

## Status (2026-08-10)
- Project scaffolded: `~/claudecode-projects/deficit` (git-initialized), directory renamed to `~/claudecode-projects/stride` on 2026-08-22 as part of the full internal rename.
- `SPEC.md` saved (Fable plan, verbatim) — full 16-section spec, source of truth for ALL phases.
- **Phasing decision (user-approved 2026-08-10):** build in phases, not one pass. Phase 1 = "core loop" only:
  - Scaffold (Gradle, Kotlin, Compose, Material 3 black+accent theme, nav shell — SPEC.md §2, execution-plan agent 1, minus HC/notification manifest bits)
  - Room data layer for: `UserProfile`, `FoodEntry`, `CustomFood`, `WeighIn` (SPEC.md §5) — no `PendingDraft`, `ExerciseSession`, `MealTime`, `SyncState`, `AppSettings` fields tied to HC/notifications yet, no `Routine`/`RoutineStep`/`RoutineCompletion` yet
  - Food logging: quick-add (manual name+kcal) + custom foods CRUD + Open Food Facts search (SPEC.md §3.3 paths 2-4). Gemini AI path (§3.3 path 1) deferred to Phase 2.
  - Basic dashboard: buffered-vs-budget bar, weight sparkline, quick-add button (SPEC.md §3.2, minus run widget/motivation card which need HC)
  - Weight tracking + chart (SPEC.md §3.5) — manual entry only, no HC two-way sync yet
  - Onboarding (SPEC.md §3.1) — BMR/TDEE/soft-budget calc, no HC setup checklist yet
  - **Explicitly deferred to later phases:** Health Connect (§3.4), Gemini estimation (§3.3 path 1), weekly commitment/motivation (§3.7), consistency grid (§3.6), adaptive budget (§3.9), weekly review (§3.10), backup/export (§3.11), routines (§3.12), posture/meal/weigh-in notification reminders (§3.13-3.15), home screen widget (§3.8). Friction rules in §4 that depend on these (4.1, 4.2, 4.5, 4.6, 4.7) are deferred with them; 4.3/4.4/4.8/4.9 apply to Phase 1 as far as they're relevant (day-boundary logic, empty states, defaults).
## Phase 1: COMPLETE (2026-08-10)
Executed via superpowers:subagent-driven-development against `docs/superpowers/plans/2026-08-10-phase1-core-loop.md` — 10 tasks, each with its own implementer + task review (+ fix rounds where needed), then a final whole-branch review + one consolidated fix wave + scoped re-review. Merged `phase1-core-loop` → `master` (fast-forward), branch deleted, SDD workspace cleaned up. Full history/rulings for every finding: `git log` on master, or see this file's superseded ledger content was at `.superpowers/sdd/2026-08-10-phase1-core-loop/progress.md` (deleted post-merge per skill convention — the git history is the record now).

- **Result:** 53/53 unit tests passing, `./gradlew assembleDebug` succeeds, debug APK at `app/build/outputs/apk/debug/app-debug.apk` (~50MB).
- **What shipped:** onboarding (BMR/TDEE/soft-budget calc) → dashboard (budget bar + weight sparkline) → food logging (quick-add, pinned snacks with fractional servings, custom foods CRUD, Open Food Facts search with local-cache fallback on failure, per-entry delete) → weight tracking (manual entry, raw+rolling chart, total change, 4-week trend) → bottom nav between the three main screens. +10% calorie buffer and 3am day-boundary logic applied consistently everywhere (including across a live day-rollover while the app stays open — this was a real bug caught only by the final whole-branch review, not any single task's review, and is now fixed).
- **Known residual (accepted, not re-opened):** the day-boundary regression test doesn't fully exercise a long-lived subscriber surviving a rollover, even though the production fix was independently verified correct by direct code trace. Worth tightening if this area gets touched again.
- **Deliberately NOT fixed this phase** (filed as Minor in the final review, left alone by design): soft-budget label copy doesn't say "soft target" per spec wording; validation failures on quick-add/custom-food/weigh-in forms fail silently (no error text) rather than showing what to do next; over-budget bar visually caps at 100% (numeric text still shows the real overage); `goalWeightKg` is captured but never displayed; saving a custom food with the same name twice creates a duplicate row rather than updating (no edit path exists yet); pinning a 7th snack silently doesn't show (no message); no launcher icon (generic Android icon on the home screen); deprecated `Divider()`/`kotlinOptions{}` calls; ~54MB of stale Gradle-cache blobs sit in git history from an early Task 1 commit that got reverted (harmless disk waste, was never pushed anywhere — would need a history rewrite to reclaim, not done since nothing's been pushed yet and it costs nothing to leave alone locally).
- **Not yet verified on-device** — only Robolectric/JVM unit tests + `assembleDebug` have run. `TESTING.md` at the project root lists the manual on-device checklist (sideload, onboarding flow, quick-add buffer math, pinned snack, OFF search online/offline, weigh-in overwrite, day-boundary check, zero-data day-1 states).

## Phase 2: COMPLETE (2026-08-11)
Scope: Health Connect (run detection, two-way weigh-in sync, per-run analytics) + Gemini AI food logging — chosen as a deliberate subset of the full deferred backlog (notifications, widget, routines, weekly review, adaptive budget, consistency grid, backup/export all still deferred to later phases). Executed via superpowers:writing-plans → superpowers:subagent-driven-development against `docs/superpowers/plans/2026-08-11-phase2-health-connect-gemini.md` — 13 tasks, each with implementer + task review (+ fix rounds where needed, including one Critical bug in the plan's own Gemini sample code caught during Task 11's review — missing `responseMimeType` in the request), then a final whole-branch review that found 3 Critical + 8 Important cross-task issues, fixed in one consolidated wave, scoped re-review clean. Merged `worktree-phase2-health-connect-gemini` → `master`, branch deleted, SDD workspace cleaned up (was inside the now-removed worktree). Full history/rulings: `git log` on master.

- **Result:** 126/126 unit tests passing (26 classes), `./gradlew assembleDebug` succeeds, debug APK ~39MB.
- **What shipped:** Health Connect permission flow + onboarding setup checklist (skippable, never blocks) → exercise-session sync (dedup by `hcRecordId`, avg pace/HR analytics, 50%-credited calories) via WorkManager (60min periodic + on-open) → dashboard run summary + HC status banner with settings deep link → run detail screen with pace-trend chart → two-way weigh-in sync (push + import, both directions have independent watermarks after the final-review fix) → Settings screen for a locally-stored Gemini API key → Gemini REST client with strict-JSON-schema-constrained output → two-step estimate→review→confirm food logging flow (Gemini never auto-saves; +10% buffer applies on confirm) → pending-draft queue for failed Gemini calls with automatic retry-on-open + manual "just quick-add it" escape hatch + photo capture via FileProvider.
- **DB bumped v3→v4** (a field added to `SyncStateEntity` during the final-review fix wave forced this — Room's schema identity hash changed). Covered by the existing `fallbackToDestructiveMigration()`. **Nothing has ever been sideloaded to the Vivo X200T yet, so no on-device data exists to lose** — but this is now the second destructive-migration bump (v1→v2 in Phase 1, v2→v3→v4 here), worth remembering before ever treating a sideloaded install as containing data worth preserving.
- **Notable findings from the final whole-branch review, all fixed:** two silent bugs a per-task view couldn't see — (1) exercise-session sync and weigh-in sync shared one timestamp watermark, so weigh-in *import* from Health Connect died completely after the first sync cycle ever ran; (2) the sync read window had zero overlap, so a Samsung Health run published 30–60 min late (a documented real lag) could permanently miss its sync window. Also fixed: an uncaught crash on the Weight screen's sync button when HC permissions were denied, main-thread photo I/O, concurrent-retry double-logging, two unused Health Connect permissions that could brick sync on a partial grant, and the 3am day-boundary not being applied to Health-Connect-derived dates.
- **Deliberately parked as Minor (not fixed, non-blocking):** same-logical-day weigh-in re-import churn (harmless — HC reads ascending, settled value is deterministic); weigh-in dedupe's check-then-write isn't wrapped in a transaction the way exercise-session dedupe is (same race class, would fail silently as a duplicate row under real concurrency — no unique index exists on `weigh_in.date`/`hcRecordId` to backstop it); captured meal photos in cache dir are never cleaned up; onboarding step state uses `remember` not `rememberSaveable` (rotation mid-onboarding on the new HC step resets to profile entry); `healthConnectStatus` on the dashboard is computed once and not rechecked after granting permission via the settings deep link; `PaceTrendChart`'s y-axis isn't inverted relative to `WeightChart`'s convention; `RunDetailViewModel` subscribes to the same Flow twice instead of deriving one from the other; "just quick-add it" only offers the first pending draft, not a picker; `DEFAULT_MODEL = "gemini-flash-latest"` is a rotating alias, unverifiable from code — confirm it resolves on first real use.
- **Not yet verified on-device** — only Robolectric/JVM unit tests + `assembleDebug` have run, same as Phase 1. `TESTING.md` now has a "Phase 2 (Health Connect + Gemini)" section with the manual checklist (HC permission grant/deny, sync timing across the 30-60min Samsung Health lag, weigh-in round-trip both directions, airplane-mode Gemini retry, photo capture, quick-add escape hatch, settings key save/clear).

## Phase 3: COMPLETE (2026-08-12)
Scope: weekly commitment core — week-boundary math + adaptive budget + motivation lines + weekly review + consistency grid + battery-optimization onboarding (SPEC.md §3.6, §3.7, §3.9, §3.10, plus a battery-kill fix). Explicitly still deferred: notifications (§3.13-15 + §3.7 notification), Glance widget (§3.8), guided routines (§3.12), backup/export (§3.11), full persistent setup checklist (§4.6). Executed via superpowers:subagent-driven-development against `docs/superpowers/plans/2026-08-11-phase3-weekly-commitment.md` — 12 tasks, each with implementer + task review (+ fix rounds where needed — 2 tasks hit real plan-authored bugs: Task 8's month-boundary week-counting bug, Task 12's non-compiling `DisposableEffectResult` brief snippet), then a final whole-branch review (Opus) that found 6 Important cross-task issues invisible to any single task's review, fixed in one consolidated wave, scoped re-review clean. Merged `worktree-phase3-weekly-commitment` → `master` (fast-forward), branch deleted, worktree + SDD workspace cleaned up. Full history/rulings: `git log` on master.

- **Result:** 195/195 unit tests passing (39 classes), `./gradlew assembleDebug` succeeds.
- **What shipped:** week-boundary math + weekly run floor/target/streak (any day counts equally, Monday-Sunday week) → adaptive calorie budget recomputed from 7-day rolling weight average, with manual override precedence → data-driven motivation-line generator (one line/day, rotates categories, doesn't repeat) → weekly review generation (day-1-inclusive guard, avg deficit only over logged days) → monthly consistency grid + weekly summary rows (ConsistencyRepository, fixed to correctly widen boundary-week day-counting without reintroducing partial-week misclassification) → Settings UI for weekly target/floor + manual budget override (now clamped: 1-7, floor≤target, override≥1200 kcal) → Dashboard weekly-commitment card (now shows the permanent floor-intact streak stat), motivation card, weekly-review card, both screens now scrollable → new Consistency screen + bottom-nav tab (grid rows lacking a boundary-week summary show "Not enough data yet" instead of silently rendering nothing; summary line now includes /7 denominators + avg deficit) → battery-optimization onboarding step (permission-free, deep-links the general system settings list) + dashboard/onboarding status recheck-on-resume (also fixes a Phase 2 parked minor: `healthConnectStatus` was never rechecked after a settings-deep-link grant).
- **DB bumped v4→v6** (v5 for the new `WeeklyReviewEntity` + settings fields; v6 for a `lastMotivationDate` watermark added during the final-review fix wave). Still `fallbackToDestructiveMigration()`. Nothing has ever been sideloaded — still no on-device data to lose, but this is now three phases of destructive bumps in a row.
- **Notable findings only the final whole-branch review caught:** Dashboard/Settings screens had no vertical scroll and Phase 3's new cards/sections could push the primary "Log food" CTA off-screen on a real device; ConsistencyScreen was written against Task 8's *pre-fix* contract and silently dropped the summary line for any week straddling a month boundary; two SPEC-mandated fields were computed but never displayed anywhere (`/7` denominators + avg deficit on the consistency screen, the floor-intact streak as a permanent UI element rather than only via the rotating motivation line); Settings' weekly target/floor/override inputs had zero validation, so a bad value (e.g. floor > target, or floor > 7) could pin the floor/target logic into a permanently-broken state with no recovery path; the motivation line re-rolled on every cold start instead of once per logical day (no date watermark existed before this fix).
- **Deliberately parked as Minor (not fixed, non-blocking):** `RUN_EXERCISE_TYPES`/run-type constant was duplicated across two repositories — hoisted into a shared `RunTypes` object during the fix wave, so this one's actually resolved now; historical deficits on the Consistency screen are computed against the *current* soft budget rather than a per-day snapshot, so the same week's average deficit can read slightly differently there vs. on the (budget-snapshotted) weekly review card; the three per-day indicator dots on the consistency grid have no legend distinguishing ran/logged/under-budget; `totalRunDaysAllTime` counts distinct days but motivation copy calls it "runs logged" (two runs same day undercounts by one); a `WeeklyReviewRepository.recomputeIfNoOverride()` redundant `takeIf` (harmless).
- **Not yet verified on-device** — only Robolectric/JVM unit tests + `assembleDebug` have run, same as Phases 1-2. The final review specifically flagged the battery-optimization deep link's OriginOS behavior, the `ON_RESUME` recheck actually firing after returning from that settings screen, and whether Samsung Health/the watch tags runs as `56` vs `57` as the biggest untested real-device surface for this phase.

## Post-Phase-3 fixes + v1.0.0/v1.0.1 release (2026-08-12/13)
App renamed Deficit → Stride, initially cosmetic only (package stayed `com.suprxsidh.deficit`). Released `v1.0.0` (GitHub, debug APK) then `v1.0.1` fixing the first real on-device bug: Health Connect never listed Stride as a connectable app at all (missing `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` manifest handler, required on Android 14+). **Update 2026-08-22**: full internal rename to Stride (`e8733d7`) — package, classes, and local dir now all say Stride; `applicationId` deliberately still `com.suprxsidh.deficit` (Android app identity, changing it forces reinstall + loses Health Connect auth + local data). The domain concept "deficit" (calorie deficit) is a separate thing and stays as-is.

**2026-08-13 session:** user sideloaded v1.0.1, slept, woke up — still zero Health Connect import. Root cause (found by static code read, no device available): the sync worker was only ever scheduled from `MainActivity.onCreate`, gated on permissions *already* being granted at that check. Onboarding's permission-grant step never scheduled it itself, so on a fresh install the worker never got enqueued until a full cold restart — which hadn't happened since the app was only backgrounded overnight. Diagnosed a fix (`OnboardingScreen.kt`'s permission-result callback calling `HealthConnectSyncWorker.schedulePeriodic`/`triggerOneOff` directly) but left it uncommitted; re-applied plus a second independent gap in the 2026-08-20 session below. Also diagnosed (not a bug): the motivation card's logic always produces a line — if it "isn't there," it's a plain default `Card` blending into the bland UI, not a crash. And clarified for the user: anterior-pelvic-tilt/posture routines were never built, that's SPEC §3.12/§3.13, still Phase 4 backlog, not a regression.

**Redesign approved this session**, mockups-first via an HTML/CSS artifact (no image-gen tool available in this harness — default to that approach for any future mobile mockup work). Direction: bold & athletic, Oura/Whoop-inspired, pushed past generic-AI-dashboard defaults into a running/ledger-specific motif — hand-built seven-segment LED readout for the hero kcal number, punch-card consistency grid, ticket-stub day counter, mechanical odometer streak, checkpoint-flag progress indicators reused as nav icons. Scoped to 3 representative screens (dashboard, consistency, onboarding) rather than all 7, on the reasoning the rest inherit the resulting design system. Written up as a formal spec (`docs/superpowers/specs/2026-08-20-visual-redesign-spec.md`) and implemented in `worktree-redesign-v1` — see below. Both this doc-only edit and the redesign approval note were uncommitted on master's working tree until 2026-08-20, when they were committed as their own small doc-only commit ahead of the HC bugfix merge below.

## HC sync-scheduling bugfix (2026-08-20, worktree `hc-import-bugfix`, branch `worktree-hc-import-bugfix`)
User report: "that doesn't import from Samsung Health properly yet." Root-caused by reading the full HC integration end-to-end rather than guessing. Full writeup in `CLAUDE.md` under "Health Connect sync scheduling — root cause + fix". Short version:

- **Fixed (2 independent scheduling gaps, same root cause as the 2026-08-13 onboarding bug):** (1) re-applied the onboarding permission-grant-callback fix from 2026-08-13 (it existed only as an uncommitted diff on the original `master` checkout and a worktree does not inherit uncommitted changes, so it had to be manually re-applied here); (2) newly found — the dashboard's "Open Health Connect settings" deep-link re-grant path (`DashboardViewModel.refreshDeviceStatuses()`, called on `ON_RESUME`) updated the status banner to OK but never scheduled the sync worker, so granting permission that way and backgrounding (not killing) the app left sync silently disabled. This is very plausibly the actual live bug, since the known history records the user backgrounding rather than killing the app on the last on-device test.
- **Resolved the long-standing open uncertainty about exercise-type `56` vs `57`:** decompiled the real `androidx.health.connect:connect-client:1.1.0` AAR with `javap` and confirmed `"running"`→56, `"running_treadmill"`→57 — `RunTypes.EXERCISE_TYPES = {"56","57"}` is exactly correct. Also confirmed this filter only affects weekly-commitment/consistency "run day" counting, never the sync/import layer, so it could never have been the cause of an import failure either way.
- **Verified still intact, not regressed:** independent exercise/weigh-in sync watermarks, and the 48h `SYNC_LOOKBACK` read-window widening for Samsung Health's 30-60min publish lag.
- **Tests:** 199/199 unit tests pass (up from 195), including new Robolectric coverage for both scheduling fixes (`DashboardViewModelTest`, `HealthConnectSyncWorkerTest` using `WorkManagerTestInitHelper`). `./gradlew assembleDebug` succeeds.
- **NOT confirmed — still needs the user's own on-device re-test:** whether this actually fixes the reported symptom (both fixes are scheduling-layer, found by static reading, with zero device confirmation that Samsung Health→Health Connect writes behave as the code assumes); the onboarding callback's 3-line wiring has no Compose-level test (no `createComposeRule` usage exists in this repo at all); WorkManager's 60-min periodic cadence actually surviving OriginOS's battery management. On next sideload: grant HC permission via BOTH the onboarding flow AND the dashboard's settings deep-link (they're two independent fixes now), background (don't kill) each time, and confirm a run/weigh-in actually shows up within ~60-90 min without a cold restart.
- **Merged to master 2026-08-20** (rebased onto master's doc commit, then fast-forwarded); worktree/branch deleted.

## Visual redesign: COMPLETE (2026-08-20, worktree `worktree-redesign-v1`)

Full UI redesign approved 2026-08-13 (bold & athletic, Oura/Whoop-inspired, LED-readout/punch-card/
ledger motif, single ember accent `#FF6A3D`, tabular-monospace numerals — see `CLAUDE.md`'s
"Visual redesign" section for the full direction). The original HTML/CSS mockup artifact (3
screens: dashboard, consistency, onboarding) is not saved anywhere accessible, so this session
wrote a complete implementation spec from the approved-direction description first.

Working in `.claude/worktrees/redesign-v1`, branch `worktree-redesign-v1`. Originally branched from
master commit `bd0da81`; rebased onto master (post HC-bugfix-merge) on 2026-08-20 so it now sits on
top of the sync-scheduling fix — the only real conflicts were import-block collisions in
`DashboardScreen.kt`/`OnboardingScreen.kt` (both branches added imports to the same block; the HC
fix's actual scheduling logic auto-merged cleanly and is intact) plus doc-file conflicts in this
file and `CLAUDE.md` (resolved by keeping both sections). All 7 screens now migrated, the nav
icon built, a whole-branch review done, and the branch merged to master — see the completion
notes below. No on-device verification done at any point — only `compileDebugKotlin`,
`testDebugUnitTest`, and `assembleDebug`.

**Completed this session (2026-08-20):**
1. Spec doc: `docs/superpowers/specs/2026-08-20-visual-redesign-spec.md` — color tokens, type
   scale (JetBrains Mono, bundled as `res/font/jetbrains_mono_*.ttf`, OFL-licensed), 4dp spacing
   scale, corner-shape scale, and concrete designs/code for the three shared components
   (`LedReadout`, `PunchCardRow`, `StartLineDivider`/`StartLineProgress`).
2. Shared design system built: `Color.kt`, `Type.kt`, `Theme.kt` rewritten with the ember-on-black
   palette + JetBrains Mono type scale; new `Shape.kt` (squared 2-8dp corners) and `Spacing.kt`
   (4dp-base scale); three new composables in `ui/theme/component/` (`LedReadout.kt`,
   `PunchCardRow.kt` with a custom notched `Shape`, `StartLine.kt`).
3. Dashboard screen fully migrated: hero kcal stat → `LedReadout`, budget bar →
   `StartLineProgress`, all cards (weekly review, weekly commitment, motivation, today's-run,
   HC-permissions-needed, battery-optimization) → `PunchCardRow`. Floor-intact streak now has a
   permanent home in the `LedReadout` trailing slot instead of only surfacing via the rotating
   motivation line.
4. Consistency screen fully migrated: month header uppercase + `StartLineDivider`, week-summary
   line → `PunchCardRow`, day-cell dots recolored to `StridePositive`/`StrideOutline`.
   `weekSummaryText`'s exact string format is untouched (covered by `ConsistencyScreenTest`).
5. Onboarding screen fully migrated: all three steps (profile entry, HC setup, battery setup) get
   uppercase `titleLarge` headers + `StartLineDivider`; swapped `headlineSmall` (not in the
   redesign's type scale, was rendering in the default Material sans font) for `titleLarge`.
6. After each screen: ran `./gradlew testDebugUnitTest` — 195/195 passing throughout, no
   regressions. Also ran `assembleDebug` after each — clean throughout.

**What's left:** nothing — all 7 screens migrated, nav icon built, whole-branch review done.

**Completed after the "What's left" gap above, same session (2026-08-20):**
- Food logging, Weight, Settings, and Run Detail (spec omits Run Detail by name, but it's a real
  7th screen and spec §10 point 4 explicitly cites "a run's pace" as a numeric-readout example, so
  it was migrated too for whole-app consistency) — all now inherit the design system per spec §10:
  `Card`/`Divider`/`LinearProgressIndicator` swapped for `PunchCardRow`/`StartLineDivider`/
  `StartLineProgress`, standalone numeric readouts (today's counted kcal, weight total-change) for
  `LedReadout`. Health-Connect-setup turned out to already be covered — it's the `HealthConnectSetupStep`
  composable inside `OnboardingScreen.kt`, migrated along with the rest of onboarding above, not a
  separate screen.
- Nav-icon checkered-flag glyph (spec §9): new `NavFlagIcon` composable (4x4 StrideEmber/
  StrideEmberDim checkerboard reusing `StartLineTrack`'s alternating-square logic), wired into
  `MainActivity.kt`'s bottom nav for the active tab; inactive tabs keep the stock Material icon
  recolored `StrideOnSurfaceMuted`. Also fixed an unrelated leftover from the Deficit→Stride rename
  caught while touching this file: the `TopAppBar` title still hardcoded "Deficit".
- Whole-branch review (this project's established finishing-a-development-branch convention):
  found and fixed two missing-scroll bugs invisible to any single screen's own review —
  `ConsistencyScreen` (a month can render up to 6 week rows, no scroll wrapper existed) and
  Onboarding's `ProfileEntryStep` (5 fields + chips + button, no scroll wrapper). Confirmed no
  stray `Divider()`/`Card()`/`LinearProgressIndicator` usages remain anywhere in `ui/`.
- **Merged to master 2026-08-20** (non-fast-forward merge — master had advanced with the HC fix
  and doc commit since this branch's rebase point); worktree/branch deleted.
- **Still NOT confirmed — needs on-device verification, never done at any point in this redesign:**
  the LED-readout glow-fake, the punch-card notch shape, and the nav-flag glyph have only ever been
  compiled, never rendered on a real screen. Sideload master and actually look at all three,
  alongside re-testing both HC permission-grant paths from the sync-scheduling fix above.

## Next 3 actions
1. Sideload master's debug APK and actually look at the redesign on the Vivo X200T — the LED-readout
   glow-fake, punch-card notch shape, and nav-flag glyph have never been rendered on a real screen,
   only compiled. Re-test both HC permission-grant paths (onboarding + dashboard settings deep-link)
   from the sync-scheduling fix at the same time.
2. Once on-device confirmed, work through `TESTING.md`'s full manual checklist covering all prior
   phases' untested-on-device behavior.
3. Decide whether any parked Minor items from Phases 1-3 are worth a follow-up pass, then scope
   Phase 4 from the remaining deferred backlog: notifications, Glance home-screen widget, guided
   routines, backup/export, full persistent setup checklist (§4.6).

## Phase 1 Minor polish backlog: all 8 items fixed (2026-08-21)

Fixed the 8 Minor items filed by the 2026-08-10 Phase 1 whole-branch review and left unfixed by
design at the time (see that phase's section above for the original list). Bug/polish fixes only —
no Phase 2/3/4-scoped work touched.

1. **"Soft target" label copy** — `DashboardScreen.kt`'s hero `LedReadout` label changed from
   `"kcal logged of $budget"` to `"kcal logged · soft target $budget"`, matching SPEC.md §2.4's
   explicit instruction ("Label it a 'soft target' in the UI").
2. **Silent validation failures** — quick-add (`FoodLogViewModel.logQuickAdd`), custom-food save
   (`saveCustomFood`), and weigh-in (`WeightViewModel.logWeighIn`) all used to `return` silently on
   invalid input. Each now sets an observable error string (`quickAddError`, `customFoodError`,
   `weighInError`) that `FoodLogScreen.kt`/`WeightScreen.kt` render in `MaterialTheme.colorScheme.error`.
3. **Over-budget bar visual cap** — `StartLineProgress` (in `ui/theme/component/StartLine.kt`) gained
   an `overBudget: Boolean` param; when true the fully-lit bar renders in `StrideError` instead of
   `StrideEmber`, so "at budget" and "over budget" no longer look identical once the bar caps at
   100%. `DashboardScreen.kt` passes `overBudget = total > budget`. The LED readout's raw kcal number
   was already uncapped, so the real overage was already visible numerically — only the bar needed a
   distinct visual state.
4. **Unused `goalWeightKg` display** — `WeightViewModel` gained an optional `UserProfileRepository`
   param and a `goalWeightKg: StateFlow<Double?>`; `WeightScreen.kt` now shows "Goal: X kg" plus
   "Y kg to go" (computed against the latest logged weight) below the weigh-in button.
5. **Custom-food save created a duplicate instead of editing** — `CustomFoodEntity.id` always
   defaulted to 0 (autoGenerate), so every save inserted a fresh row even when the name matched an
   existing one. Added `CustomFoodDao.findByName()` (case-insensitive); `saveCustomFood` now looks up
   an existing row by name first and reuses its `id` on upsert, so re-saving the same name updates
   that row instead of duplicating it. No schema change needed (no new unique index).
6. **Pin-cap-of-6 silent truncation** — `observePinned()`'s `LIMIT 6` only ever affected what
   displayed; nothing stopped the DB from marking a 7th+ food `isPinned=true`, so it silently never
   appeared. Added `CustomFoodDao.countPinned()` (uncapped) plus a shared `MAX_PINNED_SNACKS = 6`
   constant; `saveCustomFood` now refuses to set the pin flag once the real count is already at the
   cap (unless the food was already pinned), saves the food unpinned instead, and surfaces
   `pinCapMessage` explaining why.
7. **Missing launcher icon** — the manifest had no `android:icon`/`android:roundIcon` at all
   (generic Android icon on the home screen). Added an adaptive icon
   (`res/mipmap-anydpi-v26/ic_launcher{,_round}.xml` + `res/drawable/ic_launcher_{background,foreground}.xml`,
   no legacy PNG mipmaps needed since minSdk 28 ≥ 26): flat `StrideBackground` (#0A0A0B) behind the
   same 4x4 `StrideEmber`/`StrideEmberDim` checkerboard glyph `NavFlagIcon` already uses for the
   active bottom-nav tab, so the launcher icon shares the app's own motif instead of a generic one.
   Verified with `aapt dump badging` on the built APK that `application-icon`/`launchable-activity`
   now resolve to it.
8. **Deprecated `Divider()`/`kotlinOptions{}`** — a repo-wide grep found zero literal `Divider()`
   calls left (the visual redesign had already replaced them all with `StartLineDivider`/
   `HorizontalDivider`, contrary to what the polish item's phrasing implied was still open — verified
   directly rather than assumed). `kotlinOptions { jvmTarget = "17" }` in `app/build.gradle.kts` (the
   part that *was* still present and deprecated as of Kotlin 2.0, removed in 2.2) replaced with the
   current top-level `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` DSL.

**Verification:** `./gradlew testDebugUnitTest` — 199/199 passing (same count as before this session;
no new tests added, none of the existing ones needed changes since all new ViewModel/DAO params are
optional/additive). `./gradlew assembleDebug` succeeds; confirmed via `aapt dump badging` that the
new launcher icon is actually packaged into the APK. **Not verified on-device** — same standing
caveat as every other unverified item in this file; the launcher icon, over-budget bar color, goal
weight readout, and all three new error messages have only been compiled, never looked at on screen.
