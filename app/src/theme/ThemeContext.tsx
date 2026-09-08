import AsyncStorage from '@react-native-async-storage/async-storage';
import type { ReactNode } from 'react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useColorScheme } from 'react-native';
import { DarkColors, LightColors, type ThemeColors } from './Theme';

export type ThemeMode = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'theme_mode';

interface ThemeContextValue {
  colors: ThemeColors;
  mode: ThemeMode;
  isDark: boolean;
  setMode: (mode: ThemeMode) => Promise<void>;
}

const ThemeContext = createContext<ThemeContextValue>({
  colors: DarkColors,
  mode: 'system',
  isDark: true,
  setMode: () => Promise.resolve(),
});

export function ThemeProvider({ children }: { children: ReactNode }) {
  const systemScheme = useColorScheme();
  const [mode, setModeState] = useState<ThemeMode>('system');

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY)
      .then((stored) => {
        if (stored === 'light' || stored === 'dark' || stored === 'system') {
          setModeState(stored);
        }
      })
      .catch(() => undefined);
  }, []);

  const setMode = useCallback(async (next: ThemeMode) => {
    setModeState(next);
    try {
      await AsyncStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Persistence is best-effort; the in-memory mode still applies.
    }
  }, []);

  const isDark = mode === 'dark' || (mode === 'system' && systemScheme !== 'light');

  const value = useMemo<ThemeContextValue>(
    () => ({ colors: isDark ? DarkColors : LightColors, mode, isDark, setMode }),
    [isDark, mode, setMode]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}
