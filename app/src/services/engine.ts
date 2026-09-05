import axios, { isAxiosError } from 'axios';

import { getBackendConfig } from '../lib/backendConfig';
import type { LiveStream } from '../types';

// Rate limit tracking
export interface RateLimitInfo {
  limit: number;
  remaining: number;
  reset: number;
}

let latestRateLimitInfo: RateLimitInfo | null = null;

function updateRateLimitInfo(headers: unknown) {
  if (!headers || typeof headers !== 'object') return;
  const record = headers as Record<string, string | undefined>;
  const limit = record['x-ratelimit-limit'];
  const remaining = record['x-ratelimit-remaining'];
  const reset = record['x-ratelimit-reset'];
  if (limit !== undefined && remaining !== undefined && reset !== undefined) {
    latestRateLimitInfo = {
      limit: parseInt(limit, 10),
      remaining: parseInt(remaining, 10),
      reset: parseInt(reset, 10),
    };
  }
}

export function getRateLimitInfo(): RateLimitInfo | null {
  return latestRateLimitInfo;
}

const API_HEADERS = {
  'Content-Type': 'application/json',
};

const getBaseUrl = async (): Promise<string> => {
  const config = await getBackendConfig();
  return config.apiUrl.replace(/\/+$/, '');
};

export const checkHealth = async (): Promise<boolean> => {
  try {
    const baseUrl = await getBaseUrl();
    const response = await axios.get(`${baseUrl}/health`, { timeout: 5000 });
    return response.data?.status === 'healthy';
  } catch {
    return false;
  }
};

export const getCacheStats = async (): Promise<Record<string, unknown> | null> => {
  try {
    const baseUrl = await getBaseUrl();
    const response = await axios.get(`${baseUrl}/cache/stats`);
    return response.data;
  } catch {
    return null;
  }
};

export const resolveStream = async (url: string, bypassCache: boolean = false) => {
  const baseUrl = await getBaseUrl();
  const params = new URLSearchParams({ url });
  if (bypassCache) {
    params.append('bypass_cache', 'true');
  }
  const response = await axios.get(`${baseUrl}/api/resolve?${params}`, {
    headers: API_HEADERS,
  });
  updateRateLimitInfo(response.headers);
  return response.data;
};

interface StatusBatchResultItem {
  title?: string;
  author?: string;
  thumbnail?: string;
  url: string;
  status: LiveStream['status'];
  category?: string;
  stream_id?: string;
  platform?: string;
  _cached?: boolean;
  error?: string;
  error_details?: LiveStream['error_details'];
}

export const streamService = {
  async checkBatchStatus(urls: string[], bypassCache: boolean = false): Promise<LiveStream[]> {
    try {
      const baseUrl = await getBaseUrl();
      const params = bypassCache ? '?bypass_cache=true' : '';
      const response = await axios.post(
        `${baseUrl}/api/status-batch${params}`,
        { urls },
        { headers: API_HEADERS }
      );
      updateRateLimitInfo(response.headers);
      const items = (response.data?.results ?? []) as StatusBatchResultItem[];
      return items.map((result, index) => ({
        id: index,
        title: result.title || 'Unknown Stream',
        author: result.author || 'Unknown Streamer',
        thumbnail: result.thumbnail || '',
        url: result.url,
        status: result.status,
        // Enhanced metadata
        category: result.category || '',
        stream_id: result.stream_id || '',
        platform: result.platform || 'unknown',
        // Cache indicator
        _cached: result._cached || false,
        // Error details
        error: result.error || '',
        error_details: result.error_details || null,
      }));
    } catch (error: unknown) {
      const axiosError = isAxiosError(error) ? error : undefined;
      // Update rate limit info from error response headers too
      updateRateLimitInfo(axiosError?.response?.headers);

      // Enhanced error handling based on HTTP status codes
      let errorMessage = 'Status check failed';
      const status = axiosError?.response?.status;
      const data = axiosError?.response?.data as
        | { detail?: string; retry_after?: number }
        | undefined;
      if (status === 400) {
        errorMessage = data?.detail || 'Invalid batch request';
        console.error('Invalid batch request:', errorMessage);
      } else if (status === 429) {
        const retryAfter = data?.retry_after || 60;
        errorMessage = `Rate limited. Retry after ${retryAfter} seconds`;
        console.warn(errorMessage);
      } else {
        console.error('Batch status check failed:', error);
      }

      return urls.map((url, index) => ({
        id: index,
        title: 'Error',
        author: 'Unknown',
        thumbnail: '',
        url,
        status: 'error' as const,
        category: '',
        stream_id: '',
        platform: 'unknown',
        _cached: false,
        error: errorMessage,
        error_details: null,
      }));
    }
  },
};
