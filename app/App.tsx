import React, { useCallback, useEffect, useState } from 'react';
import { View, StyleSheet, Linking } from 'react-native';
import * as SplashScreen from 'expo-splash-screen';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Home, Library, PlusCircle, Settings } from 'lucide-react-native';
import { supabase } from './lib/supabase';
import { Session } from '@supabase/supabase-js';

// Screen Imports
import { AuthScreen } from './src/screens/AuthScreen';
import { HomeScreen } from './src/screens/HomeScreen';
import { LibraryScreen } from './src/screens/LibraryScreen';
import { AddScreen } from './src/screens/AddScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { PlayerScreen } from './src/screens/PlayerScreen';
import { ForgotPasswordScreen } from './src/screens/ForgotPasswordScreen';
import { ResetPasswordScreen } from './src/screens/ResetPasswordScreen';
import { StreamProvider } from './src/context/StreamContext';
import { CustomSplashScreen } from './src/components/CustomSplashScreen';
import { Palette } from './src/theme/Theme';

SplashScreen.preventAutoHideAsync();

const Tab = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

const linking = {
  prefixes: ['streamwatch://', 'https://lnuxpkwnbesqrqsxyiek.supabase.co'],
  config: {
    screens: {
      Auth: 'auth',
      ResetPassword: 'reset-password',
      MainTabs: {
        screens: {
          Home: 'home',
          'My List': 'library',
          Add: 'add',
          Settings: 'settings',
        },
      },
      Player: 'player',
    },
  },
};

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
      <Tab.Screen name="Home" component={HomeScreen} options={{ tabBarIcon: ({ color }) => <Home color={color} size={24} /> }} />
      <Tab.Screen name="My List" component={LibraryScreen} options={{ tabBarIcon: ({ color }) => <Library color={color} size={24} /> }} />
      <Tab.Screen name="Add" component={AddScreen} options={{ tabBarIcon: ({ color }) => <PlusCircle color={color} size={24} /> }} />
      <Tab.Screen name="Settings" component={SettingsScreen} options={{ tabBarIcon: ({ color }) => <Settings color={color} size={24} /> }} />
    </Tab.Navigator>
  );
}

export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [appIsReady, setAppIsReady] = useState(false);

  useEffect(() => {
    async function prepare() {
      try {
        const { data: { session }, error } = await supabase.auth.getSession();
        if (error) {
          console.error('Supabase session error:', error);
        } else {
          setSession(session);
        }

        supabase.auth.onAuthStateChange((_event, session) => setSession(session));

        await new Promise(resolve => setTimeout(resolve, 2000));
      } catch (e) {
        console.error('Network error:', e);
      } finally {
        setAppIsReady(true);
      }
    }

    prepare();
  }, []);

  useEffect(() => {
    const handleDeepLink = async (event: { url: string }) => {
      const url = event.url;
      if (url.includes('reset-password') || url.includes('type=recovery')) {
        // Deep link will be handled by NavigationContainer
      }
    };

    const subscription = Linking.addEventListener('url', handleDeepLink);
    return () => subscription.remove();
  }, []);

  const onLayoutRootView = useCallback(async () => {
    if (appIsReady) {
      await SplashScreen.hideAsync();
    }
  }, [appIsReady]);

  useEffect(() => {
    SplashScreen.hideAsync();
  }, []);

  if (!appIsReady) {
    return (
      <View style={styles.container}>
        <CustomSplashScreen />
      </View>
    );
  }

  if (!session) {
    return (
      <View style={styles.container} onLayout={onLayoutRootView}>
        <NavigationContainer linking={linking}>
          <Stack.Navigator screenOptions={{ headerShown: false }}>
            <Stack.Screen name="Auth" component={AuthScreen} />
            <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
            <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
          </Stack.Navigator>
        </NavigationContainer>
      </View>
    );
  }

  return (
    <StreamProvider>
      <View style={styles.container} onLayout={onLayoutRootView}>
        <NavigationContainer linking={linking}>
          <Stack.Navigator screenOptions={{ headerShown: false, animation: 'slide_from_bottom' }}>
            <Stack.Screen name="MainTabs" component={TabNavigator} />
            <Stack.Screen name="Player" component={PlayerScreen} options={{ presentation: 'fullScreenModal' }} />
          </Stack.Navigator>
        </NavigationContainer>
      </View>
    </StreamProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
});
