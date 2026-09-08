import { useState } from 'react';
import { isNetworkError, OFFLINE_MESSAGE } from '../lib/networkErrors';
import { resolveStream } from '../services/engine';
import type { StreamResolution } from '../types';

export function useStreamResolver() {
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resolve = async (
    url: string,
    bypassCache: boolean = false
  ): Promise<StreamResolution | null> => {
    setResolving(true);
    setError(null);
    try {
      const data = await resolveStream(url, bypassCache);
      return data as StreamResolution;
    } catch (e: unknown) {
      // Axios errors carry status/detail payloads; narrow once for the handlers below.
      const err = e as {
        response?: { status?: number; data?: { detail?: unknown; retry_after?: number } };
        message?: string;
      };
      // Enhanced error handling based on HTTP status codes
      let msg = 'Failed to resolve stream';

      if (err.response?.status === 400) {
        const detail = err.response.data?.detail;
        msg =
          typeof detail === 'string'
            ? detail
            : 'Invalid URL format. Please check the URL and try again.';
      } else if (err.response?.status === 404) {
        const detail = err.response.data?.detail;
        msg = typeof detail === 'string' ? detail : 'No streams found for this URL.';
      } else if (err.response?.status === 422) {
        const detail = err.response.data?.detail;
        if (typeof detail === 'object' && detail !== null) {
          // Structured error (browser required, plugin error)
          const structured = detail as { error?: string; alternative?: string };
          msg = structured.error || 'Unsupported streaming service or plugin error.';
          if (structured.alternative) {
            msg += `\n${structured.alternative}`;
          }
        } else {
          msg =
            typeof detail === 'string' ? detail : 'Unsupported streaming service or plugin error.';
        }
      } else if (err.response?.status === 429) {
        const retryAfter = err.response.data?.retry_after || 60;
        msg = `Too many requests. Please wait ${retryAfter} seconds and try again.`;
      } else {
        const detail: unknown = err.response?.data?.detail;
        if (typeof detail === 'string' && detail) {
          msg = detail;
        } else if (typeof detail === 'object' && detail !== null) {
          const structured = detail as { error?: string };
          msg = structured.error || JSON.stringify(detail);
        } else if (err.message) {
          if (isNetworkError(e)) {
            msg = OFFLINE_MESSAGE;
          } else {
            msg = err.message;
          }
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
