import * as SecureStore from 'expo-secure-store';

const SESSION_TOKEN_KEY = 'tukiwatch_session_token';

export async function getSessionToken(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(SESSION_TOKEN_KEY);
  } catch {
    return null;
  }
}

export async function setSessionToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(SESSION_TOKEN_KEY, token);
}

export async function clearSessionToken(): Promise<void> {
  try {
    await SecureStore.deleteItemAsync(SESSION_TOKEN_KEY);
  } catch {
    // Already absent — nothing to do.
  }
}

export async function hasSessionToken(): Promise<boolean> {
  const token = await getSessionToken();
  return token !== null && token.length > 0;
}
