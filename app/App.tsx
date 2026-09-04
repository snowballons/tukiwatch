// Screen Imports
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import {
  createNavigationContainerRef,
  type LinkingOptions,
  NavigationContainer,
  type NavigatorScreenParams,
} from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import * as SplashScreen from 'expo-splash-screen';
import { Home, Library, PlusCircle, Search, Settings } from 'lucide-react-native';
import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { CustomSplashScreen } from './src/components/CustomSplashScreen';
import { StreamProvider } from './src/context/StreamContext';
import { AddScreen } from './src/screens/AddScreen';
import { ConnectScreen } from './src/screens/ConnectScreen';
import { DiscoveryScreen } from './src/screens/DiscoveryScreen';
import { HomeScreen } from './src/screens/HomeScreen';
import { LibraryScreen } from './src/screens/LibraryScreen';
import { OnboardingScreen } from './src/screens/OnboardingScreen';
import { PlayerScreen } from './src/screens/PlayerScreen';
import { SettingsScreen } from './src/screens/SettingsScreen';
import { Palette } from './src/theme/Theme';

SplashScreen.preventAutoHideAsync();

const Tab = createBottomTabNavigator();
const Stack = createNativeStackNavigator();

type MainTabsParamList = {
  Home: undefined;
  'My List': undefined;
  Add: undefined;
  Discovery: undefined;
  Settings: undefined;
};

type RootStackParamList = {
  MainTabs: NavigatorScreenParams<MainTabsParamList>;
  Player: undefined;
  Connect: { url?: string };
};

const linking: LinkingOptions<RootStackParamList> = {
  prefixes: ['tukiwatch://', 'https://tukiwatch.snowballons.com'],
  config: {
    screens: {
      MainTabs: {
        screens: {
          Home: 'home',
          'My List': 'library',
          Add: 'add',
          Discovery: 'discovery',
          Settings: 'settings',
        },
      },
      Player: 'player',
      Connect: 'connect',
    },
  },
};

const navigationRef = createNavigationContainerRef<RootStackParamList>();

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
        name="Discovery"
        component={DiscoveryScreen}
        options={{
          tabBarIcon: ({ color }) => <Search color={color} size={24} />,
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
  const [appIsReady, setAppIsReady] = useState(false);
  const [onboardingComplete, setOnboardingComplete] = useState<boolean | null>(null);

  useEffect(() => {
    AsyncStorage.getItem('onboarding_complete')
      .then((val) => setOnboardingComplete(val === 'true'))
      .catch(() => setOnboardingComplete(true)) // on error, skip onboarding
      .finally(() => setAppIsReady(true));
  }, []);

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
      <View style={styles.container} onLayout={onLayoutRootView}>
        <NavigationContainer linking={linking} ref={navigationRef}>
          <Stack.Navigator screenOptions={{ headerShown: false, animation: 'slide_from_bottom' }}>
            <Stack.Screen name="MainTabs" component={TabNavigator} />
            <Stack.Screen
              name="Player"
              component={PlayerScreen}
              options={{ presentation: 'fullScreenModal' }}
            />
            <Stack.Screen name="Connect" component={ConnectScreen} />
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
  addButton: {
    backgroundColor: Palette.primary,
    borderRadius: 16,
    padding: 10,
    marginBottom: 4,
  },
});
