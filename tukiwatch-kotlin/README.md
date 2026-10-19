# TukiWatch (Android)

Android app that resolves and plays live streams **on-device** — Streamlink runs in the APK via
Chaquopy, no backend server. Kotlin Multiplatform + Compose.

Plan: [`TUKIWATCH_KMP_PORTING_PLAN.md`](../kotlin-python-app/TUKIWATCH_KMP_PORTING_PLAN.md).

## Status

**Phase 0 — engine foundation.** The on-device engine layer is in place and device-tested against the
prototype (`../kotlin-python-app/py-kot-app`). Nothing else is built yet: no favorites, no navigation,
no discovery, no settings.

| Phase | Scope | State |
|---|---|---|
| 0 | Engine transplant into this repo | **done** |
| 1 | Engine parity: metadata, error taxonomy, codec alignment | pending |
| 2 | Data layer: Room favorites, DataStore, Ktor, migration import | pending |
| 3 | UI: Home / My List / Add / Player / Settings / Onboarding | pending |

## Modules

- `shared` — `StreamEngine` interface, `EngineResult`/`StreamItem`/`EngineTimings` (`@Serializable`),
  plus the Compose Multiplatform UI shell. `androidMain` throws from `createStreamEngine()` by
  design — Android uses `ChaquopyStreamEngine(context)`.
- `androidApp` — the shipped APK. Chaquopy + Streamlink, Media3 ExoPlayer, the rotation-safe
  `StreamViewModel`, and `stream_engine.py`.
- `desktopApp` — JVM iteration harness only. `SubprocessStreamEngine` shells out to a local Python;
  **not shipped**.

## Running

```sh
./gradlew :androidApp:assembleDebug   # APK
./gradlew :desktopApp:run             # desktop iteration
```

Tests:

```sh
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
```

## Toolchain pins

| Component | Version |
|---|---|
| Chaquopy | 17.0.0 |
| Python | 3.11 |
| Streamlink | 8.6.2 |
| Media3 | 1.11.1 |
| Kotlin | 2.4.20 |
| AGP | 9.1.1 |
| Compose Multiplatform | 1.12.1 |
| minSdk / targetSdk | 26 / 37 |

`minSdk 26` is fixed by the Python 3.11 ABI split (`armeabi-v7a` + `arm64-v8a`). `buildPython` is
pinned to `/home/jonnie/.local/bin/python3.11`; override with
`-Pchaquopy.buildPython=/path/to/python3.11` on other machines.

Desktop iteration reads `PYKOT_SCRIPT` / `PYKOT_PYTHON`; defaults point at this repo's
`androidApp/src/main/python/stream_engine.py`.

## Locked decisions (from the plan, §0)

Android only. Twitch-led. No server — Polar licensing, session tokens, rate limiting, Redis and all
deployment artifacts are deleted, not ported. **No API secrets in the APK**: anything shipped is
extractable, so Helix access is BYO-key only.
