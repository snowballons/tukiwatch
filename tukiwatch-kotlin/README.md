# TukiWatch (Android)

Android app that resolves and plays live streams **on-device** — Streamlink runs in the APK via
Chaquopy, no backend server. Kotlin Multiplatform + Compose.

Plan: [`TUKIWATCH_KMP_PORTING_PLAN.md`](../../../kotlin-python-app/TUKIWATCH_KMP_PORTING_PLAN.md)
(local workspace copy; it lives outside this repository).

## Status

**Phases 0–2 landed; Phase 3 (UI) in progress.** The on-device engine and data layer are in place and
covered by tests. `:ui` now holds the shared Compose surface, and the Android player + My List use it;
navigation and the full screen map are still Phase 3.

| Phase | Scope | State |
|---|---|---|
| 0 | Engine transplant into this repo | **done** |
| 1 | Engine parity: metadata, error taxonomy, codec alignment | **done** |
| 2 | Data layer: SQLDelight favorites, DataStore, JSON import, liveness | **done** (data layer; UI wiring partial) |
| 3 | UI: Home / My List / Add / Player / Settings / Onboarding | pending |

## Modules

- `shared` — headless core: `StreamEngine` interface,
  `EngineResult`/`StreamStatus`/`ErrorDetails`/`StreamItem` (`@Serializable`, snake_case wire
  contract), SQLDelight favorites + JSON backup, DataStore prefs, `LivenessRepository`, network error
  classification. No Compose dependency. `androidMain` throws from `createStreamEngine()` by design —
  Android uses `ChaquopyStreamEngine(context)`.
- `ui` — shared Compose surface: `TukiWatchTheme`/palette, status pill, metadata card, error card,
  My List row and quality chips. Android is the reference implementation; desktop renders the same
  components.
- `androidApp` — the shipped APK. Chaquopy + Streamlink, Media3 ExoPlayer, the rotation-safe
  `StreamViewModel`, and `stream_engine.py`.
- `desktopApp` — JVM iteration harness. A real Compose window over the same
  `SubprocessStreamEngine`, `FavoritesRepository` and `LivenessRepository` as the app, **not shipped**.
  It resolves URLs, shows metadata/errors/qualities/timings, and exercises My List (file-backed SQLite
  at `~/.tukiwatch/harness.db`) plus on-demand liveness.

## Running

```sh
./gradlew :androidApp:assembleDebug   # APK
./gradlew :desktopApp:run             # desktop harness
```

Tests:

```sh
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
./gradlew :desktopApp:test            # harness unit + Compose UI tests (needs a display for UI)
# Phase 1 Python parity suite (needs a Python 3.11 with streamlink installed).
# Use 8.6.2 to match the version bundled in the APK.
python -m pytest androidApp/src/main/python/test_stream_engine.py
```

The desktop UI tests render a real `ComposeWindow`; run them under `DISPLAY=:0` or
`xvfb-run -a ./gradlew :desktopApp:test`. They skip themselves when no display can be opened.

The liveness ticker runs only while the activity is `STARTED`
(`repeatOnLifecycle`), and transport failures are surfaced through
`error_details.error_class = "network"` so the offline banner can fire without
treating them as plugin errors.

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

Desktop iteration auto-discovers a Streamlink-capable interpreter
(`PYKOT_PYTHON` → repo `.venv` / `tukiwatch-core/.venv` → `python3.11`/`python3`) and
`androidApp/src/main/python/stream_engine.py` (`PYKOT_SCRIPT`, otherwise found by walking up from
the working directory). `PYKOT_PYTHON`/`PYKOT_SCRIPT` still override everything.

## Locked decisions (from the plan, §0)

Android only. Twitch-led. No server — Polar licensing, session tokens, rate limiting, Redis and all
deployment artifacts are deleted, not ported. **No API secrets in the APK**: anything shipped is
extractable, so Helix access is BYO-key only.
