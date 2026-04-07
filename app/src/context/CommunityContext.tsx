import React, { createContext, useContext, useState, useCallback } from 'react';
import { CommunityStream } from '../types';
import { fetchCommunityStreams, CommunityFilters } from '../services/communityService';

interface CommunityContextType {
  streams: CommunityStream[];
  loading: boolean;
  filters: CommunityFilters;
  setFilters: (f: CommunityFilters) => void;
  refresh: (overrideFilters?: CommunityFilters) => Promise<void>;
}

const CommunityContext = createContext<CommunityContextType | undefined>(undefined);

export function CommunityProvider({ children }: { children: React.ReactNode }) {
  const [streams, setStreams] = useState<CommunityStream[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFiltersState] = useState<CommunityFilters>({});

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

  const setFilters = useCallback((f: CommunityFilters) => {
    setFiltersState(f);
    setLoading(true);
    fetchCommunityStreams(f)
      .then(setStreams)
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  return (
    <CommunityContext.Provider value={{ streams, loading, filters, setFilters, refresh }}>
      {children}
    </CommunityContext.Provider>
  );
}

export function useCommunity() {
  const ctx = useContext(CommunityContext);
  if (!ctx) throw new Error('useCommunity must be used within CommunityProvider');
  return ctx;
}
