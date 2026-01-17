import { useState } from 'react';
import { resolveStream } from '../services/engine';
import { StreamResolution } from '../types';

export function useStreamResolver() {
    const [resolving, setResolving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resolve = async (url: string, bypassCache: boolean = false): Promise<StreamResolution | null> => {
        setResolving(true);
        setError(null);
        try {
            const data = await resolveStream(url, bypassCache);
            return data as StreamResolution;
        } catch (e: any) {
            // Enhanced error handling based on HTTP status codes
            let msg = 'Failed to resolve stream';
            
            if (e.response?.status === 400) {
                msg = 'Invalid URL format. Please check the URL and try again.';
            } else if (e.response?.status === 404) {
                msg = 'No streams found for this URL.';
            } else if (e.response?.status === 422) {
                msg = e.response.data.detail || 'Unsupported streaming service or plugin error.';
            } else if (e.response?.status === 429) {
                // Rate limiting error
                const retryAfter = e.response.data.retry_after || 60;
                msg = `Too many requests. Please wait ${retryAfter} seconds and try again.`;
            } else if (e.response?.status === 503) {
                msg = 'Service temporarily unavailable. Browser may be required for this stream.';
            } else if (e.response?.data?.detail) {
                msg = e.response.data.detail;
            } else if (e.message) {
                msg = e.message;
            }
            
            setError(msg);
            return null;
        } finally {
            setResolving(false);
        }
    };

    return { resolve, resolving, error };
}
