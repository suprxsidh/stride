# deficit — project constraints

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
