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
                const detail = e.response.data?.detail;
                msg = typeof detail === 'string' ? detail : 'Invalid URL format. Please check the URL and try again.';
            } else if (e.response?.status === 404) {
                const detail = e.response.data?.detail;
                msg = typeof detail === 'string' ? detail : 'No streams found for this URL.';
            } else if (e.response?.status === 422) {
                const detail = e.response.data?.detail;
                if (typeof detail === 'object' && detail !== null) {
                    // Structured error (browser required, plugin error)
                    msg = detail.error || 'Unsupported streaming service or plugin error.';
                    if (detail.alternative) {
                        msg += '\n' + detail.alternative;
                    }
                } else {
                    msg = typeof detail === 'string' ? detail : 'Unsupported streaming service or plugin error.';
                }
            } else if (e.response?.status === 429) {
                const retryAfter = e.response.data?.retry_after || 60;
                msg = `Too many requests. Please wait ${retryAfter} seconds and try again.`;
            } else if (e.response?.data?.detail) {
                const detail = e.response.data.detail;
                msg = typeof detail === 'object' ? (detail.error || JSON.stringify(detail)) : detail;
            } else if (e.message) {
                if (e.message === 'Network Error') {
                    msg = 'Cannot connect to the server. Please check your internet connection and try again.';
                } else {
                    msg = e.message;
                }
            }
            
            setError(msg);
            return null;
        } finally {
            setResolving(false);
        }
    };

    return { resolve, resolving, error };
}
