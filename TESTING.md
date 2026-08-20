# Deficit — Phase 1 (Core Loop) Testing Notes

Phase 1 has no Health Connect, Gemini, notifications, widget, or routines — see `SPEC.md` and `BUILD_PLAN.md` for what's deferred to later phases against the same spec.

## Automated coverage
`./gradlew testDebugUnitTest` — 48 tests, 0 failures, 0 errors, across 15 test classes: calorie/BMR/TDEE math, 3am day-boundary logic, 7-day rolling average, all four DAOs, all three repositories, the Open Food Facts client (success + network-failure paths), and every screen's ViewModel (validation, buffer math wiring, day-boundary-aware "today" queries).

## Manual on-device checks (sideload `app/build/outputs/apk/debug/app-debug.apk`)
1. **Onboarding**: fresh install → onboarding screen appears; submitting blank fields shows a validation error; submitting valid data lands on the Dashboard with a bottom nav bar (Today / Food / Weight).
2. **Quick add**: log a food via Quick Add on the Food screen; confirm the dashboard's "counted" total updates and matches raw×1.10 (rounded up).
3. **Pinned snack**: pin a custom food (isPinned = true via the custom-food form), confirm it appears as a one-tap chip and logs correctly.
4. **Open Food Facts search**: search a real packaged food (needs network); confirm results appear and logging one adds it to today's list. Turn off network and search again — should show "No results — check spelling or your connection." not a crash.
5. **Weigh-in**: log a weigh-in; log a second one the same day and confirm it overwrites (still one entry for today, not two). Log a few more across different days and confirm the raw+rolling chart renders (raw points faint, rolling average bold) and total-change/trend text appears.
6. **Day boundary**: if practical, change the device clock to just after midnight and log a meal — confirm it's grouped with the previous day's log, not a new "today."
7. **Zero-data day 1**: on a completely fresh profile with nothing logged, confirm every screen shows a sensible message (never a blank panel or infinite spinner).

## Known Phase 1 gaps (deferred to later phases, not bugs)
- No Health Connect: no run detection, no two-way weigh-in sync, no exercise credit shown anywhere in the UI (the math function exists and is tested, just has no live caller yet).
- No Gemini AI food estimation — only Quick Add / OFF search / Custom Foods.
- No notifications of any kind (motivation, meals, weigh-in, posture, backup reminder).
- No home screen widget.
- No guided routines (mobility/warm-up/cool-down).
- No backup/export/restore.
- No weekly commitment/motivation system, consistency grid, or weekly review.

## Phase 2 (Health Connect + Gemini) Testing Notes

Phase 2 adds Health Connect sync (exercise + weight, two-way) and Gemini-based AI meal estimation. See `SPEC.md` for what's still deferred (notifications, widget, routines, backup/export).

## Automated coverage
`./gradlew testDebugUnitTest` — 126 tests, 0 failures, 0 errors, across 26 test classes. New in Phase 2: `HealthConnectRepository` (permission gating, exercise dedupe by `hcRecordId`, weigh-in two-way sync, unavailable/revoked-permission fallbacks), `HealthConnectSyncWorker`, `GeminiFoodEstimator` (prompt/schema construction, response parsing, HTTP/IO error mapping), `GeminiFoodRepository` (estimate-without-saving, confirm-with-edits, pending-draft-on-failure, retry-and-auto-log, discard-as-quick-add), `SettingsRepository`, and the extended `FoodLogViewModel` (AI estimate availability gating, review-before-save flow, cancel-without-logging, draft banner actions). `HealthConnectDataSource` (the thin wrapper around the actual `HealthConnectClient` SDK calls) is explicitly **not** unit-tested — it can't run outside a device/emulator with Health Connect installed — but all the business logic it feeds into (`HealthConnectRepository`) is fully unit-tested against a fake data source instead.

Added by the final whole-branch review fix wave: independent exercise/weigh-in sync watermarks (a weigh-in still imports after exercise sync ran first in the same cycle, and repeated cycles keep importing), the publish-lag read lookback (a run published after the watermark was set is still picked up, and re-reading it updates rather than duplicates), the 3am logical day boundary applied to Health Connect timestamps, weigh-in import updating an existing row for the same day instead of creating a second one, `WeightViewModel` surfacing a message instead of crashing when permissions are missing, concurrent `retryPendingDrafts()` logging a meal only once, reactive `aiEstimateAvailable`, and `ExerciseSessionDao.upsertByHcRecordId` insert-or-update.

## Manual on-device checks (sideload `app/build/outputs/apk/debug/app-debug.apk`)
0. **First install wipes local data**: the DB is at v4 with destructive fallback, so sideloading over an existing Phase 1 install DISCARDS previously logged food/weigh-ins. Uninstall-reinstall deliberately, or accept the wipe, before running the checks below.
1. **Health Connect permission grant**: from onboarding (or Settings), trigger the Health Connect permission request; grant all requested permissions (exercise, calories, distance, heart rate, weight read/write — six in total, and steps/speed must NOT be requested); confirm the app doesn't crash and subsequently reports itself as having permissions.
2. **Health Connect permission denial**: deny/skip the permission prompt; confirm the app falls back to an "unavailable/permissions not granted" UI state rather than crashing, and that Quick Add / OFF / custom-food logging still work normally without Health Connect. Then, with permissions still denied but Health Connect installed, tap "Sync with Health Connect" on the Weight screen: it must show "Health Connect permissions needed — grant them in Settings" rather than crashing.
2b. **Partial permission grant**: grant only some of the six requested permissions; confirm the app reports "permissions needed" rather than silently never syncing, and that granting the rest recovers it.
3. **Samsung Health run sync timing**: log a run in Samsung Health (or another Health Connect–writing app) on the device; confirm it appears in Deficit's exercise credit either within the periodic 60-minute WorkManager sync window, or immediately after opening Samsung Health then reopening Deficit (which triggers a one-off sync on app open).
4. **Exercise dedupe**: sync the same run twice (e.g. force a manual sync twice in a row); confirm it is credited only once, not duplicated, by its Health Connect record id.
5. **Weigh-in round-trip, Deficit → Health Connect**: log a weigh-in in Deficit; confirm it appears as a weight record in Health Connect (and thus in any other app reading from Health Connect).
6. **Weigh-in round-trip, Health Connect → Deficit**: log a weight record in another Health Connect–writing app; confirm it syncs into Deficit's weight history on next sync.
6b. **Weigh-in import keeps working across cycles**: after a first successful sync, write another weight record externally and sync again; confirm the second one imports too (a shared sync cursor used to make every sync after the first import nothing).
6c. **One row per day**: with a weigh-in already logged in Deficit for today, write a *different* weight for today from another app and sync; confirm Deficit's weight history shows a single updated entry for today, not two, and the rolling-average trend doesn't jump.
6d. **Late-published run**: after a sync has run, let Samsung Health publish a run whose start time is earlier than that sync; confirm it still gets credited on a later sync (the read window looks back 48h).
7. **Gemini airplane-mode retry**: turn on airplane mode, set a Gemini API key in Settings, submit an AI meal description; confirm it does NOT crash or silently drop the meal, but instead shows an error and creates a pending draft (nothing typed is lost). Turn airplane mode back off and either wait for the next app-open retry or manually trigger "Retry now" from the pending-drafts banner; confirm the draft resolves into a logged entry with the same +10% buffer as every other logging path.
8. **"Just quick-add it" escape hatch**: with a pending draft present (from the airplane-mode check above, or any other Gemini failure), tap "Just quick-add it," enter a calorie value in the dialog, and confirm it logs a `QUICK`-sourced entry and clears the draft — this is a real user-entered value, not a hardcoded fallback.
9. **AI review-before-save**: with a Gemini key set and network available, submit an AI meal description; confirm the app shows the itemized review dialog (items, kcal, confidence) BEFORE anything is saved — check the dashboard/food-log total is unchanged until "Confirm & log" is tapped. Tap "Cancel" once and confirm nothing was logged. Edit the name/kcal fields before confirming and confirm the edited values (not the original estimate) are what gets logged.
10. **Gemini hidden with no key**: with no Gemini API key set, confirm the "Describe your meal" / AI estimate UI does not appear at all on the Food screen (not just disabled — absent).
11. **Settings key save/clear**: set a Gemini API key in Settings, confirm the AI estimate UI appears on Food screen; clear the key, confirm the AI UI disappears again and any previously pending drafts are left untouched (not deleted).
12. **`HealthConnectDataSource` mapping logic**: explicitly device-only, not unit-tested — it requires a real Health Connect client to exercise. The business logic it feeds (`HealthConnectRepository`) is fully unit-tested instead; this manual pass is the only verification of the actual SDK-facing mapping/permission calls.

## Known Phase 2 gaps (deferred to later phases, not bugs)
- No notifications of any kind (motivation, meals, weigh-in, posture, backup reminder).
- No home screen widget.
- No guided routines (mobility/warm-up/cool-down).
- No backup/export/restore.
- No weekly commitment/motivation system, consistency grid, or weekly review.
- No in-app UI for reviewing/dismissing a Gemini pending draft list beyond the single-draft "Retry now" / "Just quick-add it" banner (multiple simultaneous drafts are stored and retried correctly, but only the oldest draft's id is offered to the "Just quick-add it" dialog at a time).

## Health Connect sync-scheduling fix (2026-08-20) — manual checks

Two independent scheduling gaps were fixed this session (see `BUILD_PLAN.md`/`CLAUDE.md` for the
full root-cause writeup): onboarding's permission-grant callback never scheduled the sync worker,
and separately the dashboard's "Open Health Connect settings" deep-link re-grant path updated the
status banner without scheduling the worker either. Both are now code-reviewed and unit-tested
(Robolectric/WorkManager-test-only) but **never confirmed against a real device or real Samsung
Health data**. On next sideload:

1. **Fresh install, onboarding grant path**: fresh install → grant Health Connect permission during
   onboarding → background the app (do NOT force-kill it) → confirm a run or weigh-in already in
   Health Connect/Samsung Health appears within ~60-90 minutes without a cold restart.
2. **Existing install, dashboard settings deep-link path**: with the app already past onboarding
   and HC permission previously denied/revoked, tap the dashboard's "Open Health Connect settings"
   banner button → grant permission in the system HC settings screen → return to Stride by
   backgrounding (not killing) it → confirm the banner clears to OK AND a run/weigh-in actually
   syncs within the same ~60-90 minute window. This path is the one most likely to have been the
   user's originally reported symptom (backgrounding rather than killing is what the prior session's
   history recorded).
3. **`adb logcat | grep -i health`** if sync still silently doesn't happen on either path — the fix
   is scheduling-layer only; it does not change anything about whether Samsung Health actually
   writes into Health Connect on this device, or whether WorkManager's periodic work survives
   OriginOS's battery management.

## Visual redesign (2026-08-20) — manual checks, NEVER done at any point

The full bold-athletic/LED-readout/punch-card redesign (spec:
`docs/superpowers/specs/2026-08-20-visual-redesign-spec.md`) has only ever been compiled
(`compileDebugKotlin`, `assembleDebug`) and exercised by JVM/Robolectric unit tests — **it has
never been rendered on a real screen**. This is the single biggest untested-on-device surface in
the app right now. On next sideload, check all 7 screens (Dashboard, Consistency, Onboarding, Food
logging, Weight, Settings, Run history) plus the bottom nav:

1. **LED-readout glow-fake**: the hero kcal number (Dashboard), streak counter (weekly-commitment
   card), food-log daily total, and weight total-change stat should all render as a recessed black
   bezel with a glowing ember seven-segment-style number — confirm the "glow" (a 10%-alpha
   `StrideEmberGlow` layer) is actually visible against the near-black `StrideSurfaceSunken`
   background and not lost to display gamma/brightness.
2. **Punch-card notch shape**: list rows (weekly review/commitment/motivation cards, run-summary
   rows, Consistency's week-summary lines, Food log's search/custom-food/logged-today rows,
   Run history's per-run rows) should show visible semicircular notches punched into the left/right
   edges at vertical center — confirm the notch actually reads as a cutout and isn't invisible at
   real screen density, and that text never collides with the punched-out curve.
3. **Nav-icon checkered-flag glyph**: the active bottom-nav tab should show a small 4x4
   ember/dim checkerboard instead of a tinted stock Material icon; inactive tabs should show the
   stock icon outline in a muted grey. Confirm this is legible at the actual nav-bar icon size on
   device (never rendered at real size, only compiled).
4. **Scroll on every screen**: Dashboard, Consistency, Onboarding's profile-entry step, Food
   logging, Weight, Settings, and Run history should all scroll if content overflows — Consistency
   and the onboarding profile step had this added during this session's whole-branch review
   specifically because they didn't have it before; confirm neither clips content on the actual
   device screen size.
5. **JetBrains Mono rendering**: confirm the bundled font actually loads and renders (tabular
   figures, monospace body text) rather than silently falling back to the system sans font — this
   would be indistinguishable from a successful build if the font resource failed to bind at
   runtime.
