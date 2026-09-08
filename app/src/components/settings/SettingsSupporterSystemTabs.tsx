import Constants from 'expo-constants';
import { ChevronRight, Info, Loader, RefreshCw, RotateCcw, Shield } from 'lucide-react-native';
import { useCallback, useEffect, useMemo, useState } from 'react';
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
import {
  activateLicense,
  deactivateSession,
  LicenseApiError,
  validateSession,
} from '../../lib/licenseApi';
import { clearSessionToken, setSessionToken } from '../../lib/sessionToken';
import { checkForUpdate, selectApkUrl } from '../../services/updateService';
import { Spacing, type ThemeColors } from '../../theme/Theme';
import { useTheme } from '../../theme/ThemeContext';
import { Card, CardRow, SectionTitle, useSharedSettingsStyles } from './SharedSettingsComponents';

const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';
const APP_VERSION_CODE = Constants.expoConfig?.android?.versionCode ?? 0;

const SUPPORTER_URL = 'https://tukiwatch.snowballons.com/supporter';

type SupporterUiState = 'checking' | 'free' | 'activating' | 'supporter';

function formatSessionExpiry(iso: string): string | null {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString();
}

export function ConnectionTab() {
  const { config, isCustom, loading: configLoading, reset, reload } = useBackendConfig();
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const sharedSettingsStyles = useSharedSettingsStyles();
  const { isBackendReachable, reconnect } = useStreams();
  const [resetting, setResetting] = useState(false);
  const [editingServer, setEditingServer] = useState(false);
  const [tempServerUrl, setTempServerUrl] = useState('');
  const [supporterState, setSupporterState] = useState<SupporterUiState>('checking');
  const [licenseKeyInput, setLicenseKeyInput] = useState('');
  const [supporterDetail, setSupporterDetail] = useState<string | null>(null);
  const [supporterError, setSupporterError] = useState<string | null>(null);

  const refreshSupporterStatus = useCallback(async () => {
    setSupporterState('checking');
    setSupporterError(null);
    const status = await validateSession();
    if (status.valid) {
      setSupporterDetail(status.expiresAt ? formatSessionExpiry(status.expiresAt) : null);
      setSupporterState('supporter');
    } else {
      setSupporterDetail(null);
      setSupporterState('free');
    }
  }, []);

  useEffect(() => {
    refreshSupporterStatus();
  }, [refreshSupporterStatus]);

  const handleActivate = useCallback(async () => {
    const key = licenseKeyInput.trim();
    if (!key) {
      setSupporterError('Enter your license key.');
      return;
    }
    setSupporterState('activating');
    setSupporterError(null);
    try {
      const result = await activateLicense(key);
      await setSessionToken(result.sessionToken);
      setLicenseKeyInput('');
      await refreshSupporterStatus();
    } catch (error) {
      setSupporterState('free');
      setSupporterError(error instanceof LicenseApiError ? error.message : 'Activation failed.');
    }
  }, [licenseKeyInput, refreshSupporterStatus]);

  const handleSignOut = useCallback(async () => {
    await deactivateSession();
    await clearSessionToken();
    setSupporterDetail(null);
    setSupporterError(null);
    setSupporterState('free');
  }, []);

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
      // Sessions belong to one backend — drop the old one on switch.
      await clearSessionToken();
      setSupporterDetail(null);
      setSupporterError(null);
      setSupporterState('free');
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
            <ActivityIndicator size="small" color={colors.textMuted} />
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
              <ChevronRight color={colors.textMuted} size={18} />
            </TouchableOpacity>
          </>
        )}
      </Card>

      {/* Supporter access card */}
      <View style={sharedSettingsStyles.gapMd} />
      <Card style={styles.accessCard}>
        <View style={styles.accessHeader}>
          <View style={styles.accessBadge}>
            <Text style={styles.accessBadgeText}>STATUS</Text>
          </View>
          <Text style={styles.accessTitle}>Supporter Access</Text>
        </View>
        {supporterState === 'checking' ? (
          <View style={sharedSettingsStyles.loadingRow}>
            <ActivityIndicator size="small" color={colors.textMuted} />
            <Text style={sharedSettingsStyles.loadingText}>Checking…</Text>
          </View>
        ) : supporterState === 'supporter' ? (
          <>
            <Text style={styles.accessDesc}>
              Supporter active
              {supporterDetail ? ` · session valid until ${supporterDetail}` : ''}. You get higher
              rate limits on all requests.
            </Text>
            <TouchableOpacity
              style={styles.accessBtn}
              onPress={() => Linking.openURL(SUPPORTER_URL)}
            >
              <Text style={styles.accessBtnText}>Manage Subscription</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.signOutRow} onPress={handleSignOut} activeOpacity={0.7}>
              <Text style={styles.signOutText}>Sign Out</Text>
            </TouchableOpacity>
          </>
        ) : (
          <>
            <Text style={styles.accessDesc}>
              Get full access to all platforms and priority features by supporting TukiWatch. Paste
              your license key below.
            </Text>
            <TextInput
              style={sharedSettingsStyles.modalInput}
              value={licenseKeyInput}
              onChangeText={(text) => {
                setLicenseKeyInput(text);
                setSupporterError(null);
              }}
              placeholder="TUKI_…"
              placeholderTextColor={colors.textMuted}
              autoCorrect={false}
              autoCapitalize="none"
              editable={supporterState !== 'activating'}
            />
            {supporterError ? <Text style={styles.errorText}>{supporterError}</Text> : null}
            <TouchableOpacity
              style={styles.accessBtn}
              onPress={handleActivate}
              disabled={supporterState === 'activating'}
              activeOpacity={0.7}
            >
              {supporterState === 'activating' ? (
                <ActivityIndicator size="small" color={colors.onPrimary} />
              ) : (
                <Text style={styles.accessBtnText}>Activate</Text>
              )}
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.signOutRow}
              onPress={() => Linking.openURL(SUPPORTER_URL)}
              activeOpacity={0.7}
            >
              <Text style={styles.signOutText}>Get a license</Text>
            </TouchableOpacity>
          </>
        )}
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
            <RotateCcw color={colors.danger} size={18} />
            <Text style={sharedSettingsStyles.destructiveText}>Reset to Default Server</Text>
          </View>
          {resetting ? (
            <ActivityIndicator size="small" color={colors.danger} />
          ) : (
            <ChevronRight color={colors.danger} size={18} />
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
              placeholder="https://api.example.com"
              placeholderTextColor={colors.textMuted}
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
  const { colors, mode: themeMode, setMode: setThemeMode } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const sharedSettingsStyles = useSharedSettingsStyles();

  const checkForUpdates = useCallback(async (isManual: boolean) => {
    if (isManual) setCheckingUpdate(true);
    try {
      const result = await checkForUpdate(APP_VERSION_CODE);
      if (result.available && result.manifest) {
        const { version, releaseNotes, mandatory } = result.manifest;
        const apkUrl = selectApkUrl(result.manifest);
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
            <Info color={colors.primary} size={24} />
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
            <RefreshCw color={colors.textMuted} size={18} />
            <Text style={styles.listRowLabel}>Check for Updates</Text>
          </View>
          <View style={styles.listRowRight}>
            {checkingUpdate ? (
              <ActivityIndicator size="small" color={colors.textMuted} />
            ) : (
              <>
                <Text style={styles.listRowValue}>v{APP_VERSION}</Text>
                <ChevronRight color={colors.textMuted} size={18} />
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
            <Shield color={colors.textMuted} size={18} />
            <Text style={styles.listRowLabel}>Terms of Service</Text>
          </View>
          <ChevronRight color={colors.textMuted} size={18} />
        </TouchableOpacity>
        <View style={sharedSettingsStyles.divider} />
        <TouchableOpacity
          style={styles.listRow}
          onPress={() => Linking.openURL('https://tukiwatch.snowballons.com/privacy')}
          activeOpacity={0.7}
        >
          <View style={styles.listRowLeft}>
            <Shield color={colors.textMuted} size={18} />
            <Text style={styles.listRowLabel}>Privacy Policy</Text>
          </View>
          <ChevronRight color={colors.textMuted} size={18} />
        </TouchableOpacity>
      </Card>

      {/* Appearance */}
      <View style={sharedSettingsStyles.gapMd} />
      <SectionTitle>APPEARANCE</SectionTitle>
      <Card>
        <View style={styles.appearanceRow}>
          {(
            [
              { key: 'system', label: 'System' },
              { key: 'light', label: 'Light' },
              { key: 'dark', label: 'Dark' },
            ] as const
          ).map((option) => {
            const selected = themeMode === option.key;
            return (
              <TouchableOpacity
                key={option.key}
                style={[styles.appearanceOption, selected && styles.appearanceOptionSelected]}
                onPress={() => setThemeMode(option.key)}
                accessibilityRole="button"
                accessibilityState={{ selected }}
              >
                <Text style={[styles.appearanceLabel, selected && styles.appearanceLabelSelected]}>
                  {option.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
        <Text style={styles.appearanceHint}>System follows your device setting.</Text>
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
            <Loader color={colors.danger} size={18} />
            <Text style={sharedSettingsStyles.destructiveText}>Clear Cache</Text>
          </View>
          <ChevronRight color={colors.danger} size={18} />
        </TouchableOpacity>
      </Card>
    </View>
  );
}

const makeStyles = (colors: ThemeColors) =>
  StyleSheet.create({
    // Access card
    accessCard: {
      backgroundColor: colors.card,
      borderRadius: 12,
      marginHorizontal: Spacing.lg,
      padding: Spacing.md,
      borderWidth: 1,
      borderColor: colors.border,
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
      color: colors.primary,
      letterSpacing: 0.5,
    },
    accessTitle: {
      fontSize: 16,
      fontWeight: '700',
      color: colors.text,
    },
    accessDesc: {
      fontSize: 13,
      color: colors.textMuted,
      lineHeight: 20,
      marginBottom: Spacing.md,
    },
    accessBtn: {
      backgroundColor: colors.primary,
      borderRadius: 8,
      paddingVertical: Spacing.md,
      alignItems: 'center',
    },
    accessBtnText: {
      fontSize: 15,
      fontWeight: '600',
      color: colors.onPrimary,
    },
    errorText: {
      fontSize: 13,
      color: colors.danger,
      marginBottom: Spacing.sm,
    },
    signOutRow: {
      alignItems: 'center',
      paddingVertical: Spacing.sm,
      marginTop: Spacing.sm,
    },
    signOutText: {
      fontSize: 14,
      fontWeight: '600',
      color: colors.textMuted,
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
      color: colors.text,
    },
    aboutVersion: {
      fontSize: 13,
      color: colors.textMuted,
      marginTop: 2,
    },
    aboutDesc: {
      fontSize: 14,
      color: colors.textMuted,
      lineHeight: 22,
      paddingHorizontal: Spacing.md,
      paddingBottom: Spacing.md,
    },
    copyrightRow: {
      borderTopWidth: 1,
      borderTopColor: colors.border,
      paddingVertical: Spacing.md,
      paddingHorizontal: Spacing.md,
    },
    copyrightText: {
      fontSize: 13,
      color: colors.textMuted,
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
      color: colors.text,
    },
    listRowRight: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: Spacing.sm,
    },
    listRowValue: {
      fontSize: 14,
      color: colors.textMuted,
    },

    // Appearance selector
    appearanceRow: {
      flexDirection: 'row',
      gap: Spacing.sm,
      paddingHorizontal: Spacing.md,
      paddingTop: Spacing.md,
    },
    appearanceOption: {
      flex: 1,
      paddingVertical: Spacing.sm,
      borderRadius: 10,
      borderWidth: 1,
      borderColor: colors.border,
      backgroundColor: colors.background,
      alignItems: 'center',
    },
    appearanceOptionSelected: {
      backgroundColor: colors.primary,
      borderColor: colors.primary,
    },
    appearanceLabel: {
      fontSize: 14,
      fontWeight: '600',
      color: colors.textMuted,
    },
    appearanceLabelSelected: {
      color: colors.onPrimary,
    },
    appearanceHint: {
      fontSize: 12,
      color: colors.textMuted,
      paddingHorizontal: Spacing.md,
      paddingVertical: Spacing.sm,
    },
  });
