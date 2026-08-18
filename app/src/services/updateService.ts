import { getBackendConfig } from '../lib/backendConfig';

export interface UpdateManifest {
  version: string;
  versionCode: number;
  apkUrl: string;
  releaseNotes: string;
  mandatory: boolean;
}

export interface UpdateResult {
  available: boolean;
  manifest?: UpdateManifest;
}

export async function checkForUpdate(currentVersionCode: number): Promise<UpdateResult> {
  const config = await getBackendConfig();
  const manifestUrl =
    config.updateManifestUrl ||
    process.env.EXPO_PUBLIC_UPDATE_MANIFEST_URL ||
    'https://downloads.snowballons.com/version.json';

  const response = await fetch(manifestUrl, {
    headers: { 'Cache-Control': 'no-cache' },
  });

  if (!response.ok) throw new Error(`Failed to fetch version manifest: ${response.status}`);

  const manifest: UpdateManifest = await response.json();

  return {
    available: manifest.versionCode > currentVersionCode,
    manifest,
  };
}
