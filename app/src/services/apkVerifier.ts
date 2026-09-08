import * as Crypto from 'expo-crypto';
import * as FileSystem from 'expo-file-system';
import { Alert, Linking } from 'react-native';

/**
 * Download an APK, verify its SHA‑256 hash against the expected value, and
 * launch the OS installer if the hash matches.
 *
 * @param url Remote URL of the APK.
 * @param expectedSha256 Expected SHA‑256 hash (lower‑case hex string).
 */
export async function verifyAndOpenApk(url: string, expectedSha256: string): Promise<void> {
  try {
    // Access cacheDirectory via unknown cast – not typed in SDK typings.
    const cacheDir = (FileSystem as unknown as { cacheDirectory?: string }).cacheDirectory;
    if (!cacheDir) throw new Error('FileSystem.cacheDirectory unavailable');
    const { uri } = await FileSystem.downloadAsync(url, `${cacheDir}update.apk`);

    // Read the file as base64.
    const base64 = await FileSystem.readAsStringAsync(uri, {
      encoding: FileSystem.EncodingType.Base64,
    });

    // Compute SHA‑256 of the base64 string.
    const hash = await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, base64);

    if (hash.toLowerCase() !== expectedSha256.toLowerCase()) {
      Alert.alert(
        'Integrity check failed',
        'The downloaded APK does not match the expected hash. Update aborted.'
      );
      return;
    }

    // Open the local file URI – Android treats this as an install request.
    await Linking.openURL(`file://${uri}`);
  } catch (e) {
    // Network or file error – surface as an alert.
    Alert.alert('Update error', 'Failed to download or verify the update.');
    console.error(e);
  }
}
