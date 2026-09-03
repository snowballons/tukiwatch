import { View, Text, StyleSheet } from 'react-native';
import { Palette, Spacing } from '../../theme/Theme';
import { useBackendConfig } from '../../lib/backendConfig';

export const BackendSettings: React.FC = () => {
  const { config } = useBackendConfig();

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Backend Configuration</Text>
      <Text style={styles.description}>
        Configure your TukiWatch backend server. Supporter tokens and session management are handled through Lemon Squeezy licensing.
      </Text>
      {config ? (
        <View style={styles.statusBox}>
          <Text style={styles.label}>Backend URL:</Text>
          <Text style={styles.value}>{config.apiUrl}</Text>
          {config.updateManifestUrl && (
            <>
              <Text style={styles.label}>Update Manifest URL:</Text>
              <Text style={styles.value}>{config.updateManifestUrl}</Text>
            </>
          )}
        </View>
      ) : (
        <Text style={styles.loading}>Loading backend configuration...</Text>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#1a1a1a',
    borderRadius: 12,
    padding: Spacing.md,
    marginVertical: Spacing.sm,
    borderWidth: 1,
    borderColor: '#333',
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    color: Palette.text,
    marginBottom: Spacing.xs,
  },
  description: {
    fontSize: 13,
    color: '#aaa',
    marginBottom: Spacing.md,
    lineHeight: 18,
  },
  statusBox: {
    backgroundColor: '#222',
    borderRadius: 8,
    padding: Spacing.md,
    borderWidth: 1,
    borderColor: '#444',
  },
  label: {
    fontSize: 13,
    color: '#888',
    marginTop: Spacing.sm,
  },
  value: {
    fontSize: 14,
    color: Palette.text,
    fontFamily: 'monospace',
  },
  loading: {
    fontSize: 14,
    color: '#888',
    fontStyle: 'italic',
  },
});
