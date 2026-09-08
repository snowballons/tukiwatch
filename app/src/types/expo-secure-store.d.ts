declare module 'expo-secure-store' {
  /**
   * Retrieve a value for the given key. Returns `null` if the key does not exist.
   */
  export function getItemAsync(key: string, options?: any): Promise<string | null>;

  /**
   * Store a value for the given key.
   */
  export function setItemAsync(key: string, value: string, options?: any): Promise<void>;

  /**
   * Delete the value for the given key.
   */
  export function deleteItemAsync(key: string, options?: any): Promise<void>;
}
