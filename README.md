# playandlearn (Android)

Aplikasi Android Play & Learn (anak 4–7 th + mode pendamping orang tua). Kotlin, Jetpack Compose, offline-first.

- Rencana: [docs/PLAN.md](docs/PLAN.md) · Kontrak API: repo `playandlearn-portal/contracts/openapi.yaml`
- `:core` — logika domain murni Kotlin (mis. `ScreenTimeGuard`), diuji tanpa Android SDK: `gradle :core:test`
- `:app` — aplikasi Android (butuh Android SDK; otomatis disertakan bila `ANDROID_HOME`/`local.properties` ada)

Status: Increment 0 (fondasi). `applicationId` masih placeholder.
