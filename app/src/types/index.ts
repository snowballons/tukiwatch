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
  // Error details from backend (free-form dict server-side)
  error?: string;
  error_details?: {
    type?: string;
    error?: string;
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
  // Error details from backend (free-form dict server-side)
  error_details?: {
    type?: string;
    error?: string;
    message?: string;
    reason?: string;
    alternative?: string;
  } | null;
}

// Discovery-specific types for browse functionality
export interface DiscoveryStream extends LiveStream {
  // Twitch-specific fields from Helix API
  game_id?: string;
  game_name?: string;
  language?: string;
  started_at?: string;
  tag_ids?: string[];
  is_mature?: boolean;
  viewer_count?: number;
  // Discovery metadata
  platform: 'twitch' | 'youtube' | 'kick' | string; // Extensible for future platforms
  // For pagination
  cursor?: string;
  // TwitchTracker-specific fields
  _twitchTrackerRank?: number;
  _shareOfGame?: string;
  _shareOfTwitch?: string;
}

export interface DiscoveryResponse {
  streams: DiscoveryStream[];
  pagination?: {
    cursor: string;
  };
  total_count?: number;
}

export interface DiscoveryFilters {
  platform?: string;
  game_id?: string;
  game_name?: string;
  language?: string;
  sort_by?: 'viewer_count' | 'started_at';
  limit?: number;
  cursor?: string;
}

export interface GameCategory {
  id: string;
  name: string;
  box_art_url: string;
}

export interface GameCategoriesResponse {
  categories: GameCategory[];
  pagination?: {
    cursor: string;
  };
}
export interface UserProfile {
  username: string;
}
