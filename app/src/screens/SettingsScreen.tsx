import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';

export function SettingsScreen() {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Settings</Text>
      <TouchableOpacity style={styles.logoutBtn} onPress={() => supabase.auth.signOut()}>
        <Text style={styles.logoutText}>Sign Out</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Palette.background, paddingTop: 60, paddingHorizontal: Spacing.lg },
  title: { color: Palette.text, fontSize: 24, fontWeight: 'bold', marginBottom: Spacing.xl },
  logoutBtn: { backgroundColor: '#3F3F46', padding: 16, borderRadius: 12, alignItems: 'center' },
  logoutText: { color: '#EF4444', fontWeight: 'bold' },
});