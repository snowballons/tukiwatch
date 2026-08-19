# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The app version lives in `app/package.json`, `app/app.json`, and `app/version.json`
(the update manifest consumed by the in-app updater). Releases are published as
GitHub Releases with the APK attached.

## [Unreleased]

### Added

- Runtime backend switching via QR code / deep link (`tukiwatch://connect`) —
  point a published build at any self-hosted backend without a rebuild.

### Changed

- App migrated to Expo SDK 56 / React Native 0.85 / React 19 (Expo plugin
  config).
- API authentication (API key middleware) removed — the backend is
  intentionally unauthenticated and rate-limited; self-hosters protect it at
  the network level.
- Connect URIs accept a plain backend URL; legacy `key=` parameters are
  tolerated and ignored.
- Docs restructured into per-area READMEs (`app/`, `api/`) and a self-hosting
  guide; the root README now follows the repo's documented template.

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

[Unreleased]: https://github.com/snowballons/tukiwatch/compare/v1.0.1...HEAD
[1.0.5]: https://github.com/snowballons/tukiwatch/releases/tag/v1.0.5
[1.0.1]: https://github.com/snowballons/tukiwatch/releases/tag/v1.0.1
[1.0.0]: https://github.com/snowballons/tukiwatch/releases/tag/1.0.0
