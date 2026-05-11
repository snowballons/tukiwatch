import { supabase } from '../../lib/supabase';
import type { CommunityStream } from '../types';
import { streamService } from './engine';

export async function fetchCommunityStreams(): Promise<CommunityStream[]> {
  const { data, error } = await supabase
    .from('community_streams')
    .select('*')
    .order('is_online', { ascending: false })
    .order('created_at', { ascending: false })
    .limit(50);

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
}): Promise<CommunityStream> {
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) throw new Error('Not authenticated');

  const results = await streamService.checkBatchStatus([payload.original_url]);
  const is_online = results[0]?.status === 'online';

  const { data, error } = await supabase
    .from('community_streams')
    .insert([{ user_id: user.id, ...payload, is_online, last_checked: new Date().toISOString() }])
    .select()
    .single();

  if (error) {
    if (error.code === '23505') throw new Error('You already shared this stream.');
    throw error;
  }
  return data as CommunityStream;
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
