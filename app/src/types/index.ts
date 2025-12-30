export interface Favorite {
  id: number;
  streamer_name: string;
  original_url: string;
  user_id: string;
}

export interface LiveStream {
  id: number;
  title: string;
  author: string;
  thumbnail: string;
  url: string;
  status: 'online' | 'offline' | 'loading';
  streamer_name?: string;
}

export interface StreamResolution {
  status: 'online' | 'offline';
  title?: string;
  author?: string;
  thumbnail?: string;
  best_quality?: string;
  all_qualities?: Record<string, string>;
  error?: string;
  original_url?: string;
}