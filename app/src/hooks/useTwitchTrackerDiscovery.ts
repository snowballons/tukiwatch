import NetInfo from '@react-native-community/netinfo';
import { useCallback, useEffect, useRef, useState } from 'react';
import { isNetworkError, OFFLINE_MESSAGE } from '../lib/networkErrors';
import type { DiscoveryStream } from '../types';

const REFRESH_INTERVAL = 5 * 60 * 1000; // 5 minutes
const REQUEST_TIMEOUT_MS = 15_000;

// Language code mapping: app's ISO codes → TwitchTracker's full names
const LANGUAGE_MAP: Record<string, string> = {
  en: 'english',
  es: 'spanish',
  pt: 'portuguese',
  fr: 'french',
  de: 'german',
  ru: 'russian',
  ja: 'japanese',
  zh: 'chinese',
  it: 'italian',
  pl: 'polish',
  ar: 'arabic',
  uk: 'ukrainian',
  th: 'thai',
  nl: 'dutch',
  tr: 'turkish',
  cs: 'czech',
  hu: 'hungarian',
  fi: 'finnish',
};

export interface TwitchTrackerDiscoveryState {
  streams: DiscoveryStream[];
  loading: boolean;
  refreshing: boolean;
  error: string | null;
  /** offline = no internet, config = tracker URL missing, server = backend error. */
  errorKind: 'offline' | 'config' | 'server' | null;
  hasMore: boolean;
  page: number;
  refresh: (bypassCache?: boolean) => Promise<void>;
  loadMore: () => Promise<void>;
}

export interface TwitchTrackerDiscoveryFilters {
  language?: string;
  limit?: number;
}

interface TwitchTrackerTrack {
  rank: number;
  name: string;
  url: string;
  game: string;
  viewers: number;
  shareOfGame: string;
  shareOfTwitch: string;
}

function mapTwitchTrackerToDiscoveryStream(
  track: TwitchTrackerTrack,
  language?: string
): DiscoveryStream {
  return {
    id: track.rank,
    title: `${track.name} is live`,
    author: track.name,
    thumbnail: `https://static-cdn.jtvnw.net/jtv_user_pictures/${track.name}-profile_image-300x300.png`,
    url: `https://twitch.tv${track.url}`,
    status: 'online',
    platform: 'twitch',
    category: track.game,
    game_name: track.game,
    viewer_count: track.viewers,
    language: language,
    _twitchTrackerRank: track.rank,
    _shareOfGame: track.shareOfGame,
    _shareOfTwitch: track.shareOfTwitch,
  };
}

export function useTwitchTrackerDiscovery(
  initialFilters: TwitchTrackerDiscoveryFilters = {}
): TwitchTrackerDiscoveryState {
  const [streams, setStreams] = useState<DiscoveryStream[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [errorKind, setErrorKind] = useState<'offline' | 'config' | 'server' | null>(null);
  const filtersRef = useRef(initialFilters);
  const isMountedRef = useRef(true);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    filtersRef.current = initialFilters;
  }, [initialFilters]);

  const fetchStreams = useCallback(
    async (isLoadMore = false, bypassCache = false) => {
      if (!isMountedRef.current) return;
      if (!isLoadMore) setLoading(true);
      else setRefreshing(true);
      setError(null);
      setErrorKind(null);

      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

      try {
        const trackerUrl = process.env.EXPO_PUBLIC_TWITCH_TRACKER_URL;
        if (!trackerUrl) {
          setStreams([]);
          setHasMore(false);
          setError('Discovery is not configured on this build.');
          setErrorKind('config');
          return;
        }
        const baseUrl = trackerUrl.replace(/\/+$/, '');

        const params = new URLSearchParams();
        params.set('page', String(isLoadMore ? page : 1));
        params.set('limit', String(filtersRef.current.limit || 20));

        const lang = filtersRef.current.language;
        if (lang && lang !== 'all') {
          const ttLang = LANGUAGE_MAP[lang] || lang;
          params.set('lang', ttLang);
        }

        // Add cache-busting param if requested
        if (bypassCache) {
          params.set('_t', Date.now().toString());
        }

        const response = await fetch(`${baseUrl}/api/live?${params.toString()}`, {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
          },
          signal: controller.signal,
        });

        if (!response.ok) {
          throw new Error(`TwitchTracker responded with status ${response.status}`);
        }

        const data = await response.json();
        const newTracks = data.data || [];
        const pagination = data.pagination;

        if (!isMountedRef.current) return;

        const mappedStreams: DiscoveryStream[] = newTracks.map((track: TwitchTrackerTrack) =>
          mapTwitchTrackerToDiscoveryStream(track, lang)
        );

        if (isLoadMore) {
          setStreams((prev) => [...prev, ...mappedStreams]);
        } else {
          setStreams(mappedStreams);
        }

        // TwitchTracker returns totalPages, convert to hasMore
        if (pagination) {
          setHasMore(page < pagination.totalPages);
        }
      } catch (err) {
        if (!isMountedRef.current) return;
        if (isNetworkError(err)) {
          setError(OFFLINE_MESSAGE);
          setErrorKind('offline');
        } else {
          const message = err instanceof Error ? err.message : 'Unknown error';
          setError(message);
          setErrorKind('server');
        }
      } finally {
        clearTimeout(timeoutId);
        if (isMountedRef.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [page]
  );

  const refresh = useCallback(
    async (bypassCache = false) => {
      setPage(1);
      setHasMore(true);
      await fetchStreams(false, bypassCache);
    },
    [fetchStreams]
  );

  const loadMore = useCallback(async () => {
    if (!hasMore || loading || refreshing) return;
    setPage((p) => p + 1);
    // Page change will trigger fetchStreams via useEffect
  }, [hasMore, loading, refreshing]);

  // Fetch when page changes
  useEffect(() => {
    fetchStreams(page > 1);
  }, [page, fetchStreams]);

  // Initial load
  useEffect(() => {
    fetchStreams();
  }, [fetchStreams]);

  // Auto-refresh timer (paused while the device reports no connection)
  useEffect(() => {
    const intervalId = setInterval(() => {
      if (!loading && !refreshing) {
        NetInfo.fetch()
          .then((netState) => {
            if (netState.isConnected === false || netState.isInternetReachable === false) {
              return;
            }
            refresh();
          })
          .catch(() => {
            refresh();
          });
      }
    }, REFRESH_INTERVAL);
    timerRef.current = intervalId;
    return () => clearInterval(intervalId);
  }, [loading, refreshing, refresh]);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      if (timerRef.current !== null) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, []);

  return {
    streams,
    loading,
    refreshing,
    error,
    errorKind,
    hasMore,
    page,
    refresh,
    loadMore,
  };
}
