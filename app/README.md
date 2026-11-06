# TukiWatch (Android)

Android app that resolves and plays live streams **on-device** — Streamlink runs
inside the APK via Chaquopy, with no backend server. Kotlin Multiplatform +
Compose.

Current version: **1.1.5** (`versionCode 13`), declared in
[`gradle.properties`](gradle.properties) and consumed by the Android and desktop
builds.

## Status

Phases 0–5B are in place: the on-device engine, data layer, navigation shell,
screens, visual polish, media/system essentials, and gesture/feedback work.
`:ui` holds the shared Compose surface and state controllers; Android wires the
ExoPlayer surface, SAF pickers, lifecycle, and system intents.

| Phase | Scope | State |
|---|---|---|
| 0 | Engine transplant into this repo | **done** |
| 1 | Engine parity: metadata, error taxonomy, codec alignment | **done** |
| 2 | Data layer: SQLDelight favourites, DataStore, JSON import, liveness | **done** |
| 3 | UI: Home / My List / Add / Player / Settings / Onboarding | **done** |
| 4A/4B | Visual polish, modals, splash, extended player/add flows | **done** |
| 5A/5B | Media/system essentials, gestures, and feedback | **done** |

## Modules

- `shared` — headless core: `StreamEngine` interface,
  `EngineResult`/`StreamStatus`/`ErrorDetails`/`StreamItem` (`@Serializable`,
  snake_case wire contract), SQLDelight favourites + JSON backup, DataStore
  preferences, `LivenessRepository`, and network-error classification. No Compose
  dependency. `androidMain` throws from `createStreamEngine()` by design —
  Android uses `ChaquopyStreamEngine(context)`.
- `ui` — shared Compose surface and plain state holders. The `theme` package
  owns the role-based `TukiPalette` contract, the curated `TukiThemes` set and
  the `tukiPaletteFor` resolver, applied by `TukiWatchTheme`. Plus status pill,
  metadata card, error card, My List row, quality chips, `StreamController`
  (resolve, cache, quality ordering, background pause, live-edge recovery,
  codec fallback) and `LibraryController` (favourites membership, JSON backup,
  liveness state). Android wraps them in thin ViewModels; desktop renders the
  same components.
- `androidApp` — the shipped APK. Chaquopy + Streamlink, Media3 ExoPlayer, thin
  `StreamViewModel`/`LibraryViewModel` wrappers over `:ui`, SAF file pickers, and
  `stream_engine.py`.
- `desktopApp` — JVM iteration harness. A real Compose window over the same
  `SubprocessStreamEngine`, `FavoritesRepository`, and `LivenessRepository` as
  the app, **not shipped**. It resolves URLs, shows metadata/errors/qualities/
  timings, and exercises My List (file-backed SQLite at
  `~/.tukiwatch/harness.db`) plus on-demand liveness.

## Running

```sh
./gradlew :androidApp:assembleDebug   # APK
./gradlew :desktopApp:run             # desktop harness
```

The debug APK is at
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Chaquopy requires Python 3.11. The build defaults to `python3.11`; pass the
interpreter explicitly if it lives elsewhere:

```sh
./gradlew :androidApp:assembleDebug -Pchaquopy.buildPython=/path/to/python3.11
```

## Tests

```sh
./gradlew :shared:testAndroidHostTest
./gradlew :shared:jvmTest
./gradlew :ui:jvmTest                 # shared state-holder tests
./gradlew :desktopApp:test            # harness unit + Compose UI tests (needs a display)
```

The desktop UI tests render a real `ComposeWindow`; run them under `DISPLAY=:0`
or `xvfb-run -a ./gradlew :desktopApp:test`. They skip themselves when no display
can be opened.

Python engine parity (needs Python 3.11 with Streamlink 8.6.2, matching the
version bundled in the APK):

```sh
python3.11 -m pytest androidApp/src/test/python/test_stream_engine.py
```

The engine lives in `androidApp/src/main/python`; its parity test lives in
`androidApp/src/test/python` so Chaquopy does not package it into the APK.

## Python engine

[`androidApp/src/main/python/stream_engine.py`](androidApp/src/main/python/stream_engine.py)
exposes the entrypoints used by the Android bridge and the desktop harness:

- `resolve_details(url, options_json, wanted)`
- `check_status(url, options_json)`
- `list_streams(url, options_json, wanted)`
- `warmup(url)`
- `version()`

It returns JSON with the same snake_case contract consumed by the Kotlin
`@Serializable` models. Streamlink `8.6.2` is installed into the APK at build
time by Chaquopy; Python 3.11 is bundled per ABI.

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
| Gradle | 9.5.1 |
| Compose Multiplatform | 1.12.1 |
| minSdk / targetSdk | 26 / 37 |

`minSdk 26` is fixed by the Python 3.11 ABI split (`armeabi-v7a` +
`arm64-v8a`).

Desktop iteration auto-discovers a Streamlink-capable interpreter
(`PYKOT_PYTHON` → repo `.venv` / `tukiwatch-core/.venv` → `python3.11` /
`python3`) and `androidApp/src/main/python/stream_engine.py` (`PYKOT_SCRIPT`,
otherwise found by walking up from the working directory). `PYKOT_PYTHON` /
`PYKOT_SCRIPT` still override everything.

## Constraints

Android only. No server, accounts, cloud sync, or bundled API secrets. Stream
resolution and playback are on-device; anything shipped in the APK is
extractable, so optional platform credentials are user-supplied only.

See the root [`README.md`](../README.md) for the project overview and
[`CONTRIBUTING.md`](../CONTRIBUTING.md) for development setup.
