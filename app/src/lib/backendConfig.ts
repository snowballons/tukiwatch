import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useEffect, useState } from 'react';

export interface BackendConfig {
  apiUrl: string;
  apiKey?: string;
  updateManifestUrl?: string;
}

export interface BackendVerification {
  ok: boolean;
  reason?: 'unreachable' | 'unauthorized' | 'invalid';
  detail?: string;
}

const CONFIG_KEY = 'backend_config';

export const DEFAULT_BACKEND_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8000';
export const DEFAULT_API_KEY = process.env.EXPO_PUBLIC_BACKEND_API_KEY || '';
export const DEFAULT_UPDATE_MANIFEST_URL =
  process.env.EXPO_PUBLIC_UPDATE_MANIFEST_URL || 'https://downloads.snowballons.com/version.json';

export function getDefaultBackendConfig(): BackendConfig {
  return {
    apiUrl: DEFAULT_BACKEND_URL,
    apiKey: DEFAULT_API_KEY,
    updateManifestUrl: DEFAULT_UPDATE_MANIFEST_URL,
  };
}

export async function getBackendConfig(): Promise<BackendConfig> {
  try {
    const stored = await AsyncStorage.getItem(CONFIG_KEY);
    if (stored) {
      return JSON.parse(stored) as BackendConfig;
    }
  } catch (error) {
    console.error('Failed to read backend config:', error);
  }
  return getDefaultBackendConfig();
}

export async function setBackendConfig(config: BackendConfig): Promise<void> {
  await AsyncStorage.setItem(CONFIG_KEY, JSON.stringify(config));
}

export async function clearBackendConfig(): Promise<void> {
  await AsyncStorage.removeItem(CONFIG_KEY);
}

function parseQueryString(query: string): Record<string, string> {
  const params: Record<string, string> = {};
  if (!query) return params;
  for (const pair of query.split('&')) {
    const [rawKey, rawValue = ''] = pair.split('=');
    params[decodeURIComponent(rawKey)] = decodeURIComponent(rawValue.replace(/\+/g, ' '));
  }
  return params;
}

/**
 * Parse a `tukiwatch://connect?url=..&key=..&updates=..` URI (as produced by a
 * QR code or a deep link) into a BackendConfig. Returns null when the URI is
 * not a valid connect link or lacks a backend URL.
 */
export function parseConnectUri(uri: string): BackendConfig | null {
  const trimmed = uri.trim();
  let params: Record<string, string> | null = null;

  if (trimmed.startsWith('tukiwatch://connect')) {
    const queryIndex = trimmed.indexOf('?');
    const query = queryIndex >= 0 ? trimmed.slice(queryIndex + 1) : '';
    params = parseQueryString(query);
  } else {
    try {
      const parsed = new URL(trimmed);
      if (parsed.pathname === '/connect') {
        params = parseQueryString(parsed.search.replace(/^\?/, ''));
      }
    } catch {
      // Not a URL — not a connect link.
    }
  }

  if (!params?.url) return null;

  return {
    apiUrl: params.url,
    apiKey: params.key || undefined,
    updateManifestUrl: params.updates || undefined,
  };
}

/**
 * Build the shareable connect URI for a config. The app only consumes this via
 * QR codes today, but the canonical form is reused for deep links.
 */
export function buildConnectUri(config: BackendConfig): string {
  const parts: string[] = [];
  if (config.apiUrl) parts.push(`url=${encodeURIComponent(config.apiUrl)}`);
  if (config.apiKey) parts.push(`key=${encodeURIComponent(config.apiKey)}`);
  if (config.updateManifestUrl)
    parts.push(`updates=${encodeURIComponent(config.updateManifestUrl)}`);
  return `tukiwatch://connect?${parts.join('&')}`;
}

function fetchWithTimeout(url: string, options: RequestInit, timeoutMs = 5000): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  return fetch(url, { ...options, signal: controller.signal }).finally(() => clearTimeout(timer));
}

/**
 * Validate a backend config by pinging /health and confirming the API key
 * against an authenticated endpoint. Returns a structured result.
 */
export async function verifyBackend(config: BackendConfig): Promise<BackendVerification> {
  const base = config.apiUrl.replace(/\/+$/, '');
  try {
    const healthRes = await fetchWithTimeout(`${base}/health`, {}, 8000);
    if (!healthRes.ok) {
      return {
        ok: false,
        reason: 'unreachable',
        detail: `Health check failed (${healthRes.status})`,
      };
    }
    const health = await healthRes.json();
    if (health?.status !== 'healthy') {
      return { ok: false, reason: 'invalid', detail: 'Not a TukiWatch backend' };
    }

    if (config.apiKey) {
      const authRes = await fetchWithTimeout(`${base}/api/status-batch`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-API-Key': config.apiKey,
        },
        body: JSON.stringify({ urls: [] }),
      });
      if (authRes.status === 401) {
        return { ok: false, reason: 'unauthorized', detail: 'Invalid API key' };
      }
      if (!authRes.ok) {
        return {
          ok: false,
          reason: 'invalid',
          detail: `Backend rejected the request (${authRes.status})`,
        };
      }
    }

    return { ok: true };
  } catch {
    return { ok: false, reason: 'unreachable', detail: 'Cannot reach the backend' };
  }
}

export function useBackendConfig() {
  const [config, setConfig] = useState<BackendConfig | null>(null);
  const [isCustom, setIsCustom] = useState(false);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    try {
      const stored = await AsyncStorage.getItem(CONFIG_KEY);
      setIsCustom(!!stored);
      setConfig(stored ? (JSON.parse(stored) as BackendConfig) : getDefaultBackendConfig());
    } catch (error) {
      console.error('Failed to load backend config:', error);
      setConfig(getDefaultBackendConfig());
      setIsCustom(false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const save = useCallback(
    async (next: BackendConfig) => {
      await setBackendConfig(next);
      await reload();
    },
    [reload]
  );

  const reset = useCallback(async () => {
    await clearBackendConfig();
    await reload();
  }, [reload]);

  return { config, isCustom, loading, save, reset, reload };
}
