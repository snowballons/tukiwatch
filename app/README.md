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
  reports an update when `manifest.versionCode > currentVersionCode`.
- The manifest shape: `{ version, versionCode, apkUrl, releaseNotes, mandatory, minAndroidVersion }`.
- "Check for Updates" in Settings downloads the APK via the manifest URL.

## Releasing a new version

Trigger the **Android APK Release** workflow (manual dispatch with a version
like `1.0.6`):

1. If a GitHub release `v<version>` already ships an APK, the workflow exits
   without rebuilding (fully idempotent rerun).
2. Otherwise, if a finished EAS Android build with the same `appVersion`
   already exists (e.g. a previous run failed after the build), that APK is
   **reused** instead of building again.
3. Otherwise EAS builds a fresh APK
   (`eas build --platform android --profile production`).
4. The workflow downloads the APK, creates a GitHub Release, and commits a
   bumped `version.json` + `app.json` back to `main`.
5. The in-app **Check for Updates** and the [web download page](../app/web/)
   read `version.json` to point users at the latest APK.

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
