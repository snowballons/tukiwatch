# Agent Instructions

This repository is the TukiWatch Android app: Kotlin Multiplatform + Compose,
with an on-device Python 3.11 / Streamlink engine bundled through Chaquopy.
There is **no backend service**.

## Repository layout

- `app/androidApp/` — Android host: Compose activity, Chaquopy bridge, Media3
  player, SAF pickers, and `stream_engine.py`.
- `app/shared/` — headless core: engine contract, SQLDelight favourites, JSON
  backup, DataStore preferences, liveness, network-error classification. Keep
  this module free of Compose.
- `app/ui/` — shared Compose screens and plain state controllers.
- `app/desktopApp/` — JVM development harness; not shipped.
- `web/` — static landing/legal site; not part of the Android build.
- `assets/` — README screenshots and brand assets.

## Quality gates

Run the relevant checks from `app/` before finishing a change:

```bash
cd app

# Shared data/engine and UI state
./gradlew :shared:jvmTest :ui:jvmTest

# Android host tests
./gradlew :shared:testAndroidHostTest

# Android build
./gradlew :androidApp:assembleDebug
```

Desktop harness tests:

```bash
# Compose UI tests need a display; use xvfb on headless machines
xvfb-run -a ./gradlew :desktopApp:test
```

Python engine parity, when changing `stream_engine.py` (Python 3.11 +
streamlink 8.6.2):

```bash
python3.11 -m pytest androidApp/src/test/python/test_stream_engine.py
```

The Gradle build defaults to `python3.11`; if it lives elsewhere, pass
`-Pchaquopy.buildPython=/path/to/python3.11` to the Gradle build.

## Constraints

- Do not add a backend, server component, or cloud dependency.
- Do not add JavaScript/Node tooling to the Android app.
- Do not bundle API secrets in the APK.
- Keep `:shared` headless and platform-agnostic where practical.
- Do not commit `local.properties`, keystores, `.env` files, or build output.

## Session completion

When ending a work session, make sure all changes are committed and pushed:

```bash
git add -A
git commit -m "..."
git push
```
