import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
import { CommunityStream } from '../types';
import { fetchCommunityStreams, checkStreamLiveness, CommunityFilters } from '../services/communityService';

const LIVENESS_INTERVAL = 60 * 60 * 1000; // 60 minutes

interface CommunityContextType {
  streams: CommunityStream[];
  loading: boolean;
  filters: CommunityFilters;
  setFilters: (f: CommunityFilters) => void;
  refresh: (overrideFilters?: CommunityFilters) => Promise<void>;
  refreshLiveness: () => Promise<void>;
  isCheckingLiveness: boolean;
}

const CommunityContext = createContext<CommunityContextType | undefined>(undefined);

export function CommunityProvider({ children }: { children: React.ReactNode }) {
  const [streams, setStreams] = useState<CommunityStream[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFiltersState] = useState<CommunityFilters>({});
  const [isCheckingLiveness, setIsCheckingLiveness] = useState(false);
  const livenessTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refresh = useCallback(async (overrideFilters?: CommunityFilters) => {
    setLoading(true);
    try {
      const data = await fetchCommunityStreams(overrideFilters ?? filters);
      setStreams(data);
    } catch (e) {
      console.error('Community fetch failed:', e);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const refreshLiveness = useCallback(async () => {
    if (streams.length === 0) return;
    setIsCheckingLiveness(true);
    try {
      const statusMap = await checkStreamLiveness(streams.map(s => ({ id: s.id, original_url: s.original_url })));
      setStreams(prev => prev.map(s => ({
        ...s,
        is_online: statusMap[s.id] ?? s.is_online,
        last_checked: new Date().toISOString(),
      })));
    } catch (e) {
      console.error('Liveness refresh failed:', e);
    } finally {
      setIsCheckingLiveness(false);
    }
  }, [streams]);

  const setFilters = useCallback((f: CommunityFilters) => {
    setFiltersState(f);
    setLoading(true);
    fetchCommunityStreams(f)
      .then(setStreams)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    livenessTimerRef.current = setInterval(() => {
      refreshLiveness();
    }, LIVENESS_INTERVAL);

    return () => {
      if (livenessTimerRef.current) clearInterval(livenessTimerRef.current);
    };
  }, [refreshLiveness]);

  return (
    <CommunityContext.Provider value={{ streams, loading, filters, setFilters, refresh, refreshLiveness, isCheckingLiveness }}>
      {children}
    </CommunityContext.Provider>
  );
}

export function useCommunity() {
  const ctx = useContext(CommunityContext);
  if (!ctx) throw new Error('useCommunity must be used within CommunityProvider');
  return ctx;
}
