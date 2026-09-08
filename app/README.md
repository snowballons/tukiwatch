# TukiWatch App

Expo (React Native) mobile client for [TukiWatch](../README.md) — a
local-first app for tracking and watching live streams, powered by a
[self-hostable FastAPI backend](../api/README.md).

- **Runtime:** [Expo SDK 56](https://docs.expo.dev/versions/v56.0.0/) ·
  React Native 0.85 · React 19 · TypeScript
- **Key libs:** [expo-video](https://docs.expo.dev/versions/latest/sdk/video/)
  (playback), [expo-sqlite](https://docs.expo.dev/versions/latest/sdk/sqlite/)
  (favorites), [expo-camera](https://docs.expo.dev/versions/latest/sdk/camera/)
  (QR connect), [React Navigation](https://reactnavigation.org/)
- **Package manager:** [Bun](https://bun.sh/)

## Setup

```bash
cd app
cp .env.example .env   # optional — see Environment variables
bun install
bun run start          # Expo dev server
```

Other scripts (`package.json`): `bun run android` / `bun run ios` /
`bun run web` for platform builds, `bun run lint` / `bun run format` /
`bun run check` for local quality, and `bun run type-check` (`tsc --noEmit`).

## Environment variables

| Variable | Purpose | Default |
| :--- | :--- | :--- |
| `EXPO_PUBLIC_API_URL` | **Build-time default** backend URL the app connects to | `http://localhost:8000/` |
| `EXPO_PUBLIC_UPDATE_MANIFEST_URL` | URL of the update manifest (`version.json`) used by "Check for Updates" | `https://downloads.snowballons.com/version.json` |

The build-time default is only a fallback. Published builds can switch backends
at runtime (no rebuild) via **Settings → Backend → Scan QR Code** or a
`tukiwatch://connect` deep link — see
[`../docs/SELF_HOSTING.md`](../docs/SELF_HOSTING.md).

## Navigation & screens

```
App.tsx
├─ CustomSplashScreen        (until AsyncStorage is read)
├─ OnboardingScreen          (once; gated by onboarding_complete)
└─ NavigationContainer       (deep links: tukiwatch://, https://…)
   └─ Root Stack
      ├─ MainTabs (bottom tabs)
      │  ├─ Home      → HomeScreen       # "Live Now" — online favorites
      │  ├─ My List   → LibraryScreen    # all favorites, add/remove
      │  ├─ Add       → AddScreen        # 20+ platform picker + verify
      │  └─ Settings  → SettingsScreen   # backend, updates, export/import
      ├─ Player        (full-screen modal)
      └─ Connect       (transient deep-link handler)
```

## Data layer

Favorites are stored **on-device** in SQLite (`expo-sqlite`, WAL mode, DB
`tukiwatch.db`):

```sql
CREATE TABLE favorites (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  streamer_name TEXT NOT NULL,
  original_url TEXT NOT NULL UNIQUE,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

`lib/db.ts` provides `addFavorite` (dedupes on `original_url`),
`removeFavorite`, `getFavorites`, and `exportFavorites` / `importFavorites`
(JSON payload via the share sheet / document picker).

`AsyncStorage` keys:

| Key | Contents |
| :--- | :--- |
| `username` | Display name shown in Settings (`useProfile`); local only |
| `backend_config` | Runtime backend override (`{ apiUrl, updateManifestUrl? }`) |
| `onboarding_complete` | Gate for the onboarding carousel |

There is **no account system and no cloud sync** — everything is local.

## Backend connectivity

- `src/services/engine.ts` — axios client. `checkHealth` (`GET /health`),
  `resolveStream` (`GET /api/resolve?url=…&bypass_cache=`), and
  `streamService.checkBatchStatus` (`POST /api/status-batch`). Tracks
  `x-ratelimit-*` headers and maps 400/429 to user-facing messages.
- `src/context/StreamContext.tsx` — the app-wide provider. Reads favorites,
  batch-checks their status, and auto-refreshes every 5 minutes (matches the
  backend status-cache TTL).
- `src/lib/backendConfig.ts` — AsyncStorage-backed config, plus the connect-URI
  format:

```
tukiwatch://connect?url=<backend-url>&updates=<manifest-url>
```

  `verifyBackend()` pings `/health` before a config is accepted; a plain URL in
  the QR is also accepted (treated as the backend base URL).

## Update flow

- `src/services/updateService.ts` fetches the manifest (`version.json`) and
  reports an update when the manifest code for the device's asset is newer.
  The manifest shape: `{ version, versionCode, versionCodeArm64, apkUrl, apkUrlArm64, releaseNotes, mandatory, minAndroidVersion }`.
- "Check for Updates" in Settings downloads the arm64 APK on arm64 devices
  (`expo-device`), otherwise the universal APK, via `selectApkUrl()`.

## Android builds: arm64 primary + universal fallback

Two APK variants are built from the same source (see `eas.json`):

- `tukiwatch-<version>-arm64.apk` — arm64-v8a only, the primary download (~50MB).
- `tukiwatch-<version>-universal.apk` — all ABIs, fallback for 32-bit devices.

Per-variant ABI filtering is driven by the `ANDROID_ABI` env var
(`production-arm64` sets it; `production-universal` leaves it unset),
consumed by `app.config.js` through the local `plugins/withAbiFilter.js`
config plugin (verified via `expo prebuild`: `abiFilters 'arm64-v8a'` lands
in `defaultConfig`, absent for universal).

## Releasing a new version

Trigger the **Android APK Release** workflow (manual dispatch with a version
like `1.0.6`):

1. If GitHub release `v<version>` already ships **both** APKs, the workflow
   exits without rebuilding (fully idempotent rerun).
2. Otherwise, finished EAS builds are reused **per profile**
   (`production-arm64`, `production-universal`) when a finished build with the
   same `appVersion` already exists.
3. Otherwise EAS builds each missing variant
   (`eas build --platform android --profile production-<variant>`).
4. The workflow downloads both APKs, creates a GitHub Release with both
   assets, and commits a bumped `version.json` + `app.json` back to `main`.
5. The in-app **Check for Updates** and the [web download page](../app/web/)
   read `version.json` to point users at the right APK per architecture.

Pass the `force` input (`true`) to rebuild a version that is already
released. Note: the `production` profile uses `autoIncrement`, so the actual
`versionCode` of a build is set by EAS — the workflow records the real built
code in `version.json`, not the one in `app.json`.

## Quality gates

```bash
bun install --frozen-lockfile
bunx @biomejs/biome ci .
bun run type-check
```

## See also

- [TukiWatch](../README.md) — repository root
- [TukiWatch API](../api/README.md) — the backend this app talks to
- [Self-hosting guide](../docs/SELF_HOSTING.md) — run your own backend and wire
  the app to it
