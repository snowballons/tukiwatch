import * as Device from 'expo-device';

import { getBackendConfig } from '../lib/backendConfig';

export interface UpdateManifest {
  version: string;
  versionCode: number;
  apkUrl: string;
  /** arm64-only asset, present on releases published after dual-APK support. */
  apkUrlArm64?: string;
  /** versionCode of the arm64 asset (autoIncrement can differ per variant). */
  versionCodeArm64?: number;
  releaseNotes: string;
  mandatory: boolean;
}

export interface UpdateResult {
  available: boolean;
  manifest?: UpdateManifest;
}

export async function checkForUpdate(currentVersionCode: number): Promise<UpdateResult> {
  const config = await getBackendConfig();
  const manifestUrl = config.updateManifestUrl || process.env.EXPO_PUBLIC_UPDATE_MANIFEST_URL;

  if (!manifestUrl) {
    return { available: false };
  }

  const response = await fetch(manifestUrl, {
    headers: { 'Cache-Control': 'no-cache' },
  });

  if (!response.ok) throw new Error(`Failed to fetch version manifest: ${response.status}`);

  const manifest: UpdateManifest = await response.json();

  return {
    available: selectVersionCode(manifest) > currentVersionCode,
    manifest,
  };
}

/**
 * True when the device can run the arm64-v8a APK. Anything else (32-bit ARM,
 * x86 emulators, unknown) falls back to the universal APK.
 */
export function isArm64Device(): boolean {
  try {
    const arches = Device.supportedCpuArchitectures;
    if (Array.isArray(arches) && arches.length > 0) {
      return arches.includes('arm64-v8a');
    }
  } catch {
    // Unknown architecture — fall through to the universal build.
  }
  return false;
}

/** Pick the smallest APK this device can install (arm64 when available). */
export function selectApkUrl(manifest: UpdateManifest): string {
  if (isArm64Device() && manifest.apkUrlArm64) {
    return manifest.apkUrlArm64;
  }
  return manifest.apkUrl;
}

/** Version code of the asset {@link selectApkUrl} would pick. */
export function selectVersionCode(manifest: UpdateManifest): number {
  if (isArm64Device() && typeof manifest.versionCodeArm64 === 'number') {
    return manifest.versionCodeArm64;
  }
  return manifest.versionCode;
}
