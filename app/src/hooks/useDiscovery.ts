import { useCallback, useEffect, useRef, useState } from 'react';
import { getBackendConfig } from '../lib/backendConfig';
import type { DiscoveryStream } from '../types';

const REFRESH_INTERVAL = 5 * 60 * 1000; // 5 minutes

export interface DiscoveryState {
  streams: DiscoveryStream[];
  loading: boolean;
  refreshing: boolean;
  error: string | null;
  hasMore: boolean;
  cursor: string | null;
  refresh: (bypassCache?: boolean) => Promise<void>;
  loadMore: () => Promise<void>;
}

export function useDiscovery(
  initialFilters: { game_id?: string; language?: string; limit?: number; platform?: string } = {}
): DiscoveryState {
  const [streams, setStreams] = useState<DiscoveryStream[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(true);
  const [error, setError] = useState<string | null>(null);
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

      try {
        const config = await getBackendConfig();
        const baseUrl = config.apiUrl.replace(/\/+$/, '');

        const params = new URLSearchParams();
        if (filtersRef.current.game_id) params.append('game_id', filtersRef.current.game_id);
        if (filtersRef.current.language) params.append('language', filtersRef.current.language);
        if (isLoadMore && cursor) params.append('cursor', cursor);
        if (!isLoadMore) params.append('limit', String(filtersRef.current.limit || 20));
        if (bypassCache) params.append('bypass_cache', 'true');

        const response = await fetch(
          `${baseUrl}/api/discover/twitch/streams?${params.toString()}`,
          {
            method: 'GET',
            headers: {
              'Content-Type': 'application/json',
            },
          }
        );

        if (!response.ok) {
          throw new Error(`Failed to fetch streams: ${response.status}`);
        }

        const data = await response.json();
        const newStreams: DiscoveryStream[] = data.data || [];
        const nextCursor = data.pagination?.cursor || null;

        if (!isMountedRef.current) return;

        if (isLoadMore) {
          setStreams((prev) => [...prev, ...newStreams]);
        } else {
          setStreams(newStreams);
        }
        setCursor(nextCursor);
        setHasMore(!!nextCursor);
      } catch (err) {
        if (!isMountedRef.current) return;
        const message = err instanceof Error ? err.message : 'Unknown error';
        setError(message);
      } finally {
        if (isMountedRef.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [cursor]
  );

  const refresh = useCallback(
    async (bypassCache = false) => {
      setCursor(null);
      setHasMore(true);
      await fetchStreams(false, bypassCache);
    },
    [fetchStreams]
  );

  const loadMore = useCallback(async () => {
    if (!hasMore || loading || refreshing) return;
    await fetchStreams(true);
  }, [fetchStreams, hasMore, loading, refreshing]);

  // Initial load
  useEffect(() => {
    fetchStreams();
  }, [fetchStreams]);

  // Auto-refresh timer
  useEffect(() => {
    const intervalId = setInterval(() => {
      if (!loading && !refreshing) {
        refresh();
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
    hasMore,
    cursor,
    refresh,
    loadMore,
  };
}
