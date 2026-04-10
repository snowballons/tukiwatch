import { supabase } from '../../lib/supabase';
import { CommunityStream } from '../types';
import { streamService } from './engine';

export interface CommunityFilters {
  platform?: string;
  category?: string;
  country?: string;
  language?: string;
  search?: string;
}

export async function fetchCommunityStreams(filters: CommunityFilters = {}): Promise<CommunityStream[]> {
  let query = supabase
    .from('community_streams')
    .select('*')
    .order('created_at', { ascending: false })
    .limit(50);

  if (filters.platform && filters.platform !== 'all') query = query.eq('platform', filters.platform);
  if (filters.category && filters.category !== 'all') query = query.eq('category', filters.category);
  if (filters.country  && filters.country  !== 'all') query = query.eq('country',  filters.country);
  if (filters.language && filters.language !== 'all') query = query.eq('language', filters.language);
  if (filters.search?.trim()) {
    query = query.ilike('streamer_name', `%${filters.search.trim()}%`);
  }

  const { data, error } = await query;
  if (error) throw error;
  return data as CommunityStream[];
}

export async function shareStream(payload: {
  original_url: string;
  streamer_name: string;
  platform: string;
  category: string;
  country: string;
  language: string;
  username: string;
}): Promise<void> {
  const { data: { user } } = await supabase.auth.getUser();
  if (!user) throw new Error('Not authenticated');

  const { error } = await supabase.from('community_streams').insert([{
    user_id: user.id,
    ...payload,
  }]);

  if (error) {
    if (error.code === '23505') throw new Error('You already shared this stream.');
    throw error;
  }
}

export async function unshareStream(id: string): Promise<void> {
  const { error } = await supabase.from('community_streams').delete().eq('id', id);
  if (error) throw error;
}

export async function isAlreadyShared(userId: string, url: string): Promise<boolean> {
  const { data } = await supabase
    .from('community_streams')
    .select('id')
    .eq('user_id', userId)
    .eq('original_url', url)
    .maybeSingle();
  return !!data;
}

export async function checkStreamLiveness(streams: { id: string; original_url: string }[]): Promise<Record<string, boolean>> {
  if (streams.length === 0) return {};

  const statusMap: Record<string, boolean> = {};

  // Chunk into batches of 20 (backend limit)
  const CHUNK_SIZE = 20;
  for (let i = 0; i < streams.length; i += CHUNK_SIZE) {
    const chunk = streams.slice(i, i + CHUNK_SIZE);
    try {
      const results = await streamService.checkBatchStatus(chunk.map(s => s.original_url));
      results.forEach((result, index) => {
        statusMap[chunk[index].id] = result.status === 'online';
      });
    } catch (e) {
      console.error('Liveness chunk check failed:', e);
    }
  }

  return statusMap;
}
