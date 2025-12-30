import { useState } from 'react';
import { resolveStream } from '../services/engine';
import { StreamResolution } from '../types';

export function useStreamResolver() {
    const [resolving, setResolving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resolve = async (url: string): Promise<StreamResolution | null> => {
        setResolving(true);
        setError(null);
        try {
            const data = await resolveStream(url);
            return data as StreamResolution;
        } catch (e: any) {
            const msg = e.response?.data?.detail || e.message || 'Failed to resolve stream';
            setError(msg);
            return null;
        } finally {
            setResolving(false);
        }
    };

    return { resolve, resolving, error };
}
