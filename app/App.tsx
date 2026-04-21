import React, { useCallback, useEffect, useState } from 'react';
import { View, StyleSheet, Linking, Platform } from 'react-native';
import * as SplashScreen from 'expo-splash-screen';
import { NavigationContainer, LinkingOptions } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { Home, Library, PlusCircle, Settings, Users } from 'lucide-react-native';
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
import { CommunityScreen } from './src/screens/CommunityScreen';
import { StreamProvider } from './src/context/StreamContext';
import { CommunityProvider } from './src/context/CommunityContext';
import { CustomSplashScreen } from './src/components/CustomSplashScreen';
import { Palette } from './src/theme/Theme';

SplashScreen.preventAutoHideAsync();

const Tab = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

const linking: LinkingOptions<any> = {
  prefixes: ['streamwatch://', 'https://streamwatch.snowballons.com', 'https://lnuxpkwnbesqrqsxyiek.supabase.co'],
  config: {
    screens: {
      Auth: 'auth',
      ResetPassword: 'reset-password',
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
      <Tab.Screen name="Add" component={AddScreen} options={{
        tabBarIcon: ({ color }) => (
          <View style={styles.addButton}>
            <PlusCircle color="#fff" size={26} />
          </View>
        ),
        tabBarLabel: () => null,
      }} />
      <Tab.Screen name="Community" component={CommunityScreen} options={{ tabBarIcon: ({ color, focused }) => <Users color={focused ? Palette.accent : color} size={24} /> }} />
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

        const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
          setSession(session);
        });

        // Handle initial URL
        const initialUrl = await Linking.getInitialURL();
        if (initialUrl) {
          handleDeepLink(initialUrl);
        }

        await new Promise(resolve => setTimeout(resolve, 2000));
        
        return () => subscription.unsubscribe();
      } catch (e) {
        console.error('Network error:', e);
      } finally {
        setAppIsReady(true);
      }
    }

    prepare();
  }, []);

  const handleDeepLink = async (url: string) => {
    if (!url) return;
    
    // Check if URL contains session info (access_token or type=recovery)
    if (url.includes('access_token=') || url.includes('type=recovery')) {
      // Supabase's setSession or getSession will pick up the hash fragment automatically
      // if detectSessionInUrl is true, but we can manually refresh here
      const { data } = await supabase.auth.getSession();
      if (data.session) setSession(data.session);
    }
  };

  useEffect(() => {
    const subscription = Linking.addEventListener('url', (event) => {
      handleDeepLink(event.url);
    });
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
      <CommunityProvider>
        <View style={styles.container} onLayout={onLayoutRootView}>
          <NavigationContainer linking={linking}>
            <Stack.Navigator screenOptions={{ headerShown: false, animation: 'slide_from_bottom' }}>
              <Stack.Screen name="MainTabs" component={TabNavigator} />
              <Stack.Screen name="Player" component={PlayerScreen} options={{ presentation: 'fullScreenModal' }} />
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
