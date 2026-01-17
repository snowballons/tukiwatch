import React, { createContext, useContext, useState, useEffect } from 'react';
import { supabase } from '../../lib/supabase';
import { streamService } from '../services/engine';
import { Favorite, LiveStream } from '../types';

interface StreamContextType {
  streams: LiveStream[];
  loading: boolean;
  refreshStreams: (bypassCache?: boolean) => Promise<void>;
}

const StreamContext = createContext<StreamContextType | undefined>(undefined);

export function StreamProvider({ children }: { children: React.ReactNode }) {
  const [streams, setStreams] = useState<LiveStream[]>([]);
  const [loading, setLoading] = useState(true);

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
      
      // Add favorite metadata to stream results
      const enrichedStreams = statusResults.map((stream, index) => ({
        ...stream,
        id: favorites[index].id,
        streamer_name: favorites[index].streamer_name
      }));

      setStreams(enrichedStreams);
    } catch (error: any) {
      console.error('Failed to refresh streams:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshStreams();
  }, []);

  return (
    <StreamContext.Provider value={{ streams, loading, refreshStreams }}>
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
