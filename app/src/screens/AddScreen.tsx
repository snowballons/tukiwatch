import React, { useState } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ActivityIndicator, Alert, Keyboard, ScrollView } from 'react-native';
import { supabase } from '../../lib/supabase';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { StreamCard } from '../components/StreamCard';
import { Palette, Spacing } from '../theme/Theme';
import { Link, Plus, X } from 'lucide-react-native'; // Added X icon

export function AddScreen() {
  const [url, setUrl] = useState('');
  const { resolve, resolving } = useStreamResolver();
  const [previewData, setPreviewData] = useState<any>(null);

  // New: Function to reset everything
  const handleClear = () => {
    setUrl('');
    setPreviewData(null);
  };

  const handlePreview = async () => {
    if (!url) return;
    setPreviewData(null); // Clear previous preview
    Keyboard.dismiss();

    const data = await resolve(url);
    if (data && data.status === 'online') {
      setPreviewData(data);
    } else if (data) {
      Alert.alert("Offline", "Stream is currently offline.");
    } else {
      Alert.alert("Error", "Could not resolve link.");
    }
  };

  const handleSave = async () => {
    const { data: { user } } = await supabase.auth.getUser();
    if (!user || !previewData) return;

    const { error } = await supabase.from('favorites').insert([
      {
        user_id: user.id,
        streamer_name: previewData.author || "Unknown",
        original_url: url,
      },
    ]);

    if (!error) {
      Alert.alert("Saved", "Added to your library.");
      handleClear(); // Reset after successful save
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={{ paddingTop: 80, paddingHorizontal: Spacing.lg }}>
      <Text style={styles.title}>Add New Stream</Text>
      <Text style={styles.subtitle}>Instant awareness. Add a link to track it.</Text>

      <View style={styles.inputWrapper}>
        <Link color={Palette.textMuted} size={20} />
        <TextInput
          style={styles.input}
          placeholder="https://..."
          placeholderTextColor={Palette.textMuted}
          value={url}
          onChangeText={setUrl}
          autoCapitalize="none"
        />
        {/* CLEAR BUTTON (X) */}
        {url.length > 0 && (
          <TouchableOpacity onPress={handleClear} style={styles.clearIcon}>
            <X color={Palette.textMuted} size={18} />
          </TouchableOpacity>
        )}
      </View>

      <TouchableOpacity
        style={[styles.primaryButton, { opacity: url ? 1 : 0.5 }]}
        onPress={handlePreview}
        disabled={resolving || !url}
      >
        {resolving ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Verify Link</Text>}
      </TouchableOpacity>

      {/* PREVIEW SECTION */}
      {previewData && (
        <View style={styles.previewSection}>
          <View style={styles.previewHeader}>
            <Text style={styles.previewLabel}>Preview</Text>
            <TouchableOpacity onPress={() => setPreviewData(null)}>
              <Text style={styles.cancelLink}>Cancel</Text>
            </TouchableOpacity>
          </View>

          <StreamCard
            title={previewData.title}
            streamer={previewData.author}
            thumbnail={previewData.thumbnail}
            isLive={true}
            url={url}
            onPress={() => { }}
          />

          <TouchableOpacity style={styles.saveButton} onPress={handleSave}>
            <Plus color="#fff" size={20} />
            <Text style={styles.buttonText}>Add to My List</Text>
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Palette.background },
  title: { color: Palette.text, fontSize: 28, fontWeight: 'bold' },
  subtitle: { color: Palette.textMuted, fontSize: 15, marginTop: 8, marginBottom: 32 },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Palette.card,
    borderRadius: 16,
    paddingHorizontal: 16,
    height: 60,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  input: { flex: 1, color: Palette.text, fontSize: 16, marginLeft: 12 },
  clearIcon: { padding: 4 },
  primaryButton: {
    backgroundColor: Palette.primary,
    height: 56,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 16,
  },
  saveButton: {
    backgroundColor: Palette.live,
    height: 56,
    borderRadius: 16,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 8,
    gap: 8,
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  previewSection: { marginTop: 40, paddingBottom: 40 },
  previewHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  previewLabel: { color: Palette.textMuted, fontSize: 12, fontWeight: '700', textTransform: 'uppercase' },
  cancelLink: { color: '#EF4444', fontSize: 12, fontWeight: '600' }
});