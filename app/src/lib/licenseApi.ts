import axios, { isAxiosError } from 'axios';
import { Platform } from 'react-native';

import { getBackendConfig } from './backendConfig';
import { getSessionToken } from './sessionToken';

export type LicenseErrorType =
  | 'license_error'
  | 'activation_limit_error'
  | 'session_error'
  | 'network_error';

export class LicenseApiError extends Error {
  type: LicenseErrorType;

  constructor(type: LicenseErrorType, message: string) {
    super(message);
    this.type = type;
  }
}

export interface ActivateResult {
  sessionToken: string;
  tier: string;
  expiresIn: number;
}

export interface SessionStatus {
  valid: boolean;
  tier?: string;
  licenseId?: string;
  expiresAt?: string;
}

const REQUEST_TIMEOUT_MS = 15000;

async function getBaseUrl(): Promise<string> {
  const config = await getBackendConfig();
  return config.apiUrl.replace(/\/+$/, '');
}

function toLicenseError(error: unknown, fallback: string): LicenseApiError {
  if (isAxiosError(error)) {
    const detail = error.response?.data?.detail as
      | { error?: string; type?: LicenseErrorType }
      | undefined;
    if (detail?.error) {
      const type: LicenseErrorType =
        detail.type === 'activation_limit_error' ||
        detail.type === 'session_error' ||
        detail.type === 'license_error'
          ? detail.type
          : 'license_error';
      return new LicenseApiError(type, detail.error);
    }
    if (error.response) {
      return new LicenseApiError('license_error', fallback);
    }
  }
  return new LicenseApiError('network_error', 'Cannot reach the backend.');
}

/** Device label sent on activation so Polar can enforce the device limit. */
function defaultDeviceLabel(): string {
  return `TukiWatch app (${Platform.OS})`;
}

/**
 * Exchange a Polar license key (TUKI_…) for a short-lived session token.
 * The key itself is never stored — only the returned session is kept.
 */
export async function activateLicense(
  licenseKey: string,
  label: string = defaultDeviceLabel()
): Promise<ActivateResult> {
  const baseUrl = await getBaseUrl();
  try {
    const response = await axios.post(
      `${baseUrl}/api/license/activate`,
      { license_key: licenseKey.trim(), label },
      { timeout: REQUEST_TIMEOUT_MS }
    );
    const data = response.data as {
      session_token?: string;
      tier?: string;
      expires_in?: number;
    };
    if (!data.session_token) {
      throw new LicenseApiError('license_error', 'Invalid license key.');
    }
    return {
      sessionToken: data.session_token,
      tier: data.tier ?? 'supporter',
      expiresIn: data.expires_in ?? 24 * 3600,
    };
  } catch (error: unknown) {
    if (error instanceof LicenseApiError) throw error;
    throw toLicenseError(error, 'Invalid license key.');
  }
}

/** Check the stored session against the backend. No session → valid: false. */
export async function validateSession(): Promise<SessionStatus> {
  const token = await getSessionToken();
  if (!token) return { valid: false };
  const baseUrl = await getBaseUrl();
  try {
    const response = await axios.post(
      `${baseUrl}/api/license/validate`,
      {},
      { headers: { Authorization: `Bearer ${token}` }, timeout: REQUEST_TIMEOUT_MS }
    );
    const data = response.data as {
      valid?: boolean;
      tier?: string;
      license_id?: string;
      expires_at?: string;
    };
    if (!data.valid) return { valid: false };
    return {
      valid: true,
      tier: data.tier,
      licenseId: data.license_id,
      expiresAt: data.expires_at,
    };
  } catch {
    return { valid: false };
  }
}

/** Revoke the stored session server-side. Never throws. */
export async function deactivateSession(): Promise<void> {
  const token = await getSessionToken();
  if (!token) return;
  const baseUrl = await getBaseUrl();
  try {
    await axios.post(
      `${baseUrl}/api/license/deactivate`,
      { session_token: token },
      { timeout: REQUEST_TIMEOUT_MS }
    );
  } catch {
    // Session expires naturally — logout proceeds regardless.
  }
}
