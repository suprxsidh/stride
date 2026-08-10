# Build Plan: "Deficit" — Personal Weight-Loss Tracker (Android)

You are building a personal Android app for a single user. Read this entire document before writing any code. Use subagents for parallel workstreams as described in the Execution Plan section. The final deliverable is a debug APK the user sideloads onto a Vivo X200T (Android 16).

## 1. Purpose & Philosophy

The user wants to lose ~10 kg through daily running (~30–35 min, auto-detected by a Galaxy Watch 4 → Samsung Health → Health Connect) and a sustained calorie deficit.

**Core design philosophy — conservative accounting, no false precision:**

- **Overestimate calories in:** every logged food gets a +10% buffer applied automatically. Store the raw value, display and budget against the buffered value.
- **Underestimate calories out:** exercise calories from Health Connect are counted at **50%** toward the daily budget. Show the real number for information, but only credit half.
- **No projected goal date. No "you'll reach X kg by Y."** The app tracks consistency and trend, never deadlines.
- **No streak-shaming.** Missed days are shown neutrally in the consistency grid, never with guilt copy or red alarm colors.

## 2. Tech Stack (fixed — do not substitute)

- Kotlin 2.x, Jetpack Compose, Material 3 (dark theme default: pure black `#000000` background with a single saturated accent color — user preference, OLED phone)
- `androidx.health.connect:connect-client` for Health Connect
- Room for local persistence (all data stays on-device; no backend, no accounts, no analytics)
- Ktor client or Retrofit for the Open Food Facts API
- WorkManager for periodic Health Connect sync
- minSdk 28, targetSdk latest stable. Single-module app is fine; keep packages clean (`data/`, `health/`, `food/`, `ui/`)

## 3. Features

### 3.1 Onboarding (first launch only)
- Inputs: height (cm), weight (kg), age, sex, goal weight (optional, default = current − 10)
- Compute BMR via Mifflin-St Jeor. TDEE = BMR × **1.2 (sedentary multiplier, always)** — running is credited separately at 50%, so do not bake activity into TDEE.
- Daily soft budget = TDEE − 500, floored at 1,500 kcal. Label it a "soft target" in the UI. Editable later in Settings.

### 3.2 Dashboard (home screen)
- Today: buffered calories logged vs. soft budget, shown as a simple bar (not a scary countdown)
- Today's run, if detected: duration, distance, real calories, and "credited: X kcal (50%)"
- Weekly run widget + motivation card (see 3.7)
- Weight sparkline: last 30 days of 7-day rolling average
- One-tap quick-add food button

### 3.3 Food logging
Four paths, all landing in the same log:
1. **AI estimate (primary path):** user types a free-text meal description ("2 rotis, dal tadka, cucumber salad") or attaches a photo. Send it to the **Gemini API** (Flash-tier model) with a system prompt that: (a) returns strict JSON `{items: [{name, kcal}], totalKcal, confidence}`, (b) instructs the model to **estimate high whenever uncertain** — portion ambiguity always resolves upward, (c) assumes Indian home-cooking defaults (ghee/oil used, standard portion sizes) unless told otherwise. Show the itemized estimate for one-tap confirm or edit before saving. The user supplies their own Gemini API key in Settings, stored locally only. No key or no network → this path hides, everything else still works.
2. **Quick add:** name + calories typed manually
3. **Open Food Facts search:** for barcoded/packaged items. Query the v2 API with `countries_tags_en=india` bias (not exclusive filter). Cache results locally.
4. **Custom foods:** user-defined foods with per-serving calories, fractional servings supported.

**Snack presets:** the user snacks often by design (planned snacking is part of the strategy). Support pinning up to 6 custom foods as one-tap buttons on the logging screen (e.g., chaas, roasted chana, fruit, curd). Logging a pinned snack must take ≤2 taps.

The +10% buffer applies to all paths automatically — including on top of Gemini's already-high estimates. Show a small line like "logged 450 → counted 495" so the buffer is transparent, not magic.

Per-food macros are NOT required. Calories only. Keep logging under 10 seconds.

### 3.4 Health Connect integration
- Request read permissions: `ExerciseSessionRecord`, `StepsRecord`, `TotalCaloriesBurnedRecord`, `DistanceRecord`, `SpeedRecord`, `HeartRateRecord`, `WeightRecord`. Request write for `WeightRecord` (so weigh-ins entered here appear in Samsung Health).
- **Per-run analytics:** for each exercise session, also read associated distance, speed, and heart-rate records in the session's time range to compute avg pace, avg/max HR. Show these on a run detail screen, plus simple cross-run trends (pace over time, avg HR at similar pace). **Do NOT attempt to read `ExerciseRoute` / GPS routes — Samsung Health does not expose routes via Health Connect (requires a Samsung partnership SDK). No maps in this app; skip gracefully.**
- Weigh-ins are two-way: manual entry writes to Health Connect; any `WeightRecord` appearing in Health Connect (Samsung Health entry, smart scale) imports automatically and feeds the rolling average.
- On app open + every 60 min via WorkManager: pull new exercise sessions and steps since last sync token (use the Changes API with a stored changes token; fall back to time-range read if the token expires).
- Dedupe sessions by Health Connect record ID.
- Handle the "Health Connect unavailable / permissions revoked" states with a clear settings deep-link (`ACTION_HEALTH_CONNECT_SETTINGS`).
- Onboarding must include a one-time setup checklist screen telling the user to enable Samsung Health → Settings → Data management → Health Connect sync, and grant Exercise permission there. Sync latency is 30–60 min; opening Samsung Health forces it — say this in the UI.

### 3.5 Weight tracking
- Manual weigh-in entry (writes to Health Connect too)
- Chart: raw points faint, 7-day rolling average bold. This is the primary progress signal of the whole app.
- Show total change since start, and 4-week trend direction. Never show a projection.

### 3.6 Consistency view
- A monthly grid (calendar heat-map style), one cell per day, three subtle indicators per day: ran / logged food / finished under soft budget
- Weekly summary row: "5/7 days ran, 6/7 logged, avg deficit ~X kcal"

### 3.7 Weekly commitment & motivation
The run rule is **weekly, never daily** — no daily streaks anywhere in the app (the "running streak" on the dashboard from 3.2 is replaced by this system).

- Week = Monday–Sunday. **Target: 4 runs. Hard floor: 3 runs.** Both editable in Settings.
- Any run on any day counts equally — a missed weekday made up on the weekend is indistinguishable from a normal week. The system must never reference *which* days.
- Dashboard shows the week state plainly: "2/4 runs · 4 days left."
- **Floor-risk escalation:** the only time the app gets loud. When `runsRemaining to reach floor > daysLeft − 1` (i.e., the floor is at risk of becoming impossible), switch the weekly widget to a warning state with direct copy: "Run today or tomorrow to protect your floor." If the floor becomes mathematically impossible, say so once, flatly, and refocus on next week.
- A completed week below the floor is marked **broken** in the consistency grid — the only place failure is ever surfaced. Weeks at 3 are fine, weeks at 4+ get a subtle highlight. Track "consecutive weeks with floor intact" as the headline streak stat.
- **Data-driven motivation, no canned quotes.** A dashboard motivation card generates one line per day from real data, priority-ordered: floor at risk > weekly progress > floor-intact streak > rolling-average weight change > total runs logged. Examples: "Floor unbroken 5 weeks running." / "Rolling average down 1.8 kg since you started." Rotate deterministically; never show the same stat two days in a row if another is available.
- One optional daily notification (user-set time, default off) carrying that day's motivation line + week state.

### 3.8 Home screen widget
A Glance (Jetpack Compose) app widget, small and OLED-friendly (pure black background, single accent): week state ("2/4 · 3 days left") on top, today's buffered calories vs soft budget below. Switches to the floor-risk warning state in sync with the dashboard. Tapping opens the app. One size is enough; keep it battery-cheap (update on data change + WorkManager sync, no aggressive polling).

### 3.9 Adaptive budget
TDEE and the soft budget must not go stale as weight drops. Recompute TDEE weekly from the **7-day rolling average weight** (not raw weigh-ins) and adjust the soft budget automatically, keeping the −500 deficit and the 1,500 kcal floor. Show a quiet one-line note in the weekly review when it changes ("Budget adjusted to 1,850 — your body burns less at this weight, this keeps the deficit real"). Manual override in Settings still wins.

### 3.10 Weekly review
Auto-generated every Monday for the week just ended, shown as a card on first open (and stored in a history list): runs vs floor, days food-logged, average daily deficit, rolling-average weight change, budget adjustment if any. Neutral tone; a broken week is stated once here, then the slate resets. This is the only place week-over-week comparison happens.

### 3.11 Backup & export
- One-tap backup: copy the Room DB to user-picked location via SAF (Storage Access Framework) — Downloads, Drive, wherever. Matching restore-from-backup in Settings.
- CSV export: food log, runs, and weigh-ins as three CSVs in a zip.
- Optional monthly backup reminder (default off).

### 3.12 Guided routines (mobility, warm-up, cool-down)
The user is desk-bound with anterior pelvic tilt and rounded shoulders. Build a **routine player**: a routine is an ordered list of timed steps, each with an exercise name, a one-line form cue, and a countdown timer with an audible beep and auto-advance (hands-free once started). Keep-screen-on during playback. Pause/skip controls. Routines are editable (reorder, retime, swap exercises), with these three seeded as defaults:

**Morning Mobility (~10 min):**
1. Couch stretch (or kneeling hip flexor stretch) — 60s/side — "squeeze the glute of the back leg, ribs down"
2. Glute bridges — 60s — "push through heels, pause 2s at top"
3. Dead bugs — 60s — "lower back stays glued to floor"
4. Cat-cow — 45s — "move slowly with breath"
5. Doorway pec stretch — 45s/side — "forearm on frame, step through gently"
6. Wall slides — 60s — "back and arms against wall, slide up without arching"
7. Chin tucks — 30s — "make a double chin, hold 3s each"
8. Thoracic extension over chair back — 45s — "hands behind head, extend upper back only"

**Pre-Run Warm-up (~5 min):**
1. Brisk walk — 90s
2. Leg swings front-back — 30s/side — "hold something for balance"
3. Leg swings side-to-side — 30s/side
4. Walking lunges — 45s — "torso tall, knee tracks over toes"
5. High knees — 30s — "light and quick"
6. Ankle circles — 15s/side
7. Glute bridges — 30s — "wake the glutes before the run"

**Post-Run Cool-down (~5 min):**
1. Easy walk — 90s — "let heart rate drop"
2. Standing quad stretch — 30s/side
3. Standing calf stretch — 30s/side
4. Standing hamstring stretch — 30s/side
5. Kneeling hip flexor stretch — 45s/side — "best APT window: muscles are warm"

Completion is logged silently (for the user's own curiosity in history) but has **no streak, goal, ring, or nudge** — the run floor remains the app's only commitment.

### 3.13 Posture-break reminder (optional)
A single optional reminder: every N minutes (default 60) within user-set work hours on weekdays, a low-priority notification — "Stand up. 30 seconds." Default **off**. Never tracked, never counted, no completion state. Snoozes silently on dismissal until the next interval.

### 3.14 Meal logging reminders
User sets meal times in Settings (defaults: breakfast 9:00, lunch 13:30, dinner 20:30 — all editable, add/remove meals) and a lead time (30–60 min, default 45). Before each meal time, fire a quiet notification: "Lunch soon — snap a photo when you eat." Tapping opens the AI logging screen (3.3 path 1) with camera immediately accessible. Late logging is a first-class flow: typing a meal description hours after eating must work identically. No missed-meal tracking, no "you didn't log" guilt notifications — the reminder fires and that's it.

### 3.15 Weigh-in reminder
A daily notification at a user-set time (default 7:00 AM, matching a morning weigh-in habit): "Morning weigh-in." Two friction-killers are mandatory:
1. **Direct reply:** the notification has an inline text field — the user types "81.4" straight into the notification and it's logged (and written to Health Connect) without ever opening the app. Tapping the notification body opens the weigh-in screen instead, with the number pad up and yesterday's weight prefilled for quick adjustment.
2. **Smart suppression:** if a weigh-in already exists for today (typed here, or imported from Health Connect via Samsung Health/smart scale), the notification is silently skipped. Never remind someone to do a thing they already did.
No missed-day tracking — the rolling average is robust to gaps, so a skipped day costs nothing and the app never mentions it.

### 3.16 Explicitly out of scope (do not build)
- Gym/strength logging (planned later — leave a Room schema comment, nothing more)
- Social features, cloud sync, macros, water tracking, daily streaks of any kind. Notifications are limited to exactly: daily motivation (3.7), posture breaks (3.13), meal reminders (3.14), weigh-in reminder (3.15), optional monthly backup reminder (3.11) — nothing else, ever. Each type gets its own Android notification channel so any one can be muted at the OS level without touching the others.
- **GPS route maps** — not accessible from Samsung Health via Health Connect; user will use Strava for maps
- **Daily step goals.** Steps from Health Connect may be shown passively (small stat on the dashboard) but must never have a target, ring, or nudge attached — the run floor is the only commitment in this app
- Gym module reserved for v2.0.0

## 4. Friction & Resilience Rules (cross-cutting — every agent reads this)

These rules exist because the app dies the day it becomes annoying or unreliable. Treat them as requirements, not suggestions.

**4.1 Vivo/OriginOS background survival (CRITICAL).** The target device is a Vivo X200T running OriginOS, which kills background apps aggressively — WorkManager syncs and scheduled reminders WILL silently die under default settings, and the app will appear broken. The onboarding checklist MUST include a step that deep-links to the app's battery settings and instructs the user to: set battery usage to unrestricted / disable optimization, and enable auto-start (Vivo's i Manager). Detect `isIgnoringBatteryOptimizations()` and re-surface this checklist item if it regresses. Use `AlarmManager` exact alarms (request `SCHEDULE_EXACT_ALARM`) for all time-based reminders — WorkManager alone is not reliable enough for reminders on this OEM; WorkManager is for sync only.

**4.2 Direct-reply meal logging.** The meal reminder notification (3.14) carries two actions: "Type it" (inline direct reply — the user types "2 rotis and chole" into the notification; the app calls Gemini in the background, applies the buffer, logs it, and posts a low-key confirmation notification showing the counted kcal with a tap-to-edit) and "Camera" (opens straight into photo capture). The full log-a-meal loop must be achievable without ever opening the app.

**4.3 Nothing typed is ever lost.** If a Gemini call fails (timeout, quota, airplane mode), the typed description or photo is saved as a **pending draft**, visible on the dashboard, retried automatically on next connectivity, with a manual "just quick-add it" escape. Same for Health Connect writes: queue and retry.

**4.4 Day boundary = 3:00 AM.** Food logged between midnight and 3 AM counts toward the previous day (late dinner reality). All "today" queries, the consistency grid, and the weekly review respect this boundary consistently.

**4.5 Widget tap zones.** The widget's week-state area opens the dashboard; the calorie area opens the food-logging screen directly. From home screen to typing a meal: one tap.

**4.6 Persistent setup checklist.** Until every setup item is done (Health Connect permissions, Samsung Health → HC sync confirmed by at least one imported record, battery optimization off, notifications permitted, widget placed [detectable], Gemini key [skippable]), a checklist card sits at the top of the dashboard showing exactly what's left, each item deep-linking to the right screen. It disappears forever when complete — no nagging afterward.

**4.7 Silent auto-backup.** After the user picks a backup folder once (SAF with persisted URI permission), auto-backup the DB there weekly, keeping the last 8, silently. Manual backup/restore (3.11) still exists; the monthly reminder only fires if no folder was ever picked.

**4.8 Defaults everywhere.** The only required input in the entire app is onboarding (height, weight, age, sex). Every other feature works out of the box with the defaults in this document and is configurable later. Never block a flow on configuration.

**4.9 Sensible empty/error states.** Every screen must render meaningfully with zero data (day 1) and with permissions missing — always saying what to do next, never a blank panel or spinner-forever.

## 5. Data Layer (Room)

Entities (adjust as needed, but keep this shape):
- `UserProfile(id=1, heightCm, weightKgAtStart, age, sex, goalWeightKg, softBudgetKcal, createdAt)`
- `FoodEntry(id, date, name, rawKcal, bufferedKcal, source[AI|QUICK|OFF|CUSTOM], offBarcode?, loggedAt)`
- `PendingDraft(id, type[MEAL_TEXT|MEAL_PHOTO], payload, createdAt, retryCount)`
- `CustomFood(id, name, kcalPerServing, servingLabel)`
- `ExerciseSession(id, hcRecordId UNIQUE, date, type, startTime, durationMin, distanceM?, avgPaceSecPerKm?, avgHr?, maxHr?, kcalReal, kcalCredited)`
- `MealTime(id, label, timeOfDay, reminderEnabled)` with lead-time minutes in `AppSettings`
- `WeighIn(id, date, weightKg, syncedToHc)`
- `SyncState(id=1, hcChangesToken?, lastSyncAt)`
- `AppSettings(id=1, geminiApiKey?, weeklyTarget=4, weeklyFloor=3, notificationTime?, weighInReminderTime='07:00', weighInReminderEnabled=true, postureReminderEnabled=false, postureIntervalMin=60, workHoursStart?, workHoursEnd?, backupFolderUri?, softBudgetKcal)`
- `Routine(id, name, isSeeded)` and `RoutineStep(id, routineId, position, name, cue, durationSec, perSide)`
- `RoutineCompletion(id, routineId, completedAt)` — history only, never surfaced as a goal
- `CustomFood` gains `isPinned: Boolean` for snack presets

Weekly stats (runs this week, floor-intact streak, broken weeks) are **derived** from `ExerciseSession` queries, not stored — compute in the repository layer so a late-synced run retroactively fixes a week.

DAOs with Flow-based queries for the dashboard.

## 6. Execution Plan (subagents)

Run these as parallel subagents where dependencies allow. Each agent must leave the project compiling. **Every agent reads Section 4 before starting.**

1. **Scaffold agent:** Gradle project, dependencies, theme (black + accent), navigation shell with empty screens, manifest with Health Connect permission declarations, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, and the required `<intent-filter>` for `ACTION_SHOW_PERMISSIONS_RATIONALE`. Notification channels defined per type.
2. **Data agent:** Room entities, DAOs, database, repository interfaces. Unit tests for the buffer math (+10%, 50% credit), rolling-average calculation, and the 3 AM day-boundary logic.
3. **Health Connect agent:** permission flow, Changes-API sync, WorkManager job, session mapping → `ExerciseSession`, write-queue with retry. Build against the SDK; note anything untestable without a device in `TESTING.md`.
4. **Food agent:** Gemini estimation client (strict-JSON prompt, estimate-high instruction, photo + text input, graceful no-key/offline fallback, pending-draft queue with auto-retry), Open Food Facts client with caching, custom foods CRUD + snack-preset pinning, logging flows, direct-reply background logging pipeline.
5. **UI agent:** all screens per Section 3 including the weekly commitment widget, floor-risk states, motivation card, all notification scheduling via exact alarms (motivation, meals with direct-reply + camera actions, weigh-in with direct-reply + smart suppression, posture, backup), the Glance home-screen widget with split tap zones, persistent setup checklist card, weekly review card + history, backup/export/restore via SAF + silent weekly auto-backup, and the routine player (timers, beep, auto-advance, keep-screen-on, seeded routines). Charts can use a lightweight Compose chart lib or hand-drawn Canvas — no heavyweight dependencies. Unit tests for the floor-risk math, week derivation (including a run syncing in late and repairing a week), adaptive budget recalculation, and weigh-in suppression logic.
6. **Integration agent (last, sequential):** wire everything, resolve conflicts, run `./gradlew assembleDebug`, fix until green, write `TESTING.md` (what to verify on-device: permission grant flow, battery-optimization exemption on OriginOS, a run appearing after Samsung Health sync, direct-reply meal and weigh-in logging from notifications, OFF search, weigh-in write-back, pending-draft retry after airplane mode).

## 7. Definition of Done

- `./gradlew assembleDebug` succeeds; APK path reported to the user
- App runs with Health Connect permissions denied (degrades gracefully to manual-only) and with no Gemini key (AI path hidden, all else works)
- Buffer math, 50%-credit math, floor-risk logic, week derivation, day-boundary logic, and weigh-in suppression covered by passing unit tests
- A meal and a weigh-in can each be logged end-to-end without opening the app (direct reply from notifications)
- No typed input or photo is ever lost to a network/API failure (pending-draft path verified)
- No network calls except Open Food Facts and Gemini (only when a key is set); no data leaves the device otherwise; the Gemini key never leaves local storage
- `TESTING.md` lists the on-device verification steps including the Samsung Health → Health Connect setup checklist, the OriginOS battery-settings walkthrough, widget placement, and a backup → wipe → restore round-trip
