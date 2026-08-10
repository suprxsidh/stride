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
