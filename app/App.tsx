// Screen Imports
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import {
  createNavigationContainerRef,
  type LinkingOptions,
  NavigationContainer,
} from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { Session } from '@supabase/supabase-js';
import * as SplashScreen from 'expo-splash-screen';
import { Home, Library, PlusCircle, Settings, Users } from 'lucide-react-native';
import { useCallback, useEffect, useState } from 'react';
import { Linking, StyleSheet, View } from 'react-native';
import { supabase } from './lib/supabase';
import { CustomSplashScreen } from './src/components/CustomSplashScreen';
import { CommunityProvider } from './src/context/CommunityContext';
import { StreamProvider } from './src/context/StreamContext';
import { AddScreen } from './src/screens/AddScreen';
import { AuthScreen } from './src/screens/AuthScreen';
import { CommunityScreen } from './src/screens/CommunityScreen';
import { ForgotPasswordScreen } from './src/screens/ForgotPasswordScreen';
import { HomeScreen } from './src/screens/HomeScreen';
import { LibraryScreen } from './src/screens/LibraryScreen';
import { OnboardingScreen } from './src/screens/OnboardingScreen';
import { PlayerScreen } from './src/screens/PlayerScreen';
import { ResetPasswordScreen } from './src/screens/ResetPasswordScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { Palette } from './src/theme/Theme';

SplashScreen.preventAutoHideAsync();

const Tab = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

const linking: LinkingOptions<Record<string, object | undefined>> = {
  prefixes: [
    'tukiwatch://',
    'https://tukiwatch.snowballons.com',
    'https://lnuxpkwnbesqrqsxyiek.supabase.co',
  ],
  config: {
    screens: {
      Auth: 'auth',
      ForgotPassword: 'forgot-password',
      ResetPassword: 'auth/reset-password',
      MainTabs: {
        screens: {
          Home: 'home',
          'My List': 'library',
          Add: 'add',
          Community: 'community',
          Settings: 'settings',
        },
      },
      Player: 'player',
    },
  },
};

const navigationRef = createNavigationContainerRef<Record<string, object | undefined>>();

function TabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: Palette.background,
          borderTopColor: Palette.border,
          height: 85,
          paddingBottom: 25,
        },
        tabBarActiveTintColor: Palette.primary,
        tabBarInactiveTintColor: Palette.textMuted,
      }}
    >
      <Tab.Screen
        name="Home"
        component={HomeScreen}
        options={{ tabBarIcon: ({ color }) => <Home color={color} size={24} /> }}
      />
      <Tab.Screen
        name="My List"
        component={LibraryScreen}
        options={{ tabBarIcon: ({ color }) => <Library color={color} size={24} /> }}
      />
      <Tab.Screen
        name="Add"
        component={AddScreen}
        options={{
          tabBarIcon: () => (
            <View style={styles.addButton}>
              <PlusCircle color="#fff" size={26} />
            </View>
          ),
          tabBarLabel: () => null,
        }}
      />
      <Tab.Screen
        name="Community"
        component={CommunityScreen}
        options={{
          tabBarIcon: ({ color, focused }) => (
            <Users color={focused ? Palette.accent : color} size={24} />
          ),
        }}
      />
      <Tab.Screen
        name="Settings"
        component={SettingsScreen}
        options={{ tabBarIcon: ({ color }) => <Settings color={color} size={24} /> }}
      />
    </Tab.Navigator>
  );
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [appIsReady, setAppIsReady] = useState(false);
  const [onboardingComplete, setOnboardingComplete] = useState<boolean | null>(null);

  useEffect(() => {
    AsyncStorage.getItem('onboarding_complete')
      .then((val) => setOnboardingComplete(val === 'true'))
      .catch(() => setOnboardingComplete(true)); // on error, skip onboarding
  }, []);

  const handleDeepLink = useCallback(async (url: string) => {
    if (!url) return;

    console.log('Deep link received:', url);

    const urlObj = new URL(url);

    // Handle hash fragment (implicit flow)
    if (url.includes('#')) {
      const hash = url.split('#')[1];
      if (hash?.includes('access_token=')) {
        const params = new URLSearchParams(hash);
        const access_token = params.get('access_token');
        const refresh_token = params.get('refresh_token');

        if (access_token && refresh_token) {
          const { data } = await supabase.auth.setSession({
            access_token,
            refresh_token,
          });
          if (data.session) {
            setSession(data.session);
          }
        }
      }
    }

    // Handle PKCE flow (query param ?code=...)
    const code = urlObj.searchParams.get('code');
    if (code) {
      const { data } = await supabase.auth.exchangeCodeForSession(code);
      if (data.session) {
        setSession(data.session);
      }
    }
  }, []);

  useEffect(() => {
    async function prepare() {
      try {
        const {
          data: { session },
          error,
        } = await supabase.auth.getSession();
        if (error) {
          console.error('Supabase session error:', error);
        } else {
          setSession(session);
        }

        const {
          data: { subscription },
        } = supabase.auth.onAuthStateChange((_event, session) => {
          setSession(session);
        });

        // Handle initial URL
        const initialUrl = await Linking.getInitialURL();
        if (initialUrl) {
          handleDeepLink(initialUrl);
        }

        await new Promise((resolve) => setTimeout(resolve, 2000));

        return () => subscription.unsubscribe();
      } catch (e) {
        console.error('Network error:', e);
      } finally {
        setAppIsReady(true);
      }
    }

    prepare();
  }, [handleDeepLink]);

  useEffect(() => {
    const subscription = Linking.addEventListener('url', (event) => {
      handleDeepLink(event.url);
    });
    return () => subscription.remove();
  }, [handleDeepLink]);

  const onLayoutRootView = useCallback(async () => {
    if (appIsReady) {
      await SplashScreen.hideAsync();
    }
  }, [appIsReady]);

  useEffect(() => {
    SplashScreen.hideAsync();
  }, []);

  if (!appIsReady || onboardingComplete === null) {
    return (
      <View style={styles.container}>
        <CustomSplashScreen />
      </View>
    );
  }

  if (!onboardingComplete) {
    return (
      <View style={styles.container} onLayout={onLayoutRootView}>
        <OnboardingScreen onComplete={() => setOnboardingComplete(true)} />
      </View>
    );
  }

  return (
    <StreamProvider>
      <CommunityProvider>
        <View style={styles.container} onLayout={onLayoutRootView}>
          <NavigationContainer linking={linking} ref={navigationRef}>
            <Stack.Navigator screenOptions={{ headerShown: false, animation: 'slide_from_bottom' }}>
              {session ? (
                <>
                  <Stack.Screen name="MainTabs" component={TabNavigator} />
                  <Stack.Screen
                    name="Player"
                    component={PlayerScreen}
                    options={{ presentation: 'fullScreenModal' }}
                  />
                  <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
                </>
              ) : (
                <>
                  <Stack.Screen name="Auth" component={AuthScreen} />
                  <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
                  <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
                </>
              )}
            </Stack.Navigator>
          </NavigationContainer>
        </View>
      </CommunityProvider>
    </StreamProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
  addButton: {
    backgroundColor: Palette.primary,
    borderRadius: 16,
    padding: 10,
    marginBottom: 4,
  },
});
