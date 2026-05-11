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
  id: string;
  username: string;
  is_guest: boolean;
  created_at: string;
  updated_at: string;
}

export interface CommunityStream {
  id: string;
  user_id: string;
  username: string;
  original_url: string;
  streamer_name: string;
  platform: string;
  category: string;
  country: string;
  language: string;
  created_at: string;
  is_online: boolean;
  last_checked: string;
}

export interface CommunityFilters {
  platform?: string;
  category?: string;
  country?: string;
  language?: string;
  search?: string;
}

export const COMMUNITY_CATEGORIES = [
  'Gaming',
  'Music',
  'Sports',
  'News',
  'Talk Shows',
  'Art & Creative',
  'Education',
  'IRL',
  'Other',
] as const;

export const COMMUNITY_COUNTRIES = [
  'Global',
  'US',
  'UK',
  'CA',
  'AU',
  'DE',
  'FR',
  'JP',
  'KR',
  'BR',
  'IN',
  'MX',
  'NG',
  'ZA',
  'Other',
] as const;

export const COMMUNITY_LANGUAGES = [
  'English',
  'Spanish',
  'Portuguese',
  'French',
  'German',
  'Japanese',
  'Korean',
  'Chinese',
  'Arabic',
  'Other',
] as const;
