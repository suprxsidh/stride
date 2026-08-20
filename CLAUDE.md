# deficit — project constraints

Personal Android weight-loss tracker. Single user (Suprasidh), sideloaded debug APK, target device Vivo X200T (Android 16, OriginOS).

- Spec of record: `SPEC.md` (came from a Fable planning session on claude.ai, 2026-08-10). Do not re-derive requirements — read it.
- Tech stack is fixed in SPEC.md §2 — Kotlin, Compose, Material 3, Health Connect, Room, Ktor/Retrofit, WorkManager. Do not substitute.
- No backend, no accounts, no analytics, no cloud sync — all data stays on-device. Only network calls allowed: Open Food Facts, Gemini (only if user sets a key).
- Execution mode: superpowers:subagent-driven-development, in this repo (git-initialized 2026-08-10).
- Portfolio-level conventions from `~/claudecode-projects/CLAUDE.md` apply (push destination, plan→approval→PoC→verify→scale flow, etc.).

## Visual design direction (locked in 2026-08-13)
Full UI redesign approved: bold & athletic, Oura/Whoop-inspired dark theme, pushed into a running/ledger-specific motif (seven-segment LED readout, punch-card grid, ticket-stub/checkpoint-flag/odometer language) rather than a generic dark-dashboard look. Mockups (3 representative screens: dashboard, consistency, onboarding) live as an HTML/CSS artifact, not committed to the repo — reference the conversation that produced them, or rebuild from this description, before starting Compose implementation. No image-generation tool is available in this harness; default to an HTML/CSS artifact mockup for any future mobile visual-direction work instead of the `imagegen-frontend-*` skills.

## Local toolchain (2026-08-10)
No system-default `java`/`ANDROID_HOME` on this machine — both are present via Homebrew but unlinked. Every implementer/reviewer subagent MUST use these, not system defaults:
- `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` (OpenJDK 21.0.11)
- `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` (build-tools 34.0.0/36.0.0, platforms 34/35/36, platform-tools already installed, licenses already accepted)
- Gradle binary: `/opt/homebrew/bin/gradle` (project has no wrapper yet — Task 1 generates `./gradlew` via `gradle wrapper`)
- Baked into the project: `local.properties` (`sdk.dir=...`) and `gradle.properties` (`org.gradle.java.home=...`) — set by Task 1, must not be deleted by later tasks. **Correction (found during Task 4):** this covers the JVM Gradle uses to actually run the build, but NOT the `./gradlew` wrapper script's own bootstrap step — that step needs a working `java` on `PATH` or `JAVA_HOME` set *before* Gradle ever reads `gradle.properties`, and macOS's system `java` stub fails immediately. Every implementer subagent must run `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` in its shell before the first `./gradlew` call each task.
- **AGP/Gradle version (bumped during Phase 2 Task 3, 2026-08-11):** AGP 8.7.3→**8.11.1**, Gradle wrapper 8.9→**8.13**. Forced by adding `androidx.health.connect:connect-client:1.1.0` — that AAR's metadata floor requires AGP ≥8.9.1, and the project's `compileSdk = 36` (set in Task 1) needs AGP ≥8.11 for full API-36 support. Verified safe: full existing test suite (19 classes) stayed green and `assembleDebug` succeeded after the bump. No other toolchain paths changed — `JAVA_HOME`/`ANDROID_HOME` above are unaffected.
