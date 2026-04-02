import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Linking, Alert } from 'react-native';
import { supabase } from '../../lib/supabase';
import { useProfile } from '../hooks/useProfile';
import { getCacheStats, getRateLimitInfo, RateLimitInfo } from '../services/engine';
import { useStreams } from '../context/StreamContext';
import { Palette, Spacing } from '../theme/Theme';
import { ChevronRight, User, Wifi, WifiOff, Database } from 'lucide-react-native';

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

export function SettingsScreen({ navigation }: any) {
  const appVersion = "1.0.0";
  const { profile } = useProfile();
  const { isBackendReachable } = useStreams();
  const [showAbout, setShowAbout] = useState(false);
  const [cacheStats, setCacheStats] = useState<any>(null);
  const [rateLimitInfo, setRateLimitInfo] = useState<RateLimitInfo | null>(null);
  const [showDebug, setShowDebug] = useState(false);

  useEffect(() => {
    const fetchStats = async () => {
      const stats = await getCacheStats();
      if (stats) setCacheStats(stats);
      setRateLimitInfo(getRateLimitInfo());
    };
    fetchStats();
  }, []);

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

      <Section title="Connection">
        <ListItem
          label="Backend Status"
          value={isBackendReachable ? 'Connected' : 'Disconnected'}
          onPress={() => setShowDebug(!showDebug)}
        />
        {showDebug && (
          <View style={styles.debugContainer}>
            <View style={styles.debugRow}>
              {isBackendReachable
                ? <Wifi color={Palette.live} size={16} />
                : <WifiOff color="#EF4444" size={16} />
              }
              <Text style={[styles.debugText, { color: isBackendReachable ? Palette.live : '#EF4444' }]}>
                {isBackendReachable ? 'API server is reachable' : 'Cannot reach API server'}
              </Text>
            </View>
            {cacheStats && (
              <>
                <View style={styles.debugRow}>
                  <Database color={Palette.textMuted} size={16} />
                  <Text style={styles.debugText}>
                    Cache: {cacheStats.cache?.type || 'Unknown'}
                    {cacheStats.cache?.connected_to ? ` (${cacheStats.cache.connected_to})` : ''}
                  </Text>
                </View>
                {cacheStats.cache?.keys !== undefined && (
                  <Text style={styles.debugSubtext}>
                    {cacheStats.cache.keys} cached keys
                    {cacheStats.cache?.used_memory_human ? ` · ${cacheStats.cache.used_memory_human} memory` : ''}
                  </Text>
                )}
              </>
            )}
            {rateLimitInfo && (
              <Text style={styles.debugSubtext}>
                Rate limit: {rateLimitInfo.remaining}/{rateLimitInfo.limit} remaining
              </Text>
            )}
          </View>
        )}
      </Section>

      <Section title="About">
        <ListItem label="About StreamWatch" onPress={() => setShowAbout(!showAbout)} />
        {showAbout && (
          <Text style={styles.aboutText}>
            Dive into a world of live entertainment! StreamWatch is your personal portal to endless streams, bringing all your favorite content directly to your screen. Experience seamless, high-quality viewing of live events, gaming, music, and more, all in one place.
          </Text>
        )}
      </Section>

      <Section title="Information">
        <ListItem label="Version" value={appVersion} />
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
  },
  listItemValue: {
    color: Palette.textMuted,
    fontSize: 16,
    marginRight: Spacing.sm,
  },
  destructiveText: {
    color: '#EF4444',
  },
  debugContainer: {
    padding: Spacing.md,
    borderTopWidth: 1,
    borderTopColor: Palette.border,
    gap: 8,
  },
  debugRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  debugText: {
    color: Palette.textMuted,
    fontSize: 13,
  },
  debugSubtext: {
    color: Palette.textMuted,
    fontSize: 12,
    marginLeft: 24,
    opacity: 0.7,
  },
});