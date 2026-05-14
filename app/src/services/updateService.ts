const VERSION_MANIFEST_URL = 'https://download.snowballons.com/version.json';

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
  const response = await fetch(VERSION_MANIFEST_URL, {
    headers: { 'Cache-Control': 'no-cache' },
  });

  if (!response.ok) throw new Error(`Failed to fetch version manifest: ${response.status}`);

  const manifest: UpdateManifest = await response.json();

  return {
    available: manifest.versionCode > currentVersionCode,
    manifest,
  };
}
