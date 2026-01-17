import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, Linking } from 'react-native';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';
import { ChevronRight } from 'lucide-react-native';

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

export function SettingsScreen() {
  const appVersion = "1.0.0"; // From package.json

  const handleCopyrightPress = () => {
    Linking.openURL('https://snowballons.com');
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Settings</Text>

      <Section title="About">
        <Text style={styles.aboutText}>
          Dive into a world of live entertainment! StreamWatch is your personal portal to endless streams, bringing all your favorite content directly to your screen. Experience seamless, high-quality viewing of live events, gaming, music, and more, all in one place.
        </Text>
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
  aboutText: {
    color: Palette.text,
    fontSize: 15,
    lineHeight: 22,
    padding: Spacing.md,
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
});