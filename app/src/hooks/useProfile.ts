import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useEffect, useState } from 'react';
import type { UserProfile } from '../types';

const USERNAME_KEY = 'username';

export async function getUsername(): Promise<string> {
  const stored = await AsyncStorage.getItem(USERNAME_KEY);
  return stored?.trim() || 'Local User';
}

export async function setUsername(username: string): Promise<void> {
  await AsyncStorage.setItem(USERNAME_KEY, username.trim());
}

export function useProfile() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchProfile = useCallback(async () => {
    try {
      const username = await getUsername();
      setProfile({ username });
    } catch (error) {
      console.error('Failed to fetch profile:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  return { profile, loading, refetch: fetchProfile };
}
