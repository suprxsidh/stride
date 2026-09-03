# stride — parked ideas

- Gym/strength logging module — explicitly deferred v2.0.0 per SPEC.md §3.16.
- Exercise/running tracking (run detection, weekly commitment, consistency grid, weekly review,
  motivation lines) — cut from the app 2026-09-02 to focus on calorie counting alone. Still in
  git history on `master` (commits tagged "refactor: cut weekly commitment..." /
  "refactor: cut running/exercise-session tracking..."); re-scope from there rather than
  rebuilding from scratch when this comes back.
- Open Food Facts barcode/search logging — cut 2026-09-02 alongside the above; Gemini (photo or
  text) is the sole food-logging path for now.
- Phase 4 backlog (per SPEC.md, not yet built, now largely superseded by the above cut): all
  notifications, home-screen widget, guided routines, backup/export.
- Phase 1 Minor polish backlog: all 8 items fixed 2026-08-21 — see BUILD_PLAN.md.
- ~54MB stale Gradle-cache blobs in git history (from early Task 1 commit, reverted but not
  purged) — reclaim via history rewrite before ever pushing repo anywhere, not before.
