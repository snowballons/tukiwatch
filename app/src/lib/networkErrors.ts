/**
 * Shared network-error classification.
 *
 * Covers axios errors, React Native `fetch` TypeErrors, and AbortController
 * timeouts, so every screen can tell "no internet" apart from server or app
 * errors and show the right offline UI with a retry affordance.
 */

export const OFFLINE_MESSAGE = 'No internet connection. Check your connection and try again.';

interface ErrorLike {
  message?: unknown;
  code?: unknown;
  name?: unknown;
}

const NETWORK_MESSAGE_HINTS = [
  'network error', // axios
  'network request failed', // RN fetch
  'failed to fetch',
  'load failed', // iOS NSURLError passthrough
  'network connection lost',
];

const NETWORK_CODES = new Set([
  'ECONNABORTED', // axios timeout
  'ERR_NETWORK', // axios generic network failure
  'ENOTFOUND',
  'EAI_AGAIN',
  'ETIMEDOUT',
  'ECONNREFUSED',
  'EHOSTUNREACH',
  'ENETUNREACH',
  'EPIPE',
]);

export function isNetworkError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const err = error as ErrorLike;

  if (typeof err.message === 'string') {
    const msg = err.message.toLowerCase();
    if (NETWORK_MESSAGE_HINTS.some((hint) => msg.includes(hint))) return true;
  }
  if (typeof err.code === 'string' && NETWORK_CODES.has(err.code.toUpperCase())) {
    return true;
  }
  // Our own fetch timeouts abort with AbortError; a timed-out request is
  // retryable and on mobile usually means the connection dropped.
  if (err.name === 'AbortError' || err.message === 'Aborted') return true;

  return false;
}
