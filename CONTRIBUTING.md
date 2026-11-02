# Contributing to TukiWatch

Thanks for considering a contribution. TukiWatch is a free, open-source Android
app that resolves and plays live streams entirely on-device. There is no server
component to run.

## Development setup

### Prerequisites

- **JDK 21** for the Gradle toolchain
- **Android SDK 37** with platform tools; set `ANDROID_HOME` or create
  `app/local.properties` with `sdk.dir=/path/to/android-sdk`
- **Python 3.11** for Chaquopy’s build-time Python
- **Streamlink 8.6.2** in that Python 3.11 environment if you want to run the
  host-side Python parity tests

No JavaScript build toolchain is used for the app.

### Build

```bash
git clone https://github.com/snowballons/tukiwatch.git
cd tukiwatch/app

./gradlew :androidApp:assembleDebug
```

The debug APK is written to
`app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

If `python3` on your `PATH` is not Python 3.11, point Chaquopy at the correct
interpreter:

```bash
./gradlew :androidApp:assembleDebug \
  -Pchaquopy.buildPython=/path/to/python3.11
```

### Desktop harness

The JVM harness is useful for iterating on Compose UI without an emulator or
device:

```bash
cd app
./gradlew :desktopApp:run
```

It uses a host Python interpreter and the same `stream_engine.py` as the APK. It
is not a shipped target.

## Quality gates

Run these from `app/` before opening a pull request:

```bash
./gradlew :shared:jvmTest :ui:jvmTest
./gradlew :shared:testAndroidHostTest
./gradlew :androidApp:assembleDebug
```

Desktop UI tests need a display:

```bash
xvfb-run -a ./gradlew :desktopApp:test
```

If you change the embedded engine, also run the Python parity suite with Python
3.11 and Streamlink 8.6.2:

```bash
python3.11 -m pytest androidApp/src/main/python/test_stream_engine.py
```

## Project structure

- `app/androidApp/` — Android host: activity, Compose surface, Chaquopy bridge,
  Media3 player, SAF file pickers, `stream_engine.py`.
- `app/shared/` — engine contract and data layer: SQLDelight favourites, JSON
  backup, DataStore preferences, liveness, network-error classification. No
  Compose dependency.
- `app/ui/` — shared Compose screens and plain state controllers.
- `app/desktopApp/` — Compose Desktop development harness; not shipped.
- `web/` — static landing and legal pages, deployed separately.
- `assets/` — README screenshots and brand assets.

## Data and privacy

TukiWatch is local-first by design:

- favourites are stored in an on-device SQLite database via SQLDelight;
- preferences are stored in DataStore;
- there are no accounts, cloud sync, or analytics;
- no backend service is contacted.

Keep new features local-first. Do not add a cloud dependency or bundle API
secrets in the APK. Optional platform credentials must remain user-supplied and
stored locally.

## Style notes

- Kotlin official code style; keep Compose state holders plain and testable.
- Keep `:shared` free of Compose and Android UI dependencies where practical.
- Keep the Python engine’s JSON contract (`@SerialName` snake_case fields) in
  sync with the Kotlin models.
- Prefer small, focused changes and comments only where they add context.
- Do not commit `local.properties`, keystores, `.env` files, or build output.

## Pull requests

1. Open an issue first for larger changes, or pick an existing one.
2. Fork the repository and create a focused branch (`feat/...`, `fix/...`).
3. Make the change and run the relevant quality gates.
4. Use clear conventional commit messages (`feat:`, `fix:`, `docs:`,
   `refactor:`).
5. Open a pull request referencing the issue where applicable.
