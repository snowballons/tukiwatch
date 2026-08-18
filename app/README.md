# TukiWatch

<p align="center">
  <img src="https://tukiwatch.snowballons.com/logo.png" alt="TukiWatch Logo" width="200"/>
</p>

<h3 align="center">Track and Watch Your Favorite Live Streams Effortlessly</h3>

<p align="center">
  Your Ultimate Companion for Live Streaming Discovery and Viewing – No Ads, No Hassle
</p>

<p align="center">
  <img src="https://tukiwatch.snowballons.com/preview.png" alt="TukiWatch App Preview" width="600"/>
</p>

## What is TukiWatch?

TukiWatch is a mobile app designed for enthusiasts of live streaming content. It allows users to track their favorite streams from platforms like Twitch, YouTube Live, and others, receive real-time notifications when streams go live, and seamlessly watch them in-app. The app brings useful capabilities to a user-friendly mobile interface.

### Main Benefits:
- **Saves time** by automating stream tracking
- **Ad-free viewing** options where possible
- **Offline notifications** support
- **Device media player integration** for a smooth experience
- **Free and open-source** with no unnecessary data collection
- **Privacy-focused** approach

## Key Features

- 🔄 **Real-Time Stream Tracking** - Add favorite channels or streamers and get instant notifications when they go live
- 📱 **Integrated Viewing** - Watch streams directly in the app with optimal quality
- 🔔 **Custom Alerts** - Set preferences for stream quality, categories (gaming, music, sports), and notification types
- 🔍 **Discovery Tools** - Browse trending streams, search by platform or keyword, and explore recommendations
- 🚫 **Ad-Free Experience** - Bypass ads on supported platforms
- 🔗 **Cross-Platform Support** - Works with major streaming sites like Twitch, YouTube, Facebook Live, and more
- 📴 **Offline Mode** - View saved stream info and history even without internet
- 🔧 **Open-Source Customization** - Access the GitHub repo to modify or contribute to the app

## Core Functionalities

- Add streams to a favorites list
- Set custom alerts and notifications
- Browse popular and trending streams
- Direct playback with quality selection
- Support for multiple streaming platforms
- Privacy-focused design with minimal data collection

## Get TukiWatch Now!

Ready to revolutionize your live streaming experience?

[📥 Download APK (Android)](https://github.com/snowballons/tukiwatch/releases)
[🔧 View on GitHub (Source & Instructions)](https://github.com/snowballons/tukiwatch)
[🍎 App Store (Coming Soon)](#)

## Releasing a New Version

Releases are built with EAS and published as GitHub Releases:

1. **Trigger** the [`Android APK Release`](../.github/workflows/release-android.yml)
   workflow (manual dispatch with a version like `1.0.6`, or push a `v*` tag).
2. The workflow builds an Android APK via EAS, uploads it to a GitHub Release,
   and commits a bumped `version.json` + `app.json`.
3. The in-app **Check for Updates** and the [web download page](web/) read
   `version.json` to point users at the latest APK.

### Self-hosted distribution

Everything is configurable for a self-hosted install:

- `EXPO_PUBLIC_API_URL` + `EXPO_PUBLIC_BACKEND_API_KEY` — point the app at your
  own backend (see [`../docs/SELF_HOSTING.md`](../docs/SELF_HOSTING.md)). These
  are the **build-time defaults**; users of a published build can switch backend
  at runtime via **Settings → Backend → Scan QR Code** without a rebuild.
- `EXPO_PUBLIC_UPDATE_MANIFEST_URL` — host `version.json` + the APK anywhere;
  the app and web page fall back to
  `https://downloads.snowballons.com/version.json` if unset.

## Contact

📧 Email: tukiwatch@snowballons.com

---

© 2026 TukiWatch. All rights reserved.
