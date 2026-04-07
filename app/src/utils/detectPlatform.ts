const PLATFORM_HOSTS: [RegExp, string][] = [
  [/twitch\.tv/, 'twitch'],
  [/youtube\.com|youtu\.be/, 'youtube'],
  [/kick\.com/, 'kick'],
  [/facebook\.com/, 'facebook'],
  [/instagram\.com/, 'instagram'],
  [/tiktok\.com/, 'tiktok'],
  [/bigo\.tv/, 'bigo'],
  [/dailymotion\.com/, 'dailymotion'],
  [/vimeo\.com/, 'vimeo'],
  [/bilibili\.com/, 'bilibili'],
  [/huya\.com/, 'huya'],
  [/picarto\.tv/, 'picarto'],
  [/trovo\.live/, 'trovo'],
  [/dlive\.tv/, 'dlive'],
  [/goodgame\.ru/, 'goodgame'],
  [/abema\.tv/, 'abematv'],
  [/aloula\.sa/, 'aloula'],
  [/vk\.com/, 'vk'],
];

export function detectPlatformFromUrl(url: string): string {
  try {
    const { hostname } = new URL(url);
    for (const [pattern, platform] of PLATFORM_HOSTS) {
      if (pattern.test(hostname)) return platform;
    }
  } catch {}
  return 'other';
}
