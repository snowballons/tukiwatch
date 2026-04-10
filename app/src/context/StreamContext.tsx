import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { supabase } from '../../lib/supabase';
import { streamService, checkHealth } from '../services/engine';
import { Favorite, LiveStream } from '../types';

const AUTO_REFRESH_INTERVAL = 300_000; // 5 minutes — matches backend status TTL

interface StreamContextType {
  streams: LiveStream[];
  loading: boolean;
  isBackendReachable: boolean;
  refreshStreams: (bypassCache?: boolean) => Promise<void>;
}

const StreamContext = createContext<StreamContextType | undefined>(undefined);

export function StreamProvider({ children }: { children: React.ReactNode }) {
  const [streams, setStreams] = useState<LiveStream[]>([]);
  const [loading, setLoading] = useState(true);
  const [isBackendReachable, setIsBackendReachable] = useState(true);
  const refreshTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refreshStreams = async (bypassCache: boolean = false) => {
    try {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) return;

      const { data: favorites } = await supabase
        .from('favorites')
        .select('*')
        .eq('user_id', user.id);

      if (!favorites || favorites.length === 0) {
        setStreams([]);
        return;
      }

      const urls = favorites.map((fav: Favorite) => fav.original_url);
      const statusResults = await streamService.checkBatchStatus(urls, bypassCache);
      
      const fetchedAt = Date.now();
      // Add favorite metadata and fetch timestamp to stream results
      const enrichedStreams = statusResults.map((stream, index) => ({
        ...stream,
        id: favorites[index].id,
        streamer_name: favorites[index].streamer_name,
        _fetchedAt: fetchedAt,
      }));

      setStreams(enrichedStreams);
      setIsBackendReachable(true);
    } catch (error: any) {
      console.error('Failed to refresh streams:', error);
      setIsBackendReachable(false);
    } finally {
      setLoading(false);
    }
  };

  const checkConnectivity = useCallback(async () => {
    const reachable = await checkHealth();
    setIsBackendReachable(reachable);
  }, []);

  useEffect(() => {
    refreshStreams();
    checkConnectivity();

    // Auto-refresh periodically to keep data from going stale
    refreshTimerRef.current = setInterval(() => {
      refreshStreams(false); // use cache — just re-validates
    }, AUTO_REFRESH_INTERVAL);

    return () => {
      if (refreshTimerRef.current) {
        clearInterval(refreshTimerRef.current);
      }
    };
  }, []);

  return (
    <StreamContext.Provider value={{ streams, loading, isBackendReachable, refreshStreams }}>
      {children}
    </StreamContext.Provider>
  );
}

export function useStreams() {
  const context = useContext(StreamContext);
  if (!context) {
    throw new Error('useStreams must be used within StreamProvider');
  }
  return context;
}
