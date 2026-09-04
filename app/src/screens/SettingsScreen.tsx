import Constants from 'expo-constants';
import {
  ChevronRight,
  Info,
  Loader,
  RefreshCw,
  RotateCcw,
  Shield,
  User,
} from 'lucide-react-native';
import type React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { exportFavorites, importFavorites } from '../../lib/db';
import { useStreams } from '../../src/context/StreamContext';
import { setUsername, useProfile } from '../../src/hooks/useProfile';
import { setBackendConfig, useBackendConfig } from '../../src/lib/backendConfig';
import { checkForUpdate } from '../../src/services/updateService';
import { Palette, Spacing } from '../../src/theme/Theme';

const TABS = ['Profile', 'Supporter', 'System'] as const;
type Tab = (typeof TABS)[number];

const APP_VERSION = Constants.expoConfig?.version ?? '1.0.0';
const APP_VERSION_CODE = Constants.expoConfig?.android?.versionCode ?? 0;

// ─── Shared sub-components ──────────────────────────────────────────────────

const SectionTitle: React.FC<{ children: string }> = ({ children }) => (
  <Text style={styles.sectionTitle}>{children}</Text>
);

const Card: React.FC<{ children: React.ReactNode; style?: object }> = ({ children, style }) => (
  <View style={[styles.card, style]}>{children}</View>
);

const CardRow: React.FC<{
  label: string;
  value: string;
  onPress?: () => void;
  right?: React.ReactNode;
}> = ({ label, value, onPress, right }) => (
  <TouchableOpacity style={styles.cardRow} onPress={onPress} activeOpacity={onPress ? 0.7 : 1}>
    <View style={styles.cardRowLeft}>
      <Text style={styles.cardRowLabel}>{label}</Text>
      <Text style={styles.cardRowValue}>{value}</Text>
    </View>
    {right ?? <ChevronRight color={Palette.textMuted} size={18} />}
  </TouchableOpacity>
);

// ─── Profile Tab ────────────────────────────────────────────────────────────

function ProfileTab() {
  const { profile, refetch } = useProfile();
  const { streams } = useStreams();
  const [editing, setEditing] = useState(false);
  const [tempName, setTempName] = useState('');
  const [saving, setSaving] = useState(false);

  const openEdit = useCallback(() => {
    setTempName(profile?.username ?? '');
    setEditing(true);
  }, [profile]);

  const saveName = useCallback(async () => {
    const trimmed = tempName.trim();
    if (!trimmed || trimmed === profile?.username) {
      setEditing(false);
      return;
    }
    setSaving(true);
    try {
      await setUsername(trimmed);
      await refetch();
    } catch {
      Alert.alert('Error', 'Failed to save name.');
    } finally {
      setSaving(false);
      setEditing(false);
    }
  }, [tempName, profile, refetch]);

  const stats = useMemo(() => {
    const live = streams.filter((s) => s.status === 'online');
    const platforms = new Set(live.map((s) => s.platform).filter(Boolean)).size;
    return { total: streams.length, platforms, live: live.length };
  }, [streams]);

  return (
    <>
      {/* Avatar + name */}
      <View style={styles.avatarSection}>
        <TouchableOpacity onPress={openEdit} activeOpacity={0.8}>
          <View style={styles.avatarRing}>
            <View style={styles.avatarCircle}>
              <User color={Palette.text} size={28} />
            </View>
          </View>
        </TouchableOpacity>
        <TouchableOpacity onPress={openEdit} activeOpacity={0.8}>
          <View style={styles.nameRow}>
            <Text style={styles.profileName} numberOfLines={1}>
              {profile?.username ?? 'Local User'}
            </Text>
            <ChevronRight color={Palette.textMuted} size={16} />
          </View>
        </TouchableOpacity>
      </View>

      {/* Stats */}
      <Card style={styles.statsCard}>
        <View style={styles.statsRow}>
          {[
            { label: 'Tracked', value: String(stats.total) },
            { label: 'Platforms', value: String(stats.platforms) },
            { label: 'Live Now', value: String(stats.live) },
          ].map((s) => (
            <View key={s.label} style={styles.statCell}>
              <Text style={styles.statValue}>{s.value}</Text>
              <Text style={styles.statLabel}>{s.label}</Text>
            </View>
          ))}
        </View>
      </Card>

      {/* Data */}
      <View style={styles.gapMd} />
      <SectionTitle>DATA</SectionTitle>
      <Card>
        <CardRow
          label="Export Library"
          value=""
          onPress={async () => {
            try {
              const shared = await exportFavorites();
              if (!shared)
                Alert.alert('No Share App', 'No app available to share the backup file.');
            } catch {
              Alert.alert('Error', 'Failed to export data.');
            }
          }}
        />
        <View style={styles.divider} />
        <CardRow
          label="Import Library"
          value=""
          onPress={() =>
            Alert.alert(
              'Import Data',
              'This will add streams from a backup file to your library.',
              [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Import',
                  onPress: async () => {
                    try {
                      const { imported, skipped } = await importFavorites();
                      if (imported > 0) {
                        Alert.alert(
                          'Import Complete',
                          `${imported} stream${imported === 1 ? '' : 's'} imported.${skipped > 0 ? ` ${skipped} skipped.` : ''}`
                        );
                      } else {
                        Alert.alert('Nothing Imported', 'No new streams found in the backup file.');
                      }
                    } catch (err: unknown) {
                      const msg =
                        err instanceof Error ? err.message : 'Could not read the backup file.';
                      Alert.alert('Import Failed', msg);
                    }
                  },
                },
              ]
            )
          }
        />
      </Card>

      {/* Edit name modal */}
      <Modal
        transparent
        animationType="fade"
        visible={editing}
        onRequestClose={() => setEditing(false)}
      >
        <TouchableOpacity
          style={styles.modalOverlay}
          activeOpacity={1}
          onPress={() => setEditing(false)}
        >
          <TouchableOpacity style={styles.modalCard} activeOpacity={1}>
            <Text style={styles.modalTitle}>Edit Name</Text>
            <TextInput
              style={styles.modalInput}
              value={tempName}
              onChangeText={setTempName}
              placeholder="Enter your name"
              placeholderTextColor={Palette.textMuted}
              autoFocus
              maxLength={30}
            />
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalBtnCancel} onPress={() => setEditing(false)}>
                <Text style={styles.modalBtnCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalBtnSave, saving && styles.modalBtnSaveDisabled]}
                onPress={saveName}
                disabled={saving}
              >
                {saving ? (
                  <ActivityIndicator size="small" color={Palette.text} />
                ) : (
                  <Text style={styles.modalBtnSaveText}>Save</Text>
                )}
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </>
  );
}

// ─── Supporter Tab ──────────────────────────────────────────────────────────

function SupporterTab() {
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
    <>
      {/* Connection status */}
      <Card>
        <Text style={styles.cardSectionLabel}>CONNECTION</Text>
        {configLoading ? (
          <View style={styles.loadingRow}>
            <ActivityIndicator size="small" color={Palette.textMuted} />
            <Text style={styles.loadingText}>Loading…</Text>
          </View>
        ) : (
          <>
            <View style={styles.statusRow}>
              <View
                style={[
                  styles.statusDot,
                  isBackendReachable ? styles.statusDotOk : styles.statusDotErr,
                ]}
              />
              <View style={styles.statusTexts}>
                <Text style={styles.statusTextPrimary}>
                  {isBackendReachable ? 'Connected' : 'Unreachable'}
                </Text>
                <Text style={styles.statusTextSub}>
                  {isCustom ? 'Custom server' : 'Default server'}
                </Text>
              </View>
            </View>
            <View style={styles.divider} />
            <CardRow label="Server URL" value={config?.apiUrl ?? ''} />
            <View style={styles.divider} />
            <TouchableOpacity style={styles.editServerRow} onPress={openEditServer}>
              <Text style={styles.editServerText}>Change Server</Text>
              <ChevronRight color={Palette.textMuted} size={18} />
            </TouchableOpacity>
          </>
        )}
      </Card>

      {/* Supporter CTA */}
      <View style={styles.gapMd} />
      <Card style={styles.supporterCard}>
        <View style={styles.supporterHeader}>
          <View style={styles.supporterBadge}>
            <Text style={styles.supporterBadgeText}>FREE</Text>
          </View>
          <Text style={styles.supporterTitle}>Supporter Access</Text>
        </View>
        <Text style={styles.supporterDesc}>
          Supporter tokens and session management are handled through Lemon Squeezy licensing.
        </Text>
        <TouchableOpacity
          style={styles.supporterBtn}
          onPress={() => Linking.openURL('https://tukiwatch.snowballons.com/pricing')}
        >
          <Text style={styles.supporterBtnText}>Get Supporter Access</Text>
        </TouchableOpacity>
      </Card>

      {/* Reset */}
      <View style={styles.gapMd} />
      <Card>
        <TouchableOpacity
          style={styles.destructiveRow}
          onPress={handleReset}
          disabled={resetting}
          activeOpacity={0.7}
        >
          <View style={styles.destructiveRowLeft}>
            <RotateCcw color="#EF4444" size={18} />
            <Text style={styles.destructiveText}>Reset to Default Server</Text>
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
          style={styles.modalOverlay}
          activeOpacity={1}
          onPress={() => setEditingServer(false)}
        >
          <TouchableOpacity style={styles.modalCard} activeOpacity={1}>
            <Text style={styles.modalTitle}>Change Server URL</Text>
            <TextInput
              style={styles.modalInput}
              value={tempServerUrl}
              onChangeText={setTempServerUrl}
              placeholder="https://api.tukiwatch.com"
              placeholderTextColor={Palette.textMuted}
              autoCorrect={false}
              autoCapitalize="none"
              keyboardType="url"
            />
            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.modalBtnCancel}
                onPress={() => setEditingServer(false)}
              >
                <Text style={styles.modalBtnCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalBtnSave} onPress={saveServer}>
                <Text style={styles.modalBtnSaveText}>Save</Text>
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </>
  );
}

// ─── System Tab ─────────────────────────────────────────────────────────────

function SystemTab() {
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
    <>
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
      <View style={styles.gapMd} />
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
        <View style={styles.divider} />
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
        <View style={styles.divider} />
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
      <View style={styles.gapMd} />
      <SectionTitle>DEVELOPER</SectionTitle>
      <Card>
        <TouchableOpacity
          style={styles.destructiveRow}
          onPress={() =>
            Alert.alert(
              'Clear Cache',
              'Cache clearing is not yet available. It will be added in a future update.'
            )
          }
          activeOpacity={0.7}
        >
          <View style={styles.destructiveRowLeft}>
            <Loader color="#EF4444" size={18} />
            <Text style={styles.destructiveText}>Clear Cache</Text>
          </View>
          <ChevronRight color="#EF4444" size={18} />
        </TouchableOpacity>
      </Card>
    </>
  );
}

// ─── Main ───────────────────────────────────────────────────────────────────

export function SettingsScreen() {
  const [activeTab, setActiveTab] = useState<Tab>('Profile');

  return (
    <View style={styles.container}>
      <Text style={styles.headerTitle}>Settings</Text>
      <View style={styles.tabBar}>
        {TABS.map((tab) => {
          const active = activeTab === tab;
          return (
            <TouchableOpacity
              key={tab}
              style={styles.tab}
              onPress={() => setActiveTab(tab)}
              activeOpacity={0.8}
            >
              <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{tab}</Text>
              {active && <View style={styles.tabIndicator} />}
            </TouchableOpacity>
          );
        })}
      </View>
      <ScrollView style={styles.scroll} showsVerticalScrollIndicator={false}>
        {activeTab === 'Profile' && <ProfileTab />}
        {activeTab === 'Supporter' && <SupporterTab />}
        {activeTab === 'System' && <SystemTab />}
        <View style={styles.bottomSpacer} />
      </ScrollView>
    </View>
  );
}

// ─── Styles ─────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
  headerTitle: {
    color: Palette.text,
    fontSize: 28,
    fontWeight: 'bold',
    paddingHorizontal: Spacing.lg,
    paddingTop: 60,
    paddingBottom: Spacing.md,
  },
  tabBar: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
    paddingHorizontal: Spacing.lg,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: Spacing.md,
    position: 'relative',
  },
  tabLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: Palette.textMuted,
  },
  tabLabelActive: {
    color: Palette.primary,
  },
  tabIndicator: {
    position: 'absolute',
    bottom: -1,
    left: 16,
    right: 16,
    height: 2,
    backgroundColor: Palette.primary,
    borderRadius: 1,
  },
  scroll: {
    flex: 1,
  },
  gapMd: {
    marginTop: Spacing.lg,
  },

  // Card
  card: {
    backgroundColor: Palette.card,
    borderRadius: 12,
    marginHorizontal: Spacing.lg,
    overflow: 'hidden',
  },
  cardSectionLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: Palette.textMuted,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.8,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.md,
    paddingBottom: Spacing.sm,
  },
  cardRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  cardRowLeft: {
    flex: 1,
    marginRight: Spacing.md,
  },
  cardRowLabel: {
    fontSize: 12,
    color: Palette.textMuted,
    marginBottom: 2,
  },
  cardRowValue: {
    fontSize: 15,
    color: Palette.text,
    fontFamily: 'monospace',
  },
  editServerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  editServerText: {
    fontSize: 15,
    color: Palette.primary,
    fontWeight: '600',
  },
  loadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  loadingText: {
    fontSize: 14,
    color: Palette.textMuted,
  },

  // Status
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: Spacing.sm,
  },
  statusDotOk: {
    backgroundColor: Palette.live,
  },
  statusDotErr: {
    backgroundColor: '#EF4444',
  },
  statusTexts: {
    flex: 1,
  },
  statusTextPrimary: {
    fontSize: 15,
    color: Palette.text,
    fontWeight: '600',
  },
  statusTextSub: {
    fontSize: 13,
    color: Palette.textMuted,
    marginTop: 2,
  },

  // Supporter
  supporterCard: {
    backgroundColor: Palette.card,
    borderRadius: 12,
    marginHorizontal: Spacing.lg,
    padding: Spacing.md,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  supporterHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    marginBottom: Spacing.sm,
  },
  supporterBadge: {
    paddingHorizontal: Spacing.sm,
    paddingVertical: 3,
    borderRadius: 6,
    backgroundColor: 'rgba(43, 53, 255, 0.15)',
  },
  supporterBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: Palette.primary,
    letterSpacing: 0.5,
  },
  supporterTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: Palette.text,
  },
  supporterDesc: {
    fontSize: 13,
    color: Palette.textMuted,
    lineHeight: 20,
    marginBottom: Spacing.md,
  },
  supporterBtn: {
    backgroundColor: Palette.primary,
    borderRadius: 8,
    paddingVertical: Spacing.md,
    alignItems: 'center',
  },
  supporterBtnText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#fff',
  },

  // Destructive row
  destructiveRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  destructiveRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  destructiveText: {
    fontSize: 15,
    color: '#EF4444',
    fontWeight: '500',
  },

  // Divider
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: Palette.border,
    marginLeft: Spacing.md,
    marginRight: Spacing.md,
  },

  // Section title
  sectionTitle: {
    fontSize: 11,
    fontWeight: '700',
    color: Palette.textMuted,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.8,
    paddingHorizontal: Spacing.lg,
    marginBottom: Spacing.sm,
    marginTop: Spacing.lg,
  },

  // Avatar section
  avatarSection: {
    alignItems: 'center',
    paddingVertical: Spacing.xl,
  },
  avatarRing: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: Palette.primary,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: Spacing.md,
  },
  avatarCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: 'rgba(255,255,255,0.1)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  profileName: {
    fontSize: 20,
    fontWeight: '700',
    color: Palette.text,
  },

  // Stats
  statsCard: {
    marginHorizontal: Spacing.lg,
    paddingTop: Spacing.md,
    paddingBottom: Spacing.md,
  },
  statsRow: {
    flexDirection: 'row',
  },
  statCell: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    fontSize: 22,
    fontWeight: '700',
    color: Palette.text,
  },
  statLabel: {
    fontSize: 12,
    color: Palette.textMuted,
    marginTop: 2,
  },

  // About
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

  // Modal
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.xl,
  },
  modalCard: {
    backgroundColor: Palette.card,
    borderRadius: 16,
    padding: Spacing.lg,
    width: '100%',
    maxWidth: 320,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Palette.text,
    marginBottom: Spacing.md,
    textAlign: 'center',
  },
  modalInput: {
    backgroundColor: Palette.background,
    borderRadius: 10,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    fontSize: 16,
    color: Palette.text,
    borderWidth: 1,
    borderColor: Palette.border,
    marginBottom: Spacing.md,
  },
  modalActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  modalBtnCancel: {
    flex: 1,
    paddingVertical: Spacing.md,
    borderRadius: 10,
    backgroundColor: Palette.secondary,
    alignItems: 'center',
  },
  modalBtnCancelText: {
    fontSize: 15,
    color: Palette.textMuted,
    fontWeight: '600',
  },
  modalBtnSave: {
    flex: 1,
    paddingVertical: Spacing.md,
    borderRadius: 10,
    backgroundColor: Palette.primary,
    alignItems: 'center',
  },
  modalBtnSaveDisabled: {
    opacity: 0.5,
  },
  modalBtnSaveText: {
    fontSize: 15,
    color: '#fff',
    fontWeight: '600',
  },

  bottomSpacer: {
    height: 40,
  },
});
