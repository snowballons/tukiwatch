import axios from 'axios';

import { Favorite, LiveStream } from '../types';

const PYTHON_API_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8000';

// Rate limit tracking
export interface RateLimitInfo {
  limit: number;
  remaining: number;
  reset: number;
}

let latestRateLimitInfo: RateLimitInfo | null = null;

function updateRateLimitInfo(headers: any) {
  if (!headers) return;
  const limit = headers['x-ratelimit-limit'];
  const remaining = headers['x-ratelimit-remaining'];
  const reset = headers['x-ratelimit-reset'];
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

const getRequestHeaders = async () => {
  return {};
};

export const checkHealth = async (): Promise<boolean> => {
  try {
    const response = await axios.get(`${PYTHON_API_URL}/health`, { timeout: 5000 });
    return response.data?.status === 'healthy';
  } catch {
    return false;
  }
};

export const getCacheStats = async (): Promise<any> => {
  try {
    const response = await axios.get(`${PYTHON_API_URL}/cache/stats`);
    return response.data;
  } catch {
    return null;
  }
};

export const resolveStream = async (url: string, bypassCache: boolean = false) => {
  const headers = await getRequestHeaders();
  const params = new URLSearchParams({ url });
  if (bypassCache) {
    params.append('bypass_cache', 'true');
  }
  const response = await axios.get(`${PYTHON_API_URL}/resolve?${params}`, { headers });
  updateRateLimitInfo(response.headers);
  return response.data;
};

export const streamService = {
  async checkBatchStatus(urls: string[], bypassCache: boolean = false): Promise<LiveStream[]> {
    try {
      const headers = await getRequestHeaders();
      const params = bypassCache ? '?bypass_cache=true' : '';
      const response = await axios.post(`${PYTHON_API_URL}/status-batch${params}`, { urls }, { headers });
      updateRateLimitInfo(response.headers);
      return response.data.results.map((result: any, index: number) => ({
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
    } catch (error: any) {
      // Update rate limit info from error response headers too
      updateRateLimitInfo(error.response?.headers);

      // Enhanced error handling based on HTTP status codes
      let errorMessage = 'Status check failed';
      if (error.response?.status === 400) {
        errorMessage = error.response.data.detail || 'Invalid batch request';
        console.error('Invalid batch request:', errorMessage);
      } else if (error.response?.status === 429) {
        const retryAfter = error.response.data.retry_after || 60;
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
  }
};