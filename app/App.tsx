import React, { useCallback, useEffect, useState } from 'react';
import { View, StyleSheet } from 'react-native';
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
import { StreamProvider } from './src/context/StreamContext';
import { CustomSplashScreen } from './src/components/CustomSplashScreen';
import { Palette } from './src/theme/Theme';

// Keep the splash screen visible while we fetch resources
SplashScreen.preventAutoHideAsync();

const Tab = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

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
        // Get initial session with timeout
        const { data: { session }, error } = await supabase.auth.getSession();
        if (error) {
          console.error('Supabase session error:', error);
        } else {
          setSession(session);
        }

        // Set up auth listener
        supabase.auth.onAuthStateChange((_event, session) => setSession(session));

        // Simulate loading time for branded splash
        await new Promise(resolve => setTimeout(resolve, 2000));
      } catch (e) {
        console.error('Network error:', e);
      } finally {
        setAppIsReady(true);
      }
    }

    prepare();
  }, []);

  const onLayoutRootView = useCallback(async () => {
    if (appIsReady) {
      // Hide the native splash screen
      await SplashScreen.hideAsync();
    }
  }, [appIsReady]);

  // Always hide the native splash immediately to show our custom one
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
        <AuthScreen />
      </View>
    );
  }

  return (
    <StreamProvider>
      <View style={styles.container} onLayout={onLayoutRootView}>
        <NavigationContainer>
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
