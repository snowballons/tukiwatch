# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The current Android version is declared in
`app/gradle.properties`. Releases are published as GitHub Releases with
the APK attached.

Entries for 1.1.4 and earlier describe the retired Expo/React Native app and
FastAPI backend; their paths are historical.

## [Unreleased]

### Added

- Branded launcher icon and splash screen built from the TukiWatch mark, with
  the “Every stream. One watchlist.” slogan and a dark, cyan-accented theme.
- GitHub Actions CI workflow covering Kotlin unit/UI tests, desktop harness
  tests, debug APK assembly, and the Python engine parity suite.
- Kotlin Multiplatform port (`app/`), Android-only, backend removed:
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
  - Phase 3 full navigation & screens — Compose bottom navigation (Home / My List / Add /
    Settings) and stack overlays (Full Player, Privacy Policy, Terms of Service, Onboarding).
    - `HomeScreen`: live stream catalog with platform filters, search bar, and empty states.
    - `LibraryScreen`: complete favorites manager with search, platform filters, SAF JSON
      import/export, and delete confirmation dialogs.
    - `AddScreen`: preset platform selector (Twitch, YouTube, TikTok, Kick, Facebook,
      Instagram, custom), identifier input, on-device preview resolve, and direct save/play.
    - `FullPlayerScreen`: 16:9 ExoPlayer surface, live status badges, quality chip switcher,
      error card, and official platform app launcher via Android intents.
    - `SettingsScreen`: theme mode switching (System / Light / Dark), DataStore persistence,
      JSON backup shortcuts, build info, and legal links.
    - `OnboardingScreen`: first-run walkthrough with DataStore completion persistence.
    - `StreamCard`: responsive card with status pills, platform badges, and metadata.

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
- Restored the 14 PNG assets that the Phase 1 commit deleted unintentionally.
  They now live in `assets/` and `web/assets/` after the repository move.
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

- The repository is now Android-only: the Kotlin Multiplatform app lives in
  `app/`, the static site in `web/`, and README assets in `assets/`. The Expo
  client, FastAPI backend, Docker/Railway deployment files, and legacy CI
  workflows were removed.
- Root documentation now describes the Kotlin app and the embedded Python
  engine instead of the retired Expo/API stack.
- Player and Add Stream now each use a single dropdown selector. Removed the
  redundant quick quality/platform chip rows and the player's Python timing line.
- Removed leftover KMP template scaffolding (`Greeting`, `Platform`), the three
  placeholder tests, and the unused `app_logo.png`. The launcher name is now
  `TukiWatch`.
- Centralized the app version in `app/gradle.properties`; Settings and desktop
  packaging read it. Chaquopy now defaults to `python3.11`, and the Python parity
  test moved to `src/test/python` so it is no longer packaged into the APK.
- Phase 3 scope: Discovery v1 is dropped (owner decision). No Discovery tab or
  default discovery source, and `twitch-live` is not part of v1.
- The KMP build now ships as `com.tukiwatch.app` (versionCode 13, v1.1.5) so it
  upgrades the Expo app in place. Release builds must be signed with the old
  keystore; debug installs require uninstalling the Expo app first.

## [Unreleased]

### Added

### Changed

### Fixed

## [1.2.0] - 2026-11-15

### Added

- **Bulk/concurrent stream resolution** — the liveness ticker now resolves
  favorites in parallel batches (default 3 streams per batch) using Python's
  `ThreadPoolExecutor`. Streamlink's underlying network I/O releases the GIL
  during socket operations in C, so multiple streams are probed simultaneously
  rather than one-by-one. For a typical 9-stream library this cuts the full
  refresh cycle from ~27 s to ~9 s on a stable connection.
  - New Python entrypoints `check_status_bulk` and `resolve_details_bulk` in
    `stream_engine.py` accept a JSON array of URLs, dispatch them concurrently,
    and return an order-aligned JSON array of results. Each individual failure is
    isolated so a single bad URL never aborts the rest of the batch.
  - `StreamEngine` interface extended with `checkStatusBulk`; `ChaquopyStreamEngine`
    serialises the URL list, calls the Python bulk entrypoint via the Chaquopy bridge,
    and falls back to sequential per-URL resolution if the bridge call itself fails.
  - `LivenessSource` default `statusBulk` implementation wraps `status()` per-URL
    with per-item error isolation so custom sources automatically gain resilience.
  - `StreamlinkLivenessSource` overrides `statusBulk` to delegate to the engine's
    bulk path (`checkStatusBulk`), thread-safe on `Dispatchers.IO`.
  - `LivenessRepository.tick()` refactored from a sequential for-each to a
    batched for-each using `chunked(batchSize)`, with progressive `_streams`
    emissions after every batch so the UI reflects results incrementally.
  - New `refreshResolvesInBulkBatches` JVM test verifies chunk size, invocation
    count, and URL ordering for the bulk path.

- **Liveness cache for instant cold-start display** (`LivenessCache` / `JsonFileLivenessCache`)
  — the last known live-status result set is now persisted to
  `liveness_cache.json` in app-internal storage. On the next app open the
  cached rows are loaded synchronously before the network tick starts, so the My
  List shows stale-but-immediate data instead of a blank loading state.
  - `LiveStream` marked `@Serializable`; `LivenessCache` interface and
    `JsonFileLivenessCache` (kotlinx.serialization JSON) added to `:shared`.
  - `LivenessRepository.preload()` loads the cache on the IO dispatcher and
    emits it into `_streams` only when the list is still empty.
  - `preload()` is called at the top of `refresh()` for apps that skip the
    explicit warm-up step, and from `TukiWatchApp` on first composition.
  - `refreshSavesToCache` and `preloadLoadsFromCacheImmediately` JVM tests added.

- **Foreground media playback service with notification controls** (`PlaybackService`)
  — when *Background Playback* is toggled on in Settings the player now binds a
  foreground `Service` that holds a `PARTIAL_WAKE_LOCK` and posts a persistent
  media notification with **Play / Pause / Stop** actions.
  - Manifest permissions added: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
    `POST_NOTIFICATIONS`, `WAKE_LOCK`.
  - `PlaybackService` manages the wake lock lifecycle and notification channel
    (`tukiwatch_playback`) with a `MediaStyle` controller wired to the active
    `ExoPlayer` session.
  - `StreamViewModel` starts and stops `PlaybackService` in response to player
    state and the `backgroundPlayback` DataStore preference.
  - `PlayerScreen` starts the service on composition when the preference is
    enabled, and stops it on `DisposableEffect` cleanup.

- **Scroll-position preservation across player back-navigation** — returning
  from the full-screen player to the Home or My List screen no longer resets the
  list to the top.
  - `LazyListState` for both `HomeScreen` and `LibraryScreen` is now hoisted into
    `AppContent` / `MainActivity` and threaded down as a parameter, so it
    survives recomposition triggered by back-stack changes.

- **Streamlink bumped to 8.6.3** — the Chaquopy pip dependency is updated from
  `8.6.2` to `8.6.3`, picking up upstream plugin fixes and reliability
  improvements. The parity test suite and all documentation references updated
  to match.

### Changed

- `LivenessRepository.tick()` now emits a progressive `_streams` update after
  each resolved batch rather than only at the end of the full tick, so the UI
  reflects partial results as they arrive.
- `LivenessRepository` constructor gains two new optional parameters:
  `cache: LivenessCache?` (default `null`) and `batchSize: Int` (default `3`).
  Existing call-sites are unaffected.
- `StreamlinkLivenessSource` constructor gains an optional `maxWorkers: Int`
  parameter (default `3`) forwarded to the Python bulk entrypoint.
- Saving an empty favorites list now also writes an empty list to the cache
  (clears stale data) instead of doing nothing.
- `AGENTS.md` toolchain pin updated to Streamlink 8.6.3.

### Fixed

- `oneFailingUrlDoesNotAbortTheTick` test correctly handles per-item error
  isolation now that the default `statusBulk` delegates through the per-URL
  `status()` guard wrapper rather than a bare sequential map, ensuring a
  `SocketTimeoutException` on one URL produces `status=error` for that item
  without affecting other URLs in the same batch.



### Added

- Floating capsule bottom navigation bar — detached rounded pill with animated
  selection pill, raised Add circle, haptic ticks, and accessibility `selectable(role = Tab)`.
- Full-bleed fullscreen and modern picture-in-picture — Media3 ExoPlayer surface
  goes edge-to-edge, no header in fullscreen, PiP window matches stream aspect ratio.
- GitHub issue templates (bug report, feature request, regression report) with
  app-specific guidance; "Report a problem" in Settings now lands on the bug
  template. Security issues route to email per the README.

### Fixed

- Right-side gap appearing across all screens on cutout / gesture-nav devices.
  The custom `FloatingBottomBar` did not consume horizontal system-bar/cutout
  insets, so `Scaffold` forwarded them into `innerPadding` which stacked on top
  of each screen's own horizontal padding. Fixed by consuming the horizontal
  insets on the bar's outer box.
- Persistent right-side gap after exiting fullscreen. `exitFullscreen()` set
  `layoutInDisplayCutoutMode = DEFAULT` on the window, a window-level constraint
  that forces the system to reserve the cutout strip and clip content out of it
  — a hard constraint `consumeWindowInsets` cannot undo. Restored
  `SHORT_EDGES` on exit and at app startup so the edge-to-edge content draws
  under the cutout in portrait, matching the transparent status/nav bars.

The entries below describe the retired Expo client and FastAPI backend. Paths
such as `app/src/`, `api/`, and the EAS workflows refer to the old layout and no
longer exist in the current Kotlin-only repository.

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

[Unreleased]: https://github.com/snowballons/tukiwatch/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/snowballons/tukiwatch/compare/v1.1.8...v1.2.0
[1.1.8]: https://github.com/snowballons/tukiwatch/compare/v1.1.4...v1.1.8
[1.1.4]: https://github.com/snowballons/tukiwatch/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/snowballons/tukiwatch/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/snowballons/tukiwatch/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/snowballons/tukiwatch/compare/v1.0.6...v1.1.1
[1.0.5]: https://github.com/snowballons/tukiwatch/releases/tag/v1.0.5
[1.0.1]: https://github.com/snowballons/tukiwatch/releases/tag/v1.0.1
[1.0.0]: https://github.com/snowballons/tukiwatch/releases/tag/1.0.0
