import { ChevronRight, User } from 'lucide-react-native';
import { useCallback, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { exportFavorites, importFavorites } from '../../../lib/db';
import { useStreams } from '../../context/StreamContext';
import { setUsername, useProfile } from '../../hooks/useProfile';
import { Palette, Spacing } from '../../theme/Theme';
import { Card, CardRow, SectionTitle, sharedSettingsStyles } from './SharedSettingsComponents';

export function ProfileTab() {
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
      <View style={sharedSettingsStyles.gapMd} />
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
        <View style={sharedSettingsStyles.divider} />
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
          style={sharedSettingsStyles.modalOverlay}
          activeOpacity={1}
          onPress={() => setEditing(false)}
        >
          <TouchableOpacity style={sharedSettingsStyles.modalCard} activeOpacity={1}>
            <Text style={sharedSettingsStyles.modalTitle}>Edit Name</Text>
            <TextInput
              style={sharedSettingsStyles.modalInput}
              value={tempName}
              onChangeText={setTempName}
              placeholder="Enter your name"
              placeholderTextColor={Palette.textMuted}
              autoFocus
              maxLength={30}
            />
            <View style={sharedSettingsStyles.modalActions}>
              <TouchableOpacity
                style={sharedSettingsStyles.modalBtnCancel}
                onPress={() => setEditing(false)}
              >
                <Text style={sharedSettingsStyles.modalBtnCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  sharedSettingsStyles.modalBtnSave,
                  saving && sharedSettingsStyles.modalBtnSaveDisabled,
                ]}
                onPress={saveName}
                disabled={saving}
              >
                {saving ? (
                  <ActivityIndicator size="small" color={Palette.text} />
                ) : (
                  <Text style={sharedSettingsStyles.modalBtnSaveText}>Save</Text>
                )}
              </TouchableOpacity>
            </View>
          </TouchableOpacity>
        </TouchableOpacity>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
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
});
