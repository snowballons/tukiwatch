import type { RouteProp } from '@react-navigation/native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, View } from 'react-native';
import {
  type BackendConfig,
  parseConnectUri,
  setBackendConfig,
  verifyBackend,
} from '../lib/backendConfig';
import { Palette, Spacing } from '../theme/Theme';

export type ConnectRouteParams = {
  url?: string;
  key?: string;
  updates?: string;
};

type RootNavParams = {
  MainTabs: { screen: 'Settings' } | undefined;
  Connect: ConnectRouteParams;
};

function buildConfigFromParams(params: ConnectRouteParams): BackendConfig | null {
  if (!params.url) return null;
  const config: BackendConfig = { apiUrl: params.url };
  if (params.key) config.apiKey = params.key;
  if (params.updates) config.updateManifestUrl = params.updates;
  return config;
}

export function ConnectScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootNavParams>>();
  const route = useRoute<RouteProp<{ Connect: ConnectRouteParams }, 'Connect'>>();
  const params = route.params || {};

  useEffect(() => {
    const applyConnect = async () => {
      const config = buildConfigFromParams(params) || parseConnectUri(params.url || '');
      if (!config) {
        Alert.alert('Connect Failed', 'The link is not a valid TukiWatch connect link.');
        navigation.navigate('MainTabs', { screen: 'Settings' });
        return;
      }

      const result = await verifyBackend(config);
      if (!result.ok) {
        Alert.alert('Connect Failed', result.detail || 'Could not reach the backend.');
        navigation.navigate('MainTabs', { screen: 'Settings' });
        return;
      }

      await setBackendConfig(config);
      Alert.alert('Connected', `Connected to your backend at ${config.apiUrl}.`);
      navigation.navigate('MainTabs', { screen: 'Settings' });
    };

    applyConnect();
  }, [navigation, params]);

  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color={Palette.primary} />
      <Text style={styles.text}>Connecting to backend...</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
    justifyContent: 'center',
    alignItems: 'center',
  },
  text: {
    color: Palette.textMuted,
    fontSize: 16,
    marginTop: Spacing.md,
  },
});
