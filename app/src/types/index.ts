export interface Favorite {
  id: number;
  streamer_name: string;
  original_url: string;
}

export interface LiveStream {
  id: number;
  title: string;
  author: string;
  thumbnail: string;
  url: string;
  status: 'online' | 'offline' | 'loading' | 'error';
  streamer_name?: string;
  // Enhanced metadata from backend
  category?: string;
  stream_id?: string;
  platform?: string;
  // Cache indicator
  _cached?: boolean;
  _fetchedAt?: number;
  // Error details from backend
  error?: string;
  error_details?: {
    type?: string;
    message?: string;
    reason?: string;
    alternative?: string;
  } | null;
}

export interface StreamResolution {
  status: 'online' | 'offline' | 'error';
  title?: string;
  author?: string;
  thumbnail?: string;
  best_quality?: string;
  all_qualities?: Record<string, string>;
  error?: string;
  original_url?: string;
  // Enhanced metadata from backend
  category?: string;
  stream_id?: string;
  platform?: string;
  stream_types?: string[];
  // Cache indicator
  _cached?: boolean;
  // Error details from backend
  error_details?: {
    type?: string;
    message?: string;
    reason?: string;
    alternative?: string;
  } | null;
}

export interface UserProfile {
  username: string;
}
