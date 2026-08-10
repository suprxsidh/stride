# deficit — parked ideas

- Gym/strength logging module — explicitly deferred to v2.0.0 per SPEC.md §3.16.
- Phase 2 (per SPEC.md, not yet scoped into a plan): Health Connect run detection + two-way weigh-in sync, Gemini AI food estimation, all notifications (motivation/meals/weigh-in/posture/backup), home-screen widget, guided routines, backup/export, weekly commitment + consistency grid + weekly review, adaptive budget recalculation.
- Phase 1 Minor polish backlog (filed by the final whole-branch review, 2026-08-10, deliberately left unfixed — see BUILD_PLAN.md for full list): "soft target" label copy, silent validation failures on food/weight forms, over-budget bar visual cap, unused goalWeightKg display, custom-food save-creates-duplicate instead of edit, pin-cap-of-6 silent truncation, missing launcher icon, deprecated Divider()/kotlinOptions{} calls.
- ~54MB of stale Gradle-cache blobs in git history (from an early Task 1 commit, reverted but not purged) — reclaim via history rewrite before ever pushing this repo anywhere, not before.
