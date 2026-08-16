import { ChevronRight, User } from 'lucide-react-native';
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
import { supabase } from '../../lib/supabase';
import { useProfile } from '../hooks/useProfile';
import { checkForUpdate } from '../services/updateService';
import { Palette, Spacing } from '../theme/Theme';

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
}> = ({ label, value, onPress, isDestructive }) => (
  <TouchableOpacity onPress={onPress} style={styles.listItem} disabled={!onPress}>
    <Text style={[styles.listItemLabel, isDestructive && styles.destructiveText]}>{label}</Text>
    <View style={styles.listItemValueContainer}>
      {value && <Text style={styles.listItemValue}>{value}</Text>}
      {onPress && !isDestructive && <ChevronRight color={Palette.textMuted} size={18} />}
    </View>
  </TouchableOpacity>
);

// Matches versionCode in app.json android.versionCode (or use expo-constants in a managed build)
const APP_VERSION = '1.0.1';
const APP_VERSION_CODE = 1;

export function SettingsScreen() {
  const { profile } = useProfile();
  const [showAbout, setShowAbout] = useState(false);
  const [checkingUpdate, setCheckingUpdate] = useState(false);

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

      <Section title="Danger Zone">
        <ListItem label="Sign Out" onPress={() => supabase.auth.signOut()} isDestructive />
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
});
