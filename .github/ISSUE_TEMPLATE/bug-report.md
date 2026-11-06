---
name: Bug report
about: Something is broken or behaving unexpectedly. Start here for most problems.
title: "[Bug] <short summary>"
labels: bug
assignees: ''
---

## Describe the bug

A clear, one-sentence description of what went wrong.

**What did you expect to happen?**

**What actually happened?**

## To reproduce

Steps a user can follow to see the problem:

1.  Open TukiWatch.
2.  ...
3.  Observe the issue.

**Is the problem reproducible every time, or only sometimes?** If intermittent, how often does it happen?

## Environment

Fill in what applies — leave the rest blank rather than guessing.

- **TukiWatch version:** (e.g. 1.1.5 — check Settings → About, or the APK version)
- **Android version:** (e.g. Android 14)
- **Device / manufacturer:** (e.g. Pixel 8, Samsung Galaxy S23)
- **Streamlink version:** (bundled with the APK; check Settings → About if shown)
- **Theme:** Light / Dark / System
- **Did you have background playback enabled?** Yes / No
- **Was the stream in picture-in-picture, fullscreen, or normal view when it happened?**

## Stream details (if the bug is about a stream)

- **Platform:** (Twitch / YouTube / TikTok / Kick / Facebook / Instagram / Bigo Live / Dailymotion / Vimeo / Steam Community / Bilibili / Huya / Picarto / Trovo / VK / DLive / GoodGame / AbemaTV / Aloula / Custom URL)
- **Streamer / URL you were trying to watch:**
- **Was the stream online when you tried to play it?**
- **Quality you had selected:**

## Logs or screenshots

If you can, attach a screenshot. For playback or resolution issues, a screen recording is much more useful than a still image.

**How to share logs:** TukiWatch has no backend, so nothing is uploaded automatically. If the issue is reproducible, build a debug APK and run the app with `logcat` enabled, then share the relevant lines. The Python engine (`stream_engine.py`) also writes to its own log file — see `app/README.md` for how to extract it.

## Additional context

Anything else that might help: settings you had changed, a favourite that misbehaves, whether the problem started after a specific update, etc.