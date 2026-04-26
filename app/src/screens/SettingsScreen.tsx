import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Linking, Alert, ActivityIndicator } from 'react-native';
import { supabase } from '../../lib/supabase';
import { useProfile } from '../hooks/useProfile';
import { Palette, Spacing } from '../theme/Theme';
import { ChevronRight, User } from 'lucide-react-native';

const Section: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <View style={styles.section}>
    <Text style={styles.sectionTitle}>{title}</Text>
    <View style={styles.sectionContent}>
      {children}
    </View>
  </View>
);

const ListItem: React.FC<{ label: string; value?: string; onPress?: () => void, isDestructive?: boolean }> = ({ label, value, onPress, isDestructive }) => (
  <TouchableOpacity onPress={onPress} style={styles.listItem} disabled={!onPress}>
    <Text style={[styles.listItemLabel, isDestructive && styles.destructiveText]}>{label}</Text>
    <View style={styles.listItemValueContainer}>
      {value && <Text style={styles.listItemValue}>{value}</Text>}
      {onPress && !isDestructive && <ChevronRight color={Palette.textMuted} size={18} />}
    </View>
  </TouchableOpacity>
);

const appVersion = "1.0.0";

const isNewerVersion = (current: string, latest: string) => {
  const currentParts = current.replace('v', '').split('.').map(Number);
  const latestParts = latest.replace('v', '').split('.').map(Number);
  for (let i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
    const curr = currentParts[i] || 0;
    const lat = latestParts[i] || 0;
    if (lat > curr) return true;
    if (lat < curr) return false;
  }
  return false;
};

export function SettingsScreen({ navigation }: any) {
  const { profile } = useProfile();
  const [showAbout, setShowAbout] = useState(false);
  const [checkingUpdate, setCheckingUpdate] = useState(false);

  useEffect(() => {
    // Silently check for updates on mount
    checkForUpdates(false);
  }, []);

  const checkForUpdates = async (isManual: boolean = false) => {
    if (isManual) setCheckingUpdate(true);
    try {
      const response = await fetch('https://api.github.com/repos/snowballons/streamwatch-api/releases/latest');
      const data = await response.json();
      
      if (data.tag_name) {
        const latestVersion = data.tag_name.replace('v', '');
        
        if (isNewerVersion(appVersion, latestVersion)) {
          // Look for an APK file in the release assets, fallback to html release page
          const apkAsset = data.assets?.find((a: any) => a.name.endsWith('.apk'));
          const downloadUrl = apkAsset ? apkAsset.browser_download_url : data.html_url;
          
          Alert.alert(
            "Update Available",
            `Version ${latestVersion} of StreamWatch is ready. Would you like to download it now?`,
            [
              { text: "Later", style: "cancel" },
              { text: "Download", onPress: () => Linking.openURL(downloadUrl) }
            ]
          );
        } else if (isManual) {
          Alert.alert("Up to Date", `You are running the latest version (${appVersion}).`);
        }
      } else if (isManual) {
        Alert.alert("Notice", "Could not verify the latest version at this time.");
      }
    } catch (error) {
      if (isManual) {
        Alert.alert("Error", "Failed to check for updates. Please check your internet connection.");
      }
    } finally {
      if (isManual) setCheckingUpdate(false);
    }
  };

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
        <ListItem label="About StreamWatch" onPress={() => setShowAbout(!showAbout)} />
        {showAbout && (
          <Text style={styles.aboutText}>
            Dive into a world of live entertainment! StreamWatch is your personal portal to endless streams, bringing all your favorite content directly to your screen. Experience seamless, high-quality viewing of live events, gaming, music, and more, all in one place.
          </Text>
        )}
      </Section>

      <Section title="Information">
        <TouchableOpacity onPress={() => checkForUpdates(true)} style={styles.listItem} disabled={checkingUpdate}>
          <Text style={styles.listItemLabel}>Check for Updates</Text>
          <View style={styles.listItemValueContainer}>
            {checkingUpdate ? (
              <ActivityIndicator size="small" color={Palette.textMuted} />
            ) : (
              <>
                <Text style={styles.listItemValue}>v{appVersion}</Text>
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
    paddingHorizontal: Spacing.lg 
  },
  title: { 
    color: Palette.text, 
    fontSize: 32, 
    fontWeight: 'bold', 
    marginBottom: Spacing.xl 
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
