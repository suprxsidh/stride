# stride — project constraints

Personal Android weight-loss tracker. Single user (Suprasidh), sideloaded debug APK, target device Vivo X200T (Android 16, OriginOS).

- Spec of record: `SPEC.md` (came from a Fable planning session on claude.ai, 2026-08-10). Do not re-derive requirements — read it.
- Tech stack is fixed in SPEC.md §2 — Kotlin, Compose, Material 3, Health Connect, Room, Ktor/Retrofit, WorkManager. Do not substitute.
- No backend, no accounts, no analytics, no cloud sync — all data stays on-device. Only network calls allowed: Open Food Facts, Gemini (only if user sets a key).
- Execution mode: superpowers:subagent-driven-development, in this repo (git-initialized 2026-08-10).
- Portfolio-level conventions from `~/claudecode-projects/CLAUDE.md` apply (push destination, plan→approval→PoC→verify→scale flow, etc.).

## Local toolchain (2026-08-10)
No system-default `java`/`ANDROID_HOME` on this machine — both are present via Homebrew but unlinked. Every implementer/reviewer subagent MUST use these, not system defaults:
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` (OpenJDK 21.0.11)
- `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` (build-tools 34.0.0/36.0.0, platforms 34/35/36, platform-tools already installed, licenses already accepted)
- Gradle binary: `/opt/homebrew/bin/gradle` (project has no wrapper yet — Task 1 generates `./gradlew` via `gradle wrapper`)
- Baked into the project: `local.properties` (`sdk.dir=...`) and `gradle.properties` (`org.gradle.java.home=...`) — set by Task 1, must not be deleted by later tasks. **Correction (found during Task 4):** this covers the JVM Gradle uses to actually run the build, but NOT the `./gradlew` wrapper script's own bootstrap step — that step needs a working `java` on `PATH` or `JAVA_HOME` set *before* Gradle ever reads `gradle.properties`, and macOS's system `java` stub fails immediately. Every implementer subagent must run `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` in its shell before the first `./gradlew` call each task.
- **AGP/Gradle version (bumped during Phase 2 Task 3, 2026-08-11):** AGP 8.7.3→**8.11.1**, Gradle wrapper 8.9→**8.13**. Forced by adding `androidx.health.connect:connect-client:1.1.0` — that AAR's metadata floor requires AGP ≥8.9.1, and the project's `compileSdk = 36` (set in Task 1) needs AGP ≥8.11 for full API-36 support. Verified safe: full existing test suite (19 classes) stayed green and `assembleDebug` succeeded after the bump. No other toolchain paths changed — `JAVA_HOME`/`ANDROID_HOME` above are unaffected.

## Health Connect sync scheduling — root cause + fix (2026-08-20, worktree `hc-import-bugfix`)

User report: "that doesn't import from Samsung Health properly yet." Investigated by reading the full HC integration end-to-end (`HealthConnectDataSource`, `HealthConnectManager`, `HealthConnectSyncWorker`, `HealthConnectRepository`) plus every call site of `HealthConnectSyncWorker.schedulePeriodic`/`triggerOneOff`, in a dedicated worktree (`git worktree add .claude/worktrees/hc-import-bugfix -b worktree-hc-import-bugfix master`).

**Root cause (confirmed by reading every place permission can be granted):** the sync worker was only ever scheduled from two places, and a real third path had NO scheduling call at all:
1. Onboarding's permission-grant callback (`OnboardingScreen.kt`) — previously missing (this was the already-diagnosed, previously-uncommitted Bug #2 fix from 2026-08-13; re-applied here since a worktree doesn't inherit uncommitted working-tree changes from the original checkout). **Fixed.**
2. `MainActivity.onCreate`'s one-shot `LaunchedEffect(Unit)` check — this path always worked, but only helps on a full cold start.
3. **Newly found this session:** the dashboard's "Open Health Connect settings" button (`DashboardScreen.kt`) deep-links to the system Health Connect app so the user can grant permission there. On return, `ON_RESUME` calls `DashboardViewModel.refreshDeviceStatuses()`, which recomputes and displays the `healthConnectStatus` banner as `OK` — but never scheduled the worker. A user who grants via this path and backgrounds (rather than kills) the app sees a green "all good" banner while sync is still never enqueued. **This is very likely the actual bug still live** — the known history notes explicitly record the user backgrounding rather than killing the app during the last on-device test, which is exactly the failure mode this path produces regardless of whether the onboarding fix was present. **Fixed**: `refreshDeviceStatuses()` now takes an injected `scheduleHealthConnectSync: () -> Unit` and calls it on the `PERMISSIONS_NEEDED`/`UNAVAILABLE` → `OK` transition only (guarded against re-firing on every resume, since `triggerOneOff`'s `REPLACE` policy would otherwise restart the one-off sync on every foreground and could prevent it from ever completing).

**Investigated and confirmed NOT bugs (real evidence, not assumption):**
- **Exercise-type constants (long-standing open uncertainty, item #4 in prior history):** `RunTypes.EXERCISE_TYPES = setOf("56", "57")` was checked against the real `androidx.health.connect:connect-client:1.1.0` AAR by decompiling `ExerciseSessionRecord.class` with `javap` — its string-to-int constant table maps `"running"` → 56 and `"running_treadmill"` → 57, exactly matching the code. This uncertainty is now resolved: the constants are correct. Also confirmed this filter is only ever applied downstream (`WeeklyCommitmentRepository`, `ConsistencyRepository`, for "run day" counting), never at the sync/import layer in `HealthConnectRepository.syncExerciseSessions()` — so even if it were wrong, it could never have blocked *import*, only weekly-commitment/consistency-grid counting.
- **Independent watermarks** (`lastSyncEpochMs` / `lastWeightSyncEpochMs`, item #3): still split correctly in `HealthConnectRepository` — verified by reading `syncExerciseSessions()`/`syncWeighIns()` directly.
- **48h read-window widening for Samsung Health's 30-60min publish lag** (item #5): `readSince()` still subtracts `SYNC_LOOKBACK = Duration.ofHours(48)` from the stored watermark before every read. Intact, not narrowed.

**What is fixed and unit-tested (Robolectric):** both scheduling gaps now have direct regression coverage — `DashboardViewModelTest` (`refreshDeviceStatuses schedules HC sync on the transition into OK`, plus a cold-start variant) and `HealthConnectSyncWorkerTest` (new `WorkManagerTestInitHelper`-based tests proving `schedulePeriodic`/`triggerOneOff` actually enqueue real named WorkManager requests). 199/199 unit tests pass, `./gradlew assembleDebug` succeeds.

**What is still NOT confirmed — needs a real device + real Samsung Health data:**
- Whether this actually fixes the user's reported symptom at all. Both fixes address *scheduling* gaps found by static code reading; there is still no on-device confirmation that Samsung Health writes exercise/weight records into Health Connect the way the code assumes, or that WorkManager's periodic 60-min cadence actually fires reliably under OriginOS's battery-management behavior (the battery-optimization onboarding step exists specifically because of this risk, per Phase 3 history).
- The Onboarding permission-callback fix has **no Compose-level test** — no `createComposeRule` usage exists anywhere in this repo's test suite, so the 3-line grant-callback wiring in `OnboardingScreen.kt` is code-reviewed only, not unit-tested. Low risk given its size, but flagging per the "don't claim more than tested" rule.
- Whether the *first* real sync after granting permission actually surfaces exercise sessions/weigh-ins that Samsung Health already logged — the 30-day `FIRST_SYNC_LOOKBACK_DAYS` floor and 48h lookback widening are logic-verified, not device-verified.
- **On-device checklist for next sideload:** grant HC permission via onboarding AND separately via the dashboard's "Open Health Connect settings" deep-link (both paths, since they're now two independent fixes) → background (don't kill) the app each time → confirm a run/weigh-in actually appears within ~60-90 min without a cold restart. Pull `adb logcat | grep -i health` if it still doesn't.
- **Merged to master 2026-08-20** (rebased onto master, then fast-forwarded). This entry stays as the historical record.

## Visual redesign: COMPLETE and merged to master (approved 2026-08-13, built 2026-08-20)

Full UI redesign: bold & athletic, Oura/Whoop-inspired dark theme (single ember accent
`#FF6A3D`, tabular-monospace numerals, "data as hero" layouts), pushed into a running/ledger-
specific motif — LED-readout stat displays, punch-card/ledger list rows, a shared "start line"
checkered-flag divider/progress motif, nav icons reusing the flag/punch-card language. Mockups (3
screens: dashboard, consistency, onboarding) existed only as an HTML/CSS artifact from a
brainstorming session, never committed to the repo and not retrievable afterward — so a full
written spec was produced from memory before any Compose work started.

Built in `.claude/worktrees/redesign-v1` (branch `worktree-redesign-v1`), rebased onto master
after the HC sync-scheduling fix merged, then merged to master itself after a whole-branch review
(this project's established finishing-a-development-branch convention) — worktree/branch deleted.

- Spec of record for the redesign: `docs/superpowers/specs/2026-08-20-visual-redesign-spec.md` —
  concrete color/type/spacing/shape tokens plus the three reusable composables (`LedReadout`,
  `PunchCardRow`, `StartLineDivider`/`StartLineProgress` in `ui/theme/component/`) plus the nav-icon
  glyph (`NavFlagIcon`). Treat this as the source of truth for anything redesign-related; don't
  re-derive from the (unrecoverable) original mockup.
- Font: JetBrains Mono (OFL), bundled at `app/src/main/res/font/jetbrains_mono_*.ttf` — chosen
  over Compose's Downloadable Fonts API specifically because that API needs Google Play Services
  network access, which would violate the "only OFF/Gemini network calls" constraint above.
- Dashboard, Consistency, and Onboarding were mocked directly; Food logging, Weight, Settings, and
  Run Detail (not named in the spec, but a real 7th screen — migrated too, per §10 point 4's own
  "a run's pace" numeric-readout example) inherit the resulting design system per spec §10.
  Health-Connect-setup is not a separate screen — it's the `HealthConnectSetupStep` composable
  inside `OnboardingScreen.kt`, migrated along with the rest of onboarding.
- Nav-icon checkered-flag glyph (spec §9): `NavFlagIcon` (4x4 StrideEmber/StrideEmberDim
  checkerboard), shown for the active bottom-nav tab in `MainActivity.kt`; inactive tabs keep the
  stock Material icon recolored `StrideOnSurfaceMuted`.
- Whole-branch review caught two missing-scroll bugs invisible to any single screen's own
  migration: `ConsistencyScreen` (a month can render up to 6 week rows) and Onboarding's
  `ProfileEntryStep` (5 fields + chips + button) both had no scroll wrapper; both fixed.
- **Never verified on a real device at any point** — the LED-readout glow-fake, punch-card notch
  shape, and nav-flag glyph have only ever been compiled (`compileDebugKotlin`, `assembleDebug`),
  never rendered on screen. This is the single biggest open risk on this whole redesign.

## Calorie-only scope-down (2026-09-02, COMPLETE)

App re-scoped to a pure calorie counter per user direction: Gemini food logging (photo/text) + onboarding BMR/TDEE budget + weigh-ins + Health Connect calories-burned read + weekly adaptive budget recompute. Cut: weekly running commitment, consistency grid, run analytics/detail, weekly review, motivation lines, Open Food Facts search. See `docs/superpowers/specs/2026-09-02-calorie-only-scope-down-design.md` and `docs/superpowers/plans/2026-09-02-calorie-only-scope-down.md`. Exercise logging is deferred, not deleted from history — see `future_plans.md`.

Technical implementation details:
- Health Connect's `REQUIRED_PERMISSIONS` covers only `TotalCaloriesBurnedRecord` (read) and `WeightRecord` (read+write) — no more Exercise/Distance/HeartRate. Any future exercise-tracking work re-adds those deliberately, not by assuming they're still there.
- `HealthConnectRepository` no longer has `syncExerciseSessions()`/`observeExerciseSessions()` — it exposes `getTodaysCaloriesBurned()` instead, reading the whole logical day's total (basal + active) directly, not a per-exercise-session credit.
- Open Food Facts search is gone — Gemini (photo or text) is the only food-logging path. `FoodEntryEntity.offBarcode` no longer exists.
- `StrideDatabase` version climbs 6→9 across this scope-down's tasks (still `fallbackToDestructiveMigration()`, no real `Migration` objects — same convention as every prior bump).

**Process note for future SDD plans on this project:** a plan step that says "add these imports" as prose *after* a fenced code block gets missed — the pre-flight scan for this plan caught exactly that (Task 1's `DashboardViewModel.kt`). Put every import the code block actually needs inside the code block itself, never in a trailing note.
