# deficit — project constraints

Personal Android weight-loss tracker. Single user (Suprasidh), sideloaded debug APK, target device Vivo X200T (Android 16, OriginOS).

- Spec of record: `SPEC.md` (came from a Fable planning session on claude.ai, 2026-08-10). Do not re-derive requirements — read it.
- Tech stack is fixed in SPEC.md §2 — Kotlin, Compose, Material 3, Health Connect, Room, Ktor/Retrofit, WorkManager. Do not substitute.
- No backend, no accounts, no analytics, no cloud sync — all data stays on-device. Only network calls allowed: Open Food Facts, Gemini (only if user sets a key).
- Execution mode: superpowers:subagent-driven-development, in this repo (git-initialized 2026-08-10).
- Portfolio-level conventions from `~/claudecode-projects/CLAUDE.md` apply (push destination, plan→approval→PoC→verify→scale flow, etc.).
