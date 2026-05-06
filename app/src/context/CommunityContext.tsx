import React, { createContext, useContext, useState, useCallback, useEffect, useRef, useMemo } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { CommunityStream, CommunityFilters } from '../types';
import { fetchCommunityStreams } from '../services/communityService';

const CACHE_KEY = 'sw_community_cache';
const CACHE_TS_KEY = 'sw_community_cache_ts';
const HOUR_MS = 60 * 60 * 1000;

interface CommunityContextType {
  streams: CommunityStream[];
  loading: boolean;
  filters: CommunityFilters;
  setFilters: (f: CommunityFilters) => void;
  addStream: (stream: CommunityStream) => void;
  removeStream: (id: string) => void;
  isCheckingLiveness: boolean;
  lastUpdated: number | null;
}

const CommunityContext = createContext<CommunityContextType | undefined>(undefined);

export function CommunityProvider({ children }: { children: React.ReactNode }) {
  const [streams, setStreams] = useState<CommunityStream[]>([]);
  const [loading, setLoading] = useState(false);
  const [isCheckingLiveness, setIsCheckingLiveness] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);
  const [filters, setFiltersState] = useState<CommunityFilters>({});
  const streamsRef = useRef<CommunityStream[]>([]);

  useEffect(() => { streamsRef.current = streams; }, [streams]);

  const sortStreams = (list: CommunityStream[]) =>
    [...list].sort((a, b) => {
      if (a.is_online !== b.is_online) return a.is_online ? -1 : 1;
      return new Date(b.created_at).getTime() - new Date(a.created_at).getTime();
    });

  const saveCache = async (list: CommunityStream[]) => {
    await AsyncStorage.multiSet([
      [CACHE_KEY, JSON.stringify(list)],
      [CACHE_TS_KEY, Date.now().toString()],
    ]);
  };

  // Initial load
  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const [[, cached], [, ts]] = await AsyncStorage.multiGet([CACHE_KEY, CACHE_TS_KEY]);
        const age = ts ? Date.now() - parseInt(ts, 10) : Infinity;

        if (cached && age < HOUR_MS) {
          const parsed = JSON.parse(cached) as CommunityStream[];
          setStreams(sortStreams(parsed));
          setLastUpdated(parseInt(ts!, 10));
        } else {
          const data = await fetchCommunityStreams();
          const sorted = sortStreams(data);
          setStreams(sorted);
          await saveCache(sorted);
          setLastUpdated(Date.now());
        }
      } catch (e) {
        console.error('Community load failed:', e);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // Hourly refresh job - fetches latest data from DB (updated by backend)
  useEffect(() => {
    const runUpdate = async () => {
      setIsCheckingLiveness(true); // Re-use this flag for the refresh
      try {
        const data = await fetchCommunityStreams();
        const sorted = sortStreams(data);
        setStreams(sorted);
        await saveCache(sorted);
        setLastUpdated(Date.now());
      } catch (e) {
        console.error('Hourly refresh failed:', e);
      } finally {
        setIsCheckingLiveness(false);
      }
    };

    const timer = setInterval(runUpdate, HOUR_MS);
    return () => clearInterval(timer);
  }, []);

  const addStream = useCallback((stream: CommunityStream) => {
    setStreams(prev => {
      const updated = sortStreams([stream, ...prev]);
      saveCache(updated);
      return updated;
    });
  }, []);

  const removeStream = useCallback((id: string) => {
    setStreams(prev => {
      const updated = prev.filter(s => s.id !== id);
      saveCache(updated);
      return updated;
    });
  }, []);

  const setFilters = useCallback((f: CommunityFilters) => {
    setFiltersState(f);
  }, []);

  const filteredStreams = useMemo(() => {
    let result = streams;
    if (filters.platform) result = result.filter(s => s.platform === filters.platform);
    if (filters.category) result = result.filter(s => s.category === filters.category);
    if (filters.country)  result = result.filter(s => s.country  === filters.country);
    if (filters.language) result = result.filter(s => s.language === filters.language);
    if (filters.search?.trim()) {
      const q = filters.search.trim().toLowerCase();
      result = result.filter(s => s.streamer_name.toLowerCase().includes(q));
    }
    return result;
  }, [streams, filters]);

  return (
    <CommunityContext.Provider value={{
      streams: filteredStreams,
      loading,
      filters,
      setFilters,
      addStream,
      removeStream,
      isCheckingLiveness,
      lastUpdated,
    }}>
      {children}
    </CommunityContext.Provider>
  );
}

export function useCommunity() {
  const ctx = useContext(CommunityContext);
  if (!ctx) throw new Error('useCommunity must be used within CommunityProvider');
  return ctx;
}
