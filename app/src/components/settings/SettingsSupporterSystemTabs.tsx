import Constants from 'expo-constants';
import { ChevronRight, Info, Loader, RefreshCw, RotateCcw, Shield } from 'lucide-react-native';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Modal,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { useStreams } from '../../context/StreamContext';
import { setBackendConfig, useBackendConfig } from '../../lib/backendConfig';
import { checkForUpdate } from '../../services/updateService';
import { Palette, Spacing } from '../../theme/Theme';
import { Card, CardRow, SectionTitle, sharedSettingsStyles } from './SharedSettingsComponents';

const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';
const APP_VERSION_CODE = Constants.expoConfig?.android?.versionCode ?? 0;

export function ConnectionTab() {
  const { config, isCustom, loading: configLoading, reset, reload } = useBackendConfig();
  const { isBackendReachable, reconnect } = useStreams();
  const [resetting, setResetting] = useState(false);
  const [editingServer, setEditingServer] = useState(false);
  const [tempServerUrl, setTempServerUrl] = useState('');

  const openEditServer = useCallback(() => {
    setTempServerUrl(config?.apiUrl ?? '');
    setEditingServer(true);
  }, [config]);

  const saveServer = useCallback(async () => {
    const trimmed = tempServerUrl.trim();
    if (!trimmed || trimmed === config?.apiUrl) {
      setEditingServer(false);
      return;
    }
    try {
      await setBackendConfig({ apiUrl: trimmed.replace(/\/+$/, '') });
      await reload();
    } catch {
      Alert.alert('Error', 'Failed to update server configuration.');
    } finally {
      setEditingServer(false);
    }
  }, [tempServerUrl, config, reload]);

  const handleReset = useCallback(async () => {
    Alert.alert('Reset Server', 'Reset to the default TukiWatch backend?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Reset',
        style: 'destructive',
        onPress: async () => {
          setResetting(true);
          try {
            await reset();
            await reconnect();
          } finally {
            setResetting(false);
          }
        },
      },
    ]);
  }, [reset, reconnect]);

  return (
    <View style={sharedSettingsStyles.tabContent}>
      {/* Connection status */}
      <Card>
        <Text style={sharedSettingsStyles.cardSectionLabel}>CONNECTION</Text>
        {configLoading ? (
          <View style={sharedSettingsStyles.loadingRow}>
            <ActivityIndicator size="small" color={Palette.textMuted} />
            <Text style={sharedSettingsStyles.loadingText}>Loading…</Text>
          </View>
        ) : (
          <>
            <View style={sharedSettingsStyles.statusRow}>
              <View
                style={[
                  sharedSettingsStyles.statusDot,
                  isBackendReachable
                    ? sharedSettingsStyles.statusDotOk
                    : sharedSettingsStyles.statusDotErr,
                ]}
              />
              <View style={sharedSettingsStyles.statusTexts}>
                <Text style={sharedSettingsStyles.statusTextPrimary}>
                  {isBackendReachable ? 'Connected' : 'Unreachable'}
                </Text>
                <Text style={sharedSettingsStyles.statusTextSub}>
                  {isCustom ? 'Custom server' : 'Default server'}
                </Text>
              </View>
            </View>
            <View style={sharedSettingsStyles.divider} />
            <CardRow label="Server URL" value={config?.apiUrl ?? ''} />
            <View style={sharedSettingsStyles.divider} />
            <TouchableOpacity style={sharedSettingsStyles.editServerRow} onPress={openEditServer}>
              <Text style={sharedSettingsStyles.editServerText}>Change Server</Text>
              <ChevronRight color={Palette.textMuted} size={18} />
            </TouchableOpacity>
          </>
        )}
      </Card>

      {/* Access card (Seamless CTA) */}
      <View style={sharedSettingsStyles.gapMd} />
      <Card style={styles.accessCard}>
        <View style={styles.accessHeader}>
          <View style={styles.accessBadge}>
            <Text style={styles.accessBadgeText}>STATUS</Text>
          </View>
          <Text style={styles.accessTitle}>Supporter Access</Text>
        </View>
        <Text style={styles.accessDesc}>
          Get full access to all platforms and priority features by supporting TukiWatch.
        </Text>
        <TouchableOpacity
          style={styles.accessBtn}
          onPress={() => Linking.openURL('https://tukiwatch.snowballons.com/supporter')}
        >
          <Text style={styles.accessBtnText}>Manage Access</Text>
        </TouchableOpacity>
      </Card>

      {/* Reset */}
      <View style={sharedSettingsStyles.gapMd} />
      <Card>
        <TouchableOpacity
          style={sharedSettingsStyles.destructiveRow}
          onPress={handleReset}
          disabled={resetting}
          activeOpacity={0.7}
        >
          <View style={sharedSettingsStyles.destructiveRowLeft}>
            <RotateCcw color="#EF4444" size={18} />
            <Text style={sharedSettingsStyles.destructiveText}>Reset to Default Server</Text>
          </View>
          {resetting ? (
            <ActivityIndicator size="small" color="#EF4444" />
          ) : (
            <ChevronRight color="#EF4444" size={18} />
          )}
        </TouchableOpacity>
      </Card>

      {/* Server edit modal */}
      <Modal
        transparent
        animationType="fade"
        visible={editingServer}
        onRequestClose={() => setEditingServer(false)}
      >
        <TouchableOpacity
          style={sharedSettingsStyles.modalOverlay}
          activeOpacity={1}
          onPress={() => setEditingServer(false)}
        >
          <TouchableOpacity style={sharedSettingsStyles.modalCard} activeOpacity={1}>
            <Text style={sharedSettingsStyles.modalTitle}>Change Server URL</Text>
            <TextInput
              style={sharedSettingsStyles.modalInput}
              value={tempServerUrl}
              onChangeText={setTempServerUrl}
              placeholder="https://api.tukiwatch.com"
              placeholderTextColor={Palette.textMuted}
              autoCorrect={false}
              autoCapitalize="none"
              keyboardType="url"
            />
            <View style={sharedSettingsStyles.modalActions}>
              <TouchableOpacity
                style={sharedSettingsStyles.modalBtnCancel}
                onPress={() => setEditingServer(false)}
              >
                <Text style={sharedSettingsStyles.modalBtnCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={sharedSettingsStyles.modalBtnSave} onPress={saveServer}>
                <Text style={sharedSettingsStyles.modalBtnSaveText}>Save</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </View>
  );
}

export function SystemTab() {
  const [checkingUpdate, setCheckingUpdate] = useState(false);

  const checkForUpdates = useCallback(async (isManual: boolean) => {
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
    } catch {
      if (isManual) Alert.alert('Error', 'Failed to check for updates.');
    } finally {
      if (isManual) setCheckingUpdate(false);
    }
  }, []);

  useEffect(() => {
    checkForUpdates(false);
  }, [checkForUpdates]);

  return (
    <View style={sharedSettingsStyles.tabContent}>
      {/* About card */}
      <Card>
        <View style={styles.aboutHeader}>
          <View style={styles.aboutIconWrap}>
            <Info color={Palette.primary} size={24} />
          </View>
          <View style={styles.aboutInfo}>
            <Text style={styles.aboutTitle}>TukiWatch</Text>
            <Text style={styles.aboutVersion}>v{APP_VERSION}</Text>
          </View>
        </View>
        <Text style={styles.aboutDesc}>
          Your personal portal to live streams. Gaming, music, events — all in one place.
        </Text>
        <TouchableOpacity
          style={styles.copyrightRow}
          onPress={() => Linking.openURL('https://snowballons.com')}
        >
          <Text style={styles.copyrightText}>© 2026 snowballons</Text>
        </TouchableOpacity>
      </Card>

      {/* App */}
      <View style={sharedSettingsStyles.gapMd} />
      <SectionTitle>APP</SectionTitle>
      <Card>
        <TouchableOpacity
          style={styles.listRow}
          onPress={() => checkForUpdates(true)}
          disabled={checkingUpdate}
          activeOpacity={0.7}
        >
          <View style={styles.listRowLeft}>
            <RefreshCw color={Palette.textMuted} size={18} />
            <Text style={styles.listRowLabel}>Check for Updates</Text>
          </View>
          <View style={styles.listRowRight}>
            {checkingUpdate ? (
              <ActivityIndicator size="small" color={Palette.textMuted} />
            ) : (
              <>
                <Text style={styles.listRowValue}>v{APP_VERSION}</Text>
                <ChevronRight color={Palette.textMuted} size={18} />
              </>
            )}
          </View>
        </TouchableOpacity>
        <View style={sharedSettingsStyles.divider} />
        <TouchableOpacity
          style={styles.listRow}
          onPress={() => Linking.openURL('https://tukiwatch.snowballons.com/terms')}
          activeOpacity={0.7}
        >
          <View style={styles.listRowLeft}>
            <Shield color={Palette.textMuted} size={18} />
            <Text style={styles.listRowLabel}>Terms of Service</Text>
          </View>
          <ChevronRight color={Palette.textMuted} size={18} />
        </TouchableOpacity>
        <View style={sharedSettingsStyles.divider} />
        <TouchableOpacity
          style={styles.listRow}
          onPress={() => Linking.openURL('https://tukiwatch.snowballons.com/privacy')}
          activeOpacity={0.7}
        >
          <View style={styles.listRowLeft}>
            <Shield color={Palette.textMuted} size={18} />
            <Text style={styles.listRowLabel}>Privacy Policy</Text>
          </View>
          <ChevronRight color={Palette.textMuted} size={18} />
        </TouchableOpacity>
      </Card>

      {/* Developer */}
      <View style={sharedSettingsStyles.gapMd} />
      <SectionTitle>DEVELOPER</SectionTitle>
      <Card>
        <TouchableOpacity
          style={sharedSettingsStyles.destructiveRow}
          onPress={() =>
            Alert.alert(
              'Clear Cache',
              'Cache clearing is not yet available. It will be added in a future update.'
            )
          }
          activeOpacity={0.7}
        >
          <View style={sharedSettingsStyles.destructiveRowLeft}>
            <Loader color="#EF4444" size={18} />
            <Text style={sharedSettingsStyles.destructiveText}>Clear Cache</Text>
          </View>
          <ChevronRight color="#EF4444" size={18} />
        </TouchableOpacity>
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  // Access card
  accessCard: {
    backgroundColor: Palette.card,
    borderRadius: 12,
    marginHorizontal: Spacing.lg,
    padding: Spacing.md,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  accessHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    marginBottom: Spacing.sm,
  },
  accessBadge: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: 3,
    borderRadius: 6,
    backgroundColor: 'rgba(43, 53, 255, 0.15)',
  },
  accessBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: Palette.primary,
    letterSpacing: 0.5,
  },
  accessTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Palette.text,
  },
  accessDesc: {
    fontSize: 13,
    color: Palette.textMuted,
    lineHeight: 20,
    marginBottom: Spacing.md,
  },
  accessBtn: {
    backgroundColor: Palette.primary,
    borderRadius: 8,
    paddingVertical: Spacing.md,
    alignItems: 'center',
  },
  accessBtnText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#fff',
  },

  // About card
  aboutHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.md,
    paddingBottom: Spacing.md,
  },
  aboutIconWrap: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: 'rgba(43, 53, 255, 0.15)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  aboutInfo: {
    flex: 1,
  },
  aboutTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: Palette.text,
  },
  aboutVersion: {
    fontSize: 13,
    color: Palette.textMuted,
    marginTop: 2,
  },
  aboutDesc: {
    fontSize: 14,
    color: Palette.textMuted,
    lineHeight: 22,
    paddingHorizontal: Spacing.md,
    paddingBottom: Spacing.md,
  },
  copyrightRow: {
    borderTopWidth: 1,
    borderTopColor: Palette.border,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  copyrightText: {
    fontSize: 13,
    color: Palette.textMuted,
  },

  // List row
  listRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  listRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    flex: 1,
  },
  listRowLabel: {
    fontSize: 15,
    color: Palette.text,
  },
  listRowRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  listRowValue: {
    fontSize: 14,
    color: Palette.textMuted,
  },
});
