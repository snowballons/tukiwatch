# Open Stream in Official App — Implementation Plan

## Overview

Add an "Open in [Platform]" button to the PlayerScreen that attempts to open the stream in the platform's native mobile app (Twitch, YouTube, etc.). If the native app is not installed, it silently falls back to opening the stream URL in the device browser.

---

## Background

StreamWatch aggregates live streams from 20 different platforms. When a user is watching a stream in the in-app player, they may want to engage with it officially (chat, donate, subscribe, interact with the streamer). This feature provides a one-tap bridge to the platform's native app.

### Data Available in PlayerScreen

The `route.params.streamData` object (type `StreamResolution` from `src/types/index.ts`) already contains:
- `platform` (string) — e.g., `"twitch"`, `"youtube"`, `"kick"`
- `original_url` (string) — the web URL of the stream (e.g., `https://www.twitch.tv/ninja`)
- `title`, `author`, `thumbnail`, `best_quality`, `all_qualities`

These fields provide everything needed to construct platform-specific deep links.

---

## React Native Linking API

The project uses `Linking` from `react-native` (already imported in `App.tsx` and `SettingsScreen.tsx`). The key methods are:

### `Linking.canOpenURL(url: string): Promise<boolean>`
Checks whether an installed app can handle the given URL. Returns `true` if a native app is available.

**Platform requirements:**
- **iOS 9+**: Must declare `LSApplicationQueriesSchemes` in `Info.plist` for each scheme to check. Without this, `canOpenURL` always returns `false`. In Expo managed workflow, this is set via `app.json` → `expo.ios.infoPlist.LSApplicationQueriesSchemes`.
- **Android 11+ (API 30)**: Must declare `<queries>` intents in `AndroidManifest.xml`. Without this, `canOpenURL` always returns `false`. In Expo managed workflow, this requires a custom config plugin.

### `Linking.openURL(url: string): Promise<any>`
Opens the given URL with the appropriate installed app. If no app handles the scheme, the promise rejects.

### Flow
1. Construct the native app URL (e.g., `twitch://stream/ninja`)
2. Call `Linking.canOpenURL(nativeUrl)` to check if the app is installed
3. If `true` → `Linking.openURL(nativeUrl)` (opens in native app)
4. If `false` → `Linking.openURL(originalUrl)` (opens in browser as fallback)

**Important**: `https://` and `http://` schemes do NOT need to be added to `LSApplicationQueriesSchemes` or `<queries>` — they always work.

---

## Platform Deep Link Schemes

### Tier 1: Platforms with Official/Documented URL Schemes

| Platform | Native URL Scheme | Stream URL Format | Android Package | Source |
|----------|------------------|-------------------|-----------------|--------|
| **Twitch** | `twitch://` | `twitch://stream/{channel}` | `tv.twitch.android.app` | [Official Docs](https://dev.twitch.tv/docs/mobile-deeplinks/) |
| **YouTube** | `vnd.youtube://` | `vnd.youtube://` + video/channel ID from URL | `com.google.android.youtube` | Universal links via `youtube.com` (auto-opens app) |
| **Instagram** | `instagram://` | `instagram://user?username={username}` | `com.instagram.android` | [Known scheme](https://dev.to/ahandsel/instagram-url-schemes-1k6n) |

### Tier 2: Platforms with Known/Community-Documented URL Schemes

| Platform | Native URL Scheme | Stream URL Format | Android Package | Source |
|----------|------------------|-------------------|-----------------|--------|
| **Facebook** | `fb://` | `fb://profile/{page_id}` or fallback to `https://` | `com.facebook.katana` | [StackOverflow](https://stackoverflow.com/questions/5707722), [Gist](https://gist.github.com/bartleby/6588aa4782dfb3f1d50c23ce9a4554e3) |
| **TikTok** | `tiktok://` | `tiktok://` (opens app, URL handled via universal links) | `com.zhiliaoapp.musically` | [MySocial](https://mysocial.io/blog/how-to-deep-link-to-tiktok/) |
| **VK** | `vk://` | `vk://` (opens app, URL path handled) | `com.vkontakte.android` | [StackOverflow](https://stackoverflow.com/questions/25205491) |

**Note on Facebook**: The `fb://` scheme has limited deep link support. The `fb://profile?id=` format requires a numeric page ID, which we don't have from the stream data. For Facebook, the most reliable approach is to skip the native scheme check and directly open the `original_url` via `https://` — Facebook's universal links will handle opening the app if it's installed.

**Note on TikTok**: The `tiktok://` scheme opens the app but doesn't reliably deep link to specific content. TikTok's universal links (`https://www.tiktok.com/...`) may or may not open the app depending on the platform. The safest approach is to try `tiktok://` first, then fall back to `https://`.

### Tier 3: Platforms without Documented URL Schemes (Browser Fallback Only)

| Platform | Strategy |
|----------|----------|
| **Kick** | Open `https://kick.com/{channel}` in browser. Kick's mobile app (package `com.kick.mobile`) supports universal links from `kick.com` URLs on Android — the browser may redirect to the app automatically. |
| **Bilibili** | Open `https://live.bilibili.com/{id}` in browser. Bilibili's app (package `tv.danmaku.bili`) may intercept via universal links. |
| **Bigo** | Open `https://www.bigo.tv/{id}` in browser. |
| **Dailymotion** | Open `https://www.dailymotion.com/{id}` in browser. |
| **Vimeo** | Open `https://vimeo.com/{id}` in browser. |
| **Steam** | Open `https://steamcommunity.com/{id}` in browser. |
| **Huya** | Open `https://www.huya.com/{id}` in browser. |
| **Picarto** | Open `https://picarto.tv/{id}` in browser. |
| **Trovo** | Open `https://trovo.live/{id}` in browser. |
| **Ustream** | Open `https://www.ustream.tv/{id}` in browser. |
| **DLive** | Open `https://dlive.tv/{id}` in browser. |
| **GoodGame** | Open `https://goodgame.ru/{id}` in browser. |
| **AbemaTV** | Open `https://abema.tv/{id}` in browser. |
| **Aloula** | Open `https://www.aloula.sa/{id}` in browser. |

For Tier 3 platforms, there is no native scheme to check. The button simply opens the `original_url` in the device browser. This still achieves the goal: the user can engage with the stream on the official platform.

---

## Implementation Details

### File 1: `src/utils/platformLinks.ts` (NEW FILE)

```typescript
import { Linking, Alert, Platform } from 'react-native';

// ── Platform Deep Link Configuration ──

interface PlatformScheme {
  scheme: string;       // URL scheme prefix (e.g., "twitch://")
  iosScheme: string;    // iOS-specific scheme if different
  androidScheme: string; // Android-specific scheme if different
}

/**
 * Maps platform keys to their native app URL schemes.
 * Only includes platforms with well-documented/deep link support.
 * Platforms not listed here will fall back to browser-only.
 */
const PLATFORM_SCHEMES: Record<string, PlatformScheme> = {
  twitch: {
    scheme: 'twitch://',
    iosScheme: 'twitch://',
    androidScheme: 'twitch://',
  },
  youtube: {
    scheme: 'vnd.youtube://',
    iosScheme: 'youtube://',
    androidScheme: 'vnd.youtube://',
  },
  instagram: {
    scheme: 'instagram://',
    iosScheme: 'instagram://',
    androidScheme: 'instagram://',
  },
  facebook: {
    scheme: 'fb://',
    iosScheme: 'fb://',
    androidScheme: 'fb://',
  },
  tiktok: {
    scheme: 'tiktok://',
    iosScheme: 'tiktok://',
    androidScheme: 'tiktok://',
  },
  vk: {
    scheme: 'vk://',
    iosScheme: 'vk://',
    androidScheme: 'vk://',
  },
};

// ── Platform Display Names ──

const PLATFORM_NAMES: Record<string, string> = {
  twitch: 'Twitch',
  youtube: 'YouTube',
  kick: 'Kick',
  facebook: 'Facebook',
  instagram: 'Instagram',
  tiktok: 'TikTok',
  bigo: 'Bigo',
  dailymotion: 'Dailymotion',
  vimeo: 'Vimeo',
  steam: 'Steam',
  bilibili: 'Bilibili',
  huya: 'Huya',
  picarto: 'Picarto',
  trovo: 'Trovo',
  ustream: 'Ustream',
  vk: 'VK',
  dlive: 'DLive',
  goodgame: 'GoodGame',
  abematv: 'AbemaTV',
  aloula: 'Aloula',
};

// ── Core Functions ──

/**
 * Returns the native app URL scheme for a given platform.
 * Returns null if the platform has no known native scheme.
 */
function getPlatformScheme(platform: string): string | null {
  const config = PLATFORM_SCHEMES[platform];
  if (!config) return null;
  return Platform.OS === 'ios' ? config.iosScheme : config.androidScheme;
}

/**
 * Constructs the native deep link URL for opening a stream in the platform's app.
 * 
 * For Twitch: extracts channel name from URL → twitch://stream/{channel}
 * For YouTube: converts to vnd.youtube://{video_id} or youtube://watch?v={id}
 * For Instagram: extracts username → instagram://user?username={username}
 * For others: just opens the scheme base URL (app will handle the path)
 */
function constructNativeUrl(platform: string, originalUrl: string): string | null {
  try {
    const url = new URL(originalUrl);
    
    switch (platform) {
      case 'twitch': {
        // URL format: https://www.twitch.tv/{channel}
        const channel = url.pathname.replace('/', '').split('/')[0];
        if (channel) {
          return `twitch://stream/${channel}`;
        }
        return 'twitch://open';
      }
      
      case 'youtube': {
        // URL format: https://www.youtube.com/watch?v={videoId}
        // Or: https://www.youtube.com/channel/{channelId}
        // Or: https://www.youtube.com/@{handle}
        // Or: https://www.youtube.com/live/{videoId}
        const videoId = url.searchParams.get('v');
        if (videoId) {
          return Platform.OS === 'ios'
            ? `youtube://watch?v=${videoId}`
            : `vnd.youtube://watch?v=${videoId}`;
        }
        // For channels/lives without a video ID, universal links handle it
        return null; // Will use original_url which YouTube handles well
      }
      
      case 'instagram': {
        // URL format: https://www.instagram.com/{username}/ or /live/{username}
        const pathParts = url.pathname.split('/').filter(Boolean);
        // Could be: /username, /live/username, /reel/...
        const username = pathParts.find(p => !['live', 'reel', 'stories', 'p'].includes(p));
        if (username) {
          return `instagram://user?username=${username}`;
        }
        return 'instagram://';
      }
      
      case 'facebook': {
        // Facebook's fb:// scheme requires numeric page IDs which we don't have
        // Return null to use browser fallback (universal links handle the rest)
        return null;
      }
      
      case 'tiktok': {
        // TikTok scheme opens the app, but specific content linking is unreliable
        return 'tiktok://';
      }
      
      case 'vk': {
        // VK scheme opens the app
        return 'vk://';
      }
      
      default:
        return null;
    }
  } catch {
    return null;
  }
}

/**
 * Returns the display name for a platform.
 */
export function getPlatformName(platform: string): string {
  return PLATFORM_NAMES[platform] || platform;
}

/**
 * Returns whether a platform has native app deep link support.
 */
export function hasNativeScheme(platform: string): boolean {
  return platform in PLATFORM_SCHEMES;
}

/**
 * Attempts to open a stream in the platform's official app.
 * Falls back to opening in the device browser if the native app is not installed.
 * 
 * @param platform - The platform key (e.g., "twitch", "youtube")
 * @param originalUrl - The stream's web URL (e.g., "https://www.twitch.tv/ninja")
 */
export async function openInOfficialApp(
  platform: string,
  originalUrl: string
): Promise<void> {
  const nativeScheme = getPlatformScheme(platform);
  
  // If no native scheme exists for this platform, open in browser
  if (!nativeScheme) {
    try {
      await Linking.openURL(originalUrl);
    } catch {
      Alert.alert('Error', 'Could not open the link.');
    }
    return;
  }
  
  // Construct the native deep link URL
  const nativeUrl = constructNativeUrl(platform, originalUrl);
  
  // If we couldn't construct a native URL, try opening the scheme base
  const urlToTry = nativeUrl || nativeScheme;
  
  try {
    const canOpen = await Linking.canOpenURL(urlToTry);
    if (canOpen) {
      await Linking.openURL(urlToTry);
    } else {
      // Native app not installed, fall back to browser
      await Linking.openURL(originalUrl);
    }
  } catch {
    // If canOpenURL itself fails (e.g., missing queries config), 
    // try opening directly and fall back to browser on error
    try {
      await Linking.openURL(originalUrl);
    } catch {
      Alert.alert('Error', 'Could not open the link.');
    }
  }
}
```

**Key design decisions:**
- `constructNativeUrl` handles platform-specific URL parsing (extracting channel names, video IDs, etc.)
- For YouTube: uses `vnd.youtube://` on Android and `youtube://` on iOS
- For Facebook: returns `null` (forces browser fallback) since `fb://` requires numeric IDs we don't have
- For TikTok: opens `tiktok://` to launch the app (specific content deep linking is unreliable)
- Error handling: if `canOpenURL` fails (e.g., missing platform config), falls back to browser instead of crashing
- The function is async and never throws — errors are caught and shown as alerts

### File 2: `src/screens/PlayerScreen.tsx` (MODIFY)

**Changes:**
1. Import `Linking` from `react-native`, `ExternalLink` from `lucide-react-native`, and the utility functions
2. Add an "Open in [Platform]" button below the quality selector (or below the divider if no quality selector)
3. Button shows platform name with brand color from `PlatformColors`

**UI Pattern:** Reuses the existing `qualitySelector` card style (bordered row with label + chevron). The icon is tinted with the platform's brand color from `PlatformColors`.

**Placement:** In the `<ScrollView contentContainerStyle={styles.details}>` section, after the quality selector block (line 78) and before the quality picker modal.

**New code to insert after line 78 (after the quality selector closing `)}`):**

```tsx
{/* Open in Official App */}
{sources.original_url && (
  <TouchableOpacity
    style={styles.openAppButton}
    onPress={() => openInOfficialApp(sources.platform || 'unknown', sources.original_url)}
  >
    <ExternalLink
      color={PlatformColors[sources.platform as keyof typeof PlatformColors] || PlatformColors.default}
      size={20}
    />
    <Text style={styles.openAppLabel}>
      Open in {getPlatformName(sources.platform || 'unknown')}
    </Text>
    <ChevronRight color={Palette.textMuted} size={20} />
  </TouchableOpacity>
)}
```

**New styles to add:**

```typescript
openAppButton: {
  flexDirection: 'row',
  alignItems: 'center',
  backgroundColor: Palette.card,
  padding: Spacing.md,
  borderRadius: 12,
  borderWidth: 1,
  borderColor: Palette.border,
  marginTop: 12,
  gap: 12,
},
openAppLabel: {
  flex: 1,
  color: Palette.text,
  fontSize: 16,
  fontWeight: '600',
},
```

### File 3: `app.json` (MODIFY)

**Required changes:**

#### iOS: Add `LSApplicationQueriesSchemes`

Add to `expo.ios`:

```json
"ios": {
  "infoPlist": {
    "LSApplicationQueriesSchemes": [
      "twitch",
      "youtube",
      "vnd.youtube",
      "instagram",
      "fb",
      "tiktok",
      "vk"
    ]
  }
}
```

Without this, `Linking.canOpenURL()` on iOS will always return `false` for these schemes.

#### Android: Add `<queries>` intents

Add to `expo.android`:

```json
"android": {
  "queries": [
    {
      "intent": {
        "action": "VIEW",
        "category": "BROWSABLE",
        "data": { "scheme": "twitch" }
      }
    },
    {
      "intent": {
        "action": "VIEW",
        "category": "BROWSABLE",
        "data": { "scheme": "vnd.youtube" }
      }
    },
    {
      "intent": {
        "action": "VIEW",
        "category": "BROWSABLE",
        "data": { "scheme": "instagram" }
      }
    },
    {
      "intent": {
        "action": "VIEW",
        "category": "BROWSABLE",
        "data": { "scheme": "fb" }
      }
    },
    {
      "intent": {
        "action": "VIEW",
        "category": "BROWSABLE",
        "data": { "scheme": "tiktok" }
      }
    },
    {
      "intent": {
        "action": "VIEW",
        "category": "BROWSABLE",
        "data": { "scheme": "vk" }
      }
    }
  ]
}
```

Without this, `Linking.canOpenURL()` on Android 11+ will always return `false` for these schemes.

---

## Files Summary

| File | Action | Purpose |
|------|--------|---------|
| `src/utils/platformLinks.ts` | **Create** | Platform URL scheme map, native URL construction, `openInOfficialApp()` function |
| `src/screens/PlayerScreen.tsx` | **Modify** | Add "Open in [Platform]" button with platform branding |
| `app.json` | **Modify** | Add iOS `LSApplicationQueriesSchemes` and Android `<queries>` for `canOpenURL` support |

---

## Complete app.json After Changes

```json
{
  "expo": {
    "name": "StreamWatch",
    "slug": "streamwatch",
    "version": "1.0.0",
    "orientation": "portrait",
    "icon": "./assets/logo.png",
    "userInterfaceStyle": "dark",
    "newArchEnabled": true,
    "scheme": "streamwatch",
    "splash": {
      "image": "./assets/logo.png",
      "resizeMode": "contain",
      "backgroundColor": "#0A0A0A"
    },
    "ios": {
      "infoPlist": {
        "LSApplicationQueriesSchemes": [
          "twitch",
          "youtube",
          "vnd.youtube",
          "instagram",
          "fb",
          "tiktok",
          "vk"
        ]
      }
    },
    "android": {
      "adaptiveIcon": {
        "foregroundImage": "./assets/logo.png",
        "backgroundColor": "#0A0A0A"
      },
      "edgeToEdgeEnabled": true,
      "predictiveBackGestureEnabled": false,
      "package": "com.streamwatch.app",
      "intentFilters": [
        {
          "action": "VIEW",
          "autoVerify": true,
          "data": [
            {
              "scheme": "https",
              "host": "lnuxpkwnbesqrqsxyiek.supabase.co",
              "pathPrefix": "/auth"
            }
          ],
          "category": ["BROWSABLE", "DEFAULT"]
        }
      ],
      "queries": [
        {
          "intent": {
            "action": "VIEW",
            "category": "BROWSABLE",
            "data": { "scheme": "twitch" }
          }
        },
        {
          "intent": {
            "action": "VIEW",
            "category": "BROWSABLE",
            "data": { "scheme": "vnd.youtube" }
          }
        },
        {
          "intent": {
            "action": "VIEW",
            "category": "BROWSABLE",
            "data": { "scheme": "instagram" }
          }
        },
        {
          "intent": {
            "action": "VIEW",
            "category": "BROWSABLE",
            "data": { "scheme": "fb" }
          }
        },
        {
          "intent": {
            "action": "VIEW",
            "category": "BROWSABLE",
            "data": { "scheme": "tiktok" }
          }
        },
        {
          "intent": {
            "action": "VIEW",
            "category": "BROWSABLE",
            "data": { "scheme": "vk" }
          }
        }
      ]
    },
    "plugins": [
      "expo-video"
    ],
    "extra": {
      "eas": {
        "projectId": "2879c1fe-24f0-415e-b606-b5ab7888d48b"
      }
    },
    "owner": "lunderwood1967s-organization"
  }
}
```

---

## PlayerScreen.tsx Changes Detail

### Imports to add:
```typescript
import { Linking } from 'react-native';  // Already has View, Text, etc.
import { X, ChevronDown, ExternalLink, ChevronRight } from 'lucide-react-native';
import { PlatformColors } from '../theme/Theme';  // Already imports Palette, Spacing
import { openInOfficialApp, getPlatformName } from '../utils/platformLinks';
```

### JSX to insert (after quality selector block, before quality picker modal):

After line 78 in PlayerScreen.tsx (after the closing `)}` of the quality selector conditional), insert:

```tsx
{sources.original_url && (
  <TouchableOpacity
    style={styles.openAppButton}
    onPress={() => openInOfficialApp(sources.platform || 'unknown', sources.original_url)}
  >
    <ExternalLink
      color={PlatformColors[sources.platform as keyof typeof PlatformColors] || PlatformColors.default}
      size={20}
    />
    <Text style={styles.openAppLabel}>
      Open in {getPlatformName(sources.platform || 'unknown')}
    </Text>
    <ChevronRight color={Palette.textMuted} size={20} />
  </TouchableOpacity>
)}
```

### Styles to add (inside the StyleSheet.create block):

```typescript
openAppButton: {
  flexDirection: 'row',
  alignItems: 'center',
  backgroundColor: Palette.card,
  padding: Spacing.md,
  borderRadius: 12,
  borderWidth: 1,
  borderColor: Palette.border,
  marginTop: 12,
  gap: 12,
},
openAppLabel: {
  flex: 1,
  color: Palette.text,
  fontSize: 16,
  fontWeight: '600',
},
```

---

## User Experience Flow

```
User taps "Open in Twitch" button
    │
    ├─ Can open twitch://stream/ninja?
    │   ├─ YES → Opens Twitch app directly to the stream
    │   └─ NO  → Opens https://www.twitch.tv/ninja in device browser
    │
    └─ (Silent, no error, one tap)
```

### Button appearance:
- Card-style row matching the existing quality selector
- Platform brand-colored icon on the left (e.g., purple for Twitch, red for YouTube)
- Label: "Open in Twitch" / "Open in YouTube" / etc.
- Chevron on the right indicating tap action
- Only visible when `streamData.original_url` exists

---

## Edge Cases & Error Handling

| Scenario | Behavior |
|----------|----------|
| Native app installed | Opens native app directly |
| Native app not installed | Opens stream URL in browser |
| `canOpenURL` fails (missing config) | Falls back to browser |
| `openURL` fails | Shows error alert |
| No `original_url` in streamData | Button is not rendered |
| Unknown platform | Uses gray color, shows platform key as name, opens in browser |
| `original_url` is malformed | `constructNativeUrl` catches the error, returns null, falls back to browser |

---

## Testing Checklist

- [ ] Twitch stream: "Open in Twitch" button visible, opens app if installed
- [ ] YouTube stream: "Open in YouTube" button visible, opens app if installed
- [ ] Instagram live: "Open in Instagram" button visible, opens app if installed
- [ ] Kick stream: "Open in Kick" button visible, opens in browser (no native scheme)
- [ ] Unknown platform: Button shows with default gray color
- [ ] No `original_url`: Button is hidden
- [ ] Native app not installed: Falls back to browser gracefully
- [ ] No TypeScript errors: `npx tsc --noEmit`
- [ ] Button styling matches existing quality selector pattern
- [ ] Platform brand colors render correctly

---

## References

- [Twitch Mobile Deep Links](https://dev.twitch.tv/docs/mobile-deeplinks/) — Official Twitch deep link documentation
- [React Native Linking API](https://reactnative.dev/docs/linking) — `canOpenURL`, `openURL`, platform requirements
- [Expo Linking into Other Apps](https://docs.expo.dev/linking/into-other-apps) — Expo-specific configuration for iOS and Android
- [Instagram URL Schemes](https://dev.to/ahandsel/instagram-url-schemes-1k6n) — Known Instagram deep link schemes
- [iOS URL Schemes Gist](https://gist.github.com/bartleby/6588aa4782dfb3f1d50c23ce9a4554e3) — Community-maintained list of iOS app schemes
- [Mobile App Deep Link Table](https://gist.github.com/yidas/4e4b134305770d87be9ac1eca2e6fb6e) — Cross-platform deep link reference
