# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The app version lives in `app/package.json`, `app/app.json`, and `app/version.json`
(the update manifest consumed by the in-app updater). Releases are published as
GitHub Releases with the APK attached.

## [Unreleased]

### Added

- Kotlin Multiplatform port (`tukiwatch-kotlin/`), Android-only, backend removed:
  - Phase 1 engine parity — the on-device Streamlink engine (Chaquopy) now returns
    `tukiwatch-core` metadata (title/author/category/stream_id/platform), the full
    error taxonomy (`no_plugin / browser_required / plugin_error / unexpected`)
    carrying the structured `{type, error, message, reason, alternative}` payload
    the Expo app rendered, and `twitch-supported-codecs` widened to `h264,h265,av1`.
  - Phase 2 data layer — SQLDelight `favorites` table with a schema identical to the
    Expo SQLite one and a 1:1 DAO port of `lib/db.ts`; JSON export/import v1 as the
    migration vehicle; DataStore prefs (theme mode, onboarding, update-skip version,
    BYO Helix keys); `networkErrors.ts` classification ported onto the
    Chaquopy/Streamlink cause chain; `LivenessRepository` running staggered
    foreground status with the 5-min cadence and `_fetchedAt` enrichment.
  - Desktop harness — `desktopApp` now opens a real Compose window over the shared
    engine/repositories instead of the Compose Multiplatform template. It resolves a
    URL, renders metadata, structured errors, qualities and engine timings, and drives
    My List (file-backed SQLite) with on-demand liveness. Covered by 5 state tests and
    3 Compose UI tests (the UI tests run with a display or skip when headless).
  - Shared UI module — `:ui` owns the Compose surface (theme/palette, status pill,
    metadata card, error card, My List row, quality chips). Android's player and
    My List consume it, and the desktop harness renders the same components. `:shared`
    is now headless: the template `App.kt` and its Compose dependencies were removed.
  - Shared state holders — `StreamController` (resolve, 45-min result cache, h264-first
    quality ordering, background pause/resume, live-edge recovery, codec fallback) and
    `LibraryController` (favorites membership, JSON v1 export/import, liveness state) now
    live in `:ui`. Android's ViewModels only own ExoPlayer, its error-code mapping and SAF
    `Uri` adapters; covered by 11 controller tests on the JVM.

### Fixed

- Live-edge recovery replayed the cached tokenized URL instead of re-extracting:
  `resolveAndPlay` now takes `forceRefresh` and the recovery path sets it, so an
  expired URL is not served from the 45-minute cache.
- Kotlin `EngineResult`/`StreamStatus` declared camelCase properties without
  `@SerialName`, so the Python bridge's snake_case keys (`original_url`,
  `stream_id`, `viewer_count`, `stream_types`, `best_quality`, `error_details`)
  decoded as null. The player metadata card lost stream types/viewer counts and
  the result object lost the resolved source URL and stream id.
- `stream_engine.py` now maps a surfaced Streamlink `NoStreamsError` to
  `offline` (as `tukiwatch-core` does) instead of classifying it as a plugin
  error; `check_status` honours the caller's `options_json` instead of silently
  resetting the session.
- `unexpected` failures now carry the same structured
  `{type, error, message, reason, alternative}` payload as the other error
  classes, so the player error card is populated.
- Desktop `SubprocessStreamEngine` invocations were parsed as
  `stream_engine.py <url> [wanted]`, so `resolve_details <url> {} all` used
  `"{}"` as the wanted quality. The script now dispatches on entrypoint while
  keeping the legacy URL form.
- Desktop `SubprocessStreamEngine` hardcoded one machine's `python3.11` and an
  absolute script path, so it failed wherever Streamlink lived in a venv. It now
  discovers a Streamlink-capable interpreter (repo `.venv` / `tukiwatch-core/.venv`,
  then PATH) and the engine script by walking up from the working directory, with
  `PYKOT_PYTHON` / `PYKOT_SCRIPT` still winning. Subprocesses now have a timeout,
  drain stdout on a side thread, and extract the JSON object past Streamlink logs.
- Restored the 14 PNG assets (`app/assets/`, `app/web/assets/`) that the Phase 1
  commit deleted unintentionally.
- The full `:shared:jvmTest` suite deadlocked: `LivenessRepositoryTest` ran its
  own `runBlocking` on the same single-thread dispatcher it dispatched DB work
  to, then blocked that thread on a `CountDownLatch`. The fixture now seeds
  outside the DB dispatcher, uses `CompletableDeferred`, and a hanging test
  fails the build after five minutes.
- The offline banner was unreachable for real engine failures.
  `ChaquopyStreamEngine.checkStatus()` returns `plugin_error` results instead of
  throwing, so `isNetworkError()` never saw them. Python now flags transport
  failures with `error_details.error_class = "network"` (DNS, timeout,
  connection refused/reset, urllib3 retry text), `LivenessRepository` sets
  `tickDegraded` from that typed result, and the classifier also matches
  Python/urllib3 messages.
- Liveness was documented as foreground-only but used a composition-scoped
  `LaunchedEffect`; it now runs inside `repeatOnLifecycle(STARTED)`.
- Offline My List rows displayed the internal `"No streams available"` sentinel
  as a subtitle. `check_status` now returns the same `title`/`author`/
  `thumbnail` fallbacks as the resolve path with `error = null`.
- Removed `discovery.md`, which `3c08ce1` reintroduced identically to the file
  intentionally deleted in `2c67f22`.

### Changed

- Phase 3 scope: Discovery v1 is dropped (owner decision). No Discovery tab or
  default discovery source, and `twitch-live` is not part of v1.
- The KMP build now ships as `com.tukiwatch.app` (versionCode 13, v1.1.5) so it
  upgrades the Expo app in place. Release builds must be signed with the old
  keystore; debug installs require uninstalling the Expo app first.

## [1.1.4] - 2026-09-08

### Added

- Global connectivity via @react-native-community/netinfo, shared
  isNetworkError classifier, OfflineBanner/OfflineEmpty with retry
- Dual Android artifacts: withAbiFilter config plugin + app.config.js
  ANDROID_ABI overlay, production-arm64/universal EAS profiles,
  release workflow builds/publishes both APKs, arch-aware updater
  (expo-device) and website fallback link
- Theme tokens split into DarkColors/LightColors (ThemeColors type);
  accent deepened for light-mode contrast, functional danger/warning/
  onPrimary tokens shared across modes
- ThemeProvider with system/light/dark mode, AsyncStorage persistence,
  live OS-scheme switching; all 16 Palette consumers migrated to
  useTheme + memoized makeStyles; shared settings styles now a hook                                                
 - Added selectApkSha256 function mirroring URL logic.                                                                                 
 - Created apkVerifier.ts implementing download, SHA‑256 verification, and safe install, with a cast for FileSystem.cacheDirectory.    

### Changed
- Settings System tab gains an Appearance selector; status bar follows
  mode; app.json userInterfaceStyle automatic
- UpdateManifest now includes required apkSha256 and optional apkSha256Arm64 fields. 
   - Updated SettingsSupporterSystemTabs:                                                                                                
   - Imported new services (checkForUpdate, selectApkUrl, selectApkSha256, verifyAndOpenApk).                                          
   - Added Spacing and ThemeColors imports.                                                                                            
   - Integrated verification flow: fetch manifest, resolve SHA‑256, alert on missing/hash mismatch, and invoke verifier. 
   
### Fixed
- Discovery: 15s timeout, typed errors, retry states, footer fix,
  auto-refresh pauses offline; banners on Home/Library/Add/Player


## [1.1.3] — 2026-09-06

### Added

- Supporter tier integration backed by Polar: license key validation, short-lived
  Bearer session management in Redis, and automated webhook lifecycle sync.
- Rate-limiting differentiation: free tier is IP-based while supporters receive
  higher rate limits identified by hashed session tokens.
- Runtime backend switching via QR code / deep link (`tukiwatch://connect`) —
  point a published build at any self-hosted backend without a rebuild.

### Changed

- Decoupled hardcoded backend, update manifest, and discovery URLs from source
  code for open-source safety; URLs are now injected via build-time environment
  variables (`EXPO_PUBLIC_*`).
- Added defensive offline and unconfigured-server guards across stream engine
  and discovery services.
- App migrated to Expo SDK 56 / React Native 0.85 / React 19 (Expo plugin
  config).
- API authentication (API key middleware) removed — the backend is
  intentionally unauthenticated and rate-limited; self-hosters protect it at
  the network level.
- Connect URIs accept a plain backend URL; legacy `key=` parameters are
  tolerated and ignored.
- Docs restructured into per-area READMEs (`app/`, `api/`) and a self-hosting
  guide; the root README now follows the repo's documented template.

## [1.1.2] — 2026-09-05

### Fixed

- Discovery screen failed with "failed to fetch streams: 404" — the
  discovery API fallback URL pointed at the TwitchTracker website instead of
  the `live-steams-api` wrapper. Default is now
  `https://live-steams-api.vercel.app` (build-time
  `EXPO_PUBLIC_TWITCH_TRACKER_URL` still overrides it).

## [1.1.1] — 2026-09-05

### Added

- Discovery screen — browse live Twitch streams (via TwitchTracker) with
  language filters, pull-to-refresh, and one-tap add to the library.
- Settings split into Profile / Connection / System tabs, with in-app update
  checking and runtime backend switching.
- Hosted backend (`https://streaming.snowballons.com`) is now the app's
  default server; Redis-backed shared rate limiting on the hosted API.

### Changed

- Biome CI and strict TypeScript clean across the app: typed navigation
  params, no explicit `any`, dead code removed.
- Lemon Squeezy remnants removed from the backend; supporter-tier rate-limit
  scaffolding kept for the upcoming Polar integration.
- README screenshots presented as labeled thumbnails.

### Fixed

- SVG transform crash in DiscoveryScreen; duplicate `useEffect` blocks in
  HomeScreen; preview-button navigation carrying proper stream data.

## [1.0.5] — 2026-08-18

### Added

- First automated Android APK release pipeline (EAS Build + GitHub Release +
  `version.json` bump).

### Changed

- Onboarding flow and custom splash screen polished for the release.
- Backend error handling improved for browser-dependent platforms (structured
  `error_details`).

## [1.0.1] — 2026-05-14

### Changed

- Backend hardening: expanded test coverage and robustness fixes.

## [1.0.0] — 2026-02-11

### Added

- Initial public release of the TukiWatch mobile app and FastAPI backend.
  Track favorite streams, check live status, and watch in-app.

[Unreleased]: https://github.com/snowballons/tukiwatch/compare/v1.1.3...HEAD
[1.1.3]: https://github.com/snowballons/tukiwatch/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/snowballons/tukiwatch/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/snowballons/tukiwatch/compare/v1.0.6...v1.1.1
[1.0.5]: https://github.com/snowballons/tukiwatch/releases/tag/v1.0.5
[1.0.1]: https://github.com/snowballons/tukiwatch/releases/tag/v1.0.1
[1.0.0]: https://github.com/snowballons/tukiwatch/releases/tag/1.0.0
