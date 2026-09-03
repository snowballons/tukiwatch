import Constants from 'expo-constants';
import { ChevronRight, RotateCcw, Server, User } from 'lucide-react-native';
import type React from 'react';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { exportFavorites, importFavorites } from '../../lib/db';
import { useStreams } from '../context/StreamContext';
import { useProfile } from '../hooks/useProfile';
import { parseConnectUri, useBackendConfig, verifyBackend } from '../lib/backendConfig';
import { checkForUpdate } from '../services/updateService';
import { Palette, Spacing } from '../theme/Theme';
import { BackendSettings } from './Settings/BackendSettings';

const Section: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <View style={styles.section}>
    <Text style={styles.sectionTitle}>{title}</Text>
    <View style={styles.sectionContent}>{children}</View>
  </View>
);

const ListItem: React.FC<{
  label: string;
  value?: string;
  onPress?: () => void;
  isDestructive?: boolean;
  icon?: React.ReactNode;
}> = ({ label, value, onPress, isDestructive, icon }) => (
  <TouchableOpacity onPress={onPress} style={styles.listItem} disabled={!onPress}>
    {icon && <View style={styles.listItemIcon}>{icon}</View>}
    <Text style={[styles.listItemLabel, isDestructive && styles.destructiveText]}>{label}</Text>
    <View style={styles.listItemValueContainer}>
      {value && <Text style={styles.listItemValue}>{value}</Text>}
      {onPress && !isDestructive && <ChevronRight color={Palette.textMuted} size={18} />}
    </View>
  </TouchableOpacity>
);

// Read the app version from the built app config so it can't drift.
// versionCode comes from app.json / EAS remote (with autoIncrement).
const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';
const APP_VERSION_CODE = Constants.expoConfig?.android?.versionCode ?? 0;

export function SettingsScreen() {
  const { profile } = useProfile();
  const { config, isCustom, loading: configLoading, save, reset } = useBackendConfig();
  const { isBackendReachable, reconnect } = useStreams();
  const [showAbout, setShowAbout] = useState(false);
  const [checkingUpdate, setCheckingUpdate] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [verificationDetail, setVerificationDetail] = useState<string | null>(null);

  const handleResetBackend = useCallback(() => {
    Alert.alert('Use Default Server', 'Reset to the default TukiWatch backend?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Reset',
        style: 'destructive',
        onPress: async () => {
          await reset();
          await reconnect();
          setVerificationDetail('Reset to default');
        },
      },
    ]);
  }, [reconnect, reset]);

  const checkForUpdates = useCallback(async (isManual: boolean = false) => {
    if (isManual) setCheckingUpdate(true);
    try {
      const result = await checkForUpdate(APP_VERSION_CODE);

      if (result.available && result.manifest) {
        const { version, apkUrl, releaseNotes, mandatory } = result.manifest;
        Alert.alert('Update Available', `Version ${version} is ready.\n\n${releaseNotes}`, [
          ...(!mandatory ? [{ text: 'Later', style: 'cancel' as const }] : []),
          { text: 'Download', onPress: () => Linking.openURL(apkUrl) },
        ]);
      } else if (isManual) {
        Alert.alert('Up to Date', `You are running the latest version (${APP_VERSION}).`);
      }
    } catch (_error) {
      if (isManual) {
        Alert.alert('Error', 'Failed to check for updates. Please check your internet connection.');
      }
    } finally {
      if (isManual) setCheckingUpdate(false);
    }
  }, []);

  useEffect(() => {
    // Silently check for updates on mount
    checkForUpdates(false);
  }, [checkForUpdates]);

  const handleCopyrightPress = () => {
    Linking.openURL('https://snowballons.com');
  };

  const handleExportData = async () => {
    try {
      const shared = await exportFavorites();
      if (!shared) {
        Alert.alert('No Share App', 'No app available to share the backup file with.');
      }
    } catch (error) {
      console.error('Export error:', error);
      Alert.alert('Error', 'Failed to export data.');
    }
  };

  const handleImportData = () => {
    Alert.alert('Import Data', 'This will add streams from a backup file to your library.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Import',
        onPress: async () => {
          try {
            const { imported, skipped } = await importFavorites();
            if (imported > 0) {
              Alert.alert(
                'Import Complete',
                `${imported} stream${imported === 1 ? '' : 's'} imported.${
                  skipped > 0 ? ` ${skipped} skipped (already in library).` : ''
                }`
              );
            } else {
              Alert.alert('Nothing Imported', 'No new streams were found in the backup file.');
            }
          } catch (error: any) {
            Alert.alert('Import Failed', error.message || 'Could not read the backup file.');
          }
        },
      },
    ]);
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Settings</Text>

      {profile && (
        <Section title="Account">
          <View style={styles.profileCard}>
            <View style={styles.avatarContainer}>
              <User color={Palette.text} size={24} />
            </View>
            <View style={styles.profileInfo}>
              <Text style={styles.username}>{profile.username}</Text>
            </View>
          </View>
        </Section>
      )}

      <Section title="Backend">
        <View style={styles.listItem}>
          <View style={styles.backendStatusContainer}>
            <Server color={Palette.textMuted} size={18} />
            <View style={styles.backendStatusText}>
              <Text style={styles.listItemLabel}>
                {configLoading || !config ? 'Loading...' : config.apiUrl}
              </Text>
              <Text
                style={[
                  styles.backendStatusDetail,
                  !isBackendReachable && styles.backendStatusDetailError,
                ]}
              >
                {!isBackendReachable
                  ? 'Backend unreachable'
                  : isCustom
                    ? 'Custom backend · connected'
                    : 'Default backend · connected'}
                {verificationDetail ? ` · ${verificationDetail}` : ''}
              </Text>
            </View>
          </View>
        </View>
        {isCustom && (
          <ListItem
            label="Use Default Server"
            onPress={handleResetBackend}
            icon={<RotateCcw color={Palette.textMuted} size={18} />}
          />
        )}
      </Section>
      <Section title="Supporter">
        <BackendSettings />
      </Section>

      {verifying && (
        <View style={styles.verifyingOverlay}>
          <ActivityIndicator size="large" color={Palette.primary} />
          <Text style={styles.verifyingText}>Connecting to backend...</Text>
        </View>
      )}

      <Section title="About">
        <ListItem label="About TukiWatch" onPress={() => setShowAbout(!showAbout)} />
        {showAbout && (
          <Text style={styles.aboutText}>
            Dive into a world of live entertainment! TukiWatch is your personal portal to endless
            streams, bringing all your favorite content directly to your screen. Experience
            seamless, high-quality viewing of live events, gaming, music, and more, all in one
            place.
          </Text>
        )}
      </Section>

      <Section title="Information">
        <TouchableOpacity
          onPress={() => checkForUpdates(true)}
          style={styles.listItem}
          disabled={checkingUpdate}
        >
          <Text style={styles.listItemLabel}>Check for Updates</Text>
          <View style={styles.listItemValueContainer}>
            {checkingUpdate ? (
              <ActivityIndicator size="small" color={Palette.textMuted} />
            ) : (
              <>
                <Text style={styles.listItemValue}>v{APP_VERSION}</Text>
                <ChevronRight color={Palette.textMuted} size={18} />
              </>
            )}
          </View>
        </TouchableOpacity>
        <ListItem label="© 2026 snowballons" onPress={handleCopyrightPress} />
      </Section>

      <Section title="Data">
        <ListItem label="Export Data" onPress={handleExportData} />
        <ListItem label="Import Data" onPress={handleImportData} />
      </Section>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
    paddingTop: 60,
    paddingHorizontal: Spacing.lg,
  },
  title: {
    color: Palette.text,
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: Spacing.xl,
  },
  section: {
    marginBottom: Spacing.xl,
  },
  sectionTitle: {
    color: Palette.textMuted,
    fontSize: 14,
    fontWeight: '600',
    textTransform: 'uppercase',
    marginBottom: Spacing.md,
  },
  sectionContent: {
    backgroundColor: Palette.card,
    borderRadius: 12,
    overflow: 'hidden',
  },
  profileCard: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: Spacing.md,
  },
  avatarContainer: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: Palette.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  profileInfo: {
    marginLeft: Spacing.md,
    flex: 1,
  },
  username: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: '600',
  },
  aboutText: {
    color: Palette.text,
    fontSize: 15,
    lineHeight: 22,
    padding: Spacing.md,
    borderTopWidth: 1,
    borderTopColor: Palette.border,
  },
  listItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  listItemLabel: {
    color: Palette.text,
    fontSize: 16,
  },
  listItemValueContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  listItemValue: {
    color: Palette.textMuted,
    fontSize: 16,
  },
  destructiveText: {
    color: '#EF4444',
  },
  listItemIcon: {
    marginRight: Spacing.md,
  },
  backendStatusContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.md,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
    flex: 1,
  },
  backendStatusText: {
    flex: 1,
  },
  backendStatusDetail: {
    color: Palette.live,
    fontSize: 13,
    marginTop: 2,
  },
  backendStatusDetailError: {
    color: '#EF4444',
  },
  verifyingOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 10,
  },
  verifyingText: {
    color: '#fff',
    marginTop: Spacing.md,
    fontSize: 16,
  },
});
