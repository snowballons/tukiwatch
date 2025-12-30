import axios from 'axios';

import { Favorite, LiveStream } from '../types';

const PYTHON_API_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8000';


const getRequestHeaders = async () => {
  return {};
};

export const resolveStream = async (url: string) => {
  const headers = await getRequestHeaders();
  const response = await axios.get(`${PYTHON_API_URL}/resolve?url=${url}`, { headers });
  return response.data;
};

export const streamService = {
  async checkBatchStatus(urls: string[]): Promise<LiveStream[]> {
    try {
      const headers = await getRequestHeaders();
      const response = await axios.post(`${PYTHON_API_URL}/status-batch`, { urls }, { headers });
      return response.data.results.map((result: any, index: number) => ({
        id: index,
        title: result.title || 'Unknown Stream',
        author: result.author || 'Unknown Streamer',
        thumbnail: result.thumbnail || '',
        url: result.url,
        status: result.status === 'online' ? 'online' : 'offline'
      }));
    } catch (error: any) {
      // Rate limiting disabled - simplified error handling
      console.error('Batch status check failed:', error);
      return urls.map((url, index) => ({
        id: index,
        title: 'Error',
        author: 'Unknown',
        thumbnail: '',
        url,
        status: 'offline' as const
      }));

    }
  }
};