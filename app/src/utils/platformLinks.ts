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
  ustreamtv: 'Ustream',
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
