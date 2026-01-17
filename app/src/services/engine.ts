import axios from 'axios';

import { Favorite, LiveStream } from '../types';

const PYTHON_API_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8000';


const getRequestHeaders = async () => {
  return {};
};

export const resolveStream = async (url: string, bypassCache: boolean = false) => {
  const headers = await getRequestHeaders();
  const params = new URLSearchParams({ url });
  if (bypassCache) {
    params.append('bypass_cache', 'true');
  }
  const response = await axios.get(`${PYTHON_API_URL}/resolve?${params}`, { headers });
  return response.data;
};

export const streamService = {
  async checkBatchStatus(urls: string[], bypassCache: boolean = false): Promise<LiveStream[]> {
    try {
      const headers = await getRequestHeaders();
      const params = bypassCache ? '?bypass_cache=true' : '';
      const response = await axios.post(`${PYTHON_API_URL}/status-batch${params}`, { urls }, { headers });
      return response.data.results.map((result: any, index: number) => ({
        id: index,
        title: result.title || 'Unknown Stream',
        author: result.author || 'Unknown Streamer',
        thumbnail: result.thumbnail || '',
        url: result.url,
        status: result.status === 'online' ? 'online' : 'offline',
        // Enhanced metadata
        category: result.category || '',
        stream_id: result.stream_id || '',
        platform: result.platform || 'unknown',
        // Cache indicator
        _cached: result._cached || false
      }));
    } catch (error: any) {
      // Enhanced error handling based on HTTP status codes
      if (error.response?.status === 400) {
        console.error('Invalid batch request:', error.response.data.detail);
      } else if (error.response?.status === 429) {
        // Rate limiting error
        console.error('Rate limited:', error.response.data);
        const retryAfter = error.response.data.retry_after || 60;
        // For batch requests, we'll return empty results but log the rate limit
        console.warn(`Rate limited. Retry after ${retryAfter} seconds`);
      } else if (error.response?.status === 503) {
        console.error('Service unavailable:', error.response.data.detail);
      } else {
        console.error('Batch status check failed:', error);
      }
      
      return urls.map((url, index) => ({
        id: index,
        title: 'Error',
        author: 'Unknown',
        thumbnail: '',
        url,
        status: 'offline' as const,
        category: '',
        stream_id: '',
        platform: 'unknown',
        _cached: false
      }));
    }
  }
};