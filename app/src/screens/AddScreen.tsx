import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ActivityIndicator, Alert, Keyboard, ScrollView, Modal, FlatList } from 'react-native';
import { supabase } from '../../lib/supabase';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { StreamCard } from '../components/StreamCard';
import { Palette, Spacing } from '../theme/Theme';
import { Link, Plus, X, ChevronDown } from 'lucide-react-native';

// Platform configuration based on backend supported domains
const PLATFORMS = [
  { key: 'twitch', name: 'Twitch', urlTemplate: 'https://www.twitch.tv/{identifier}', placeholder: 'Enter channel name (e.g., ninja)' },
  { key: 'youtube', name: 'YouTube', urlTemplate: 'https://www.youtube.com/{identifier}', placeholder: 'Enter channel/video ID (e.g., watch?v=... or channel/...)' },
  { key: 'facebook', name: 'Facebook', urlTemplate: 'https://www.facebook.com/{identifier}', placeholder: 'Enter page/video path (e.g., page/videos/...)' },
  { key: 'instagram', name: 'Instagram', urlTemplate: 'https://www.instagram.com/{identifier}', placeholder: 'Enter username or post path' },
  { key: 'tiktok', name: 'TikTok', urlTemplate: 'https://www.tiktok.com/{identifier}', placeholder: 'Enter @username or video path' },
  { key: 'bigo', name: 'Bigo', urlTemplate: 'https://www.bigo.tv/{identifier}', placeholder: 'Enter streamer name' },
  { key: 'dailymotion', name: 'Dailymotion', urlTemplate: 'https://www.dailymotion.com/{identifier}', placeholder: 'Enter video/user path' },
  { key: 'vimeo', name: 'Vimeo', urlTemplate: 'https://vimeo.com/{identifier}', placeholder: 'Enter video ID or user path' },
  { key: 'steam', name: 'Steam', urlTemplate: 'https://steamcommunity.com/{identifier}', placeholder: 'Enter broadcast path' },
  { key: 'bilibili', name: 'Bilibili', urlTemplate: 'https://live.bilibili.com/{identifier}', placeholder: 'Enter channel ID or video path' },
  { key: 'huya', name: 'Huya', urlTemplate: 'https://www.huya.com/{identifier}', placeholder: 'Enter streamer name' },
  { key: 'picarto', name: 'Picarto', urlTemplate: 'https://picarto.tv/{identifier}', placeholder: 'Enter channel name' },
  { key: 'trovo', name: 'Trovo', urlTemplate: 'https://trovo.live/{identifier}', placeholder: 'Enter channel name' },
  { key: 'ustream', name: 'Ustream', urlTemplate: 'https://www.ustream.tv/{identifier}', placeholder: 'Enter channel/video path' },
  { key: 'vk', name: 'VK', urlTemplate: 'https://vk.com/{identifier}', placeholder: 'Enter video/user path' },
  { key: 'dlive', name: 'DLive', urlTemplate: 'https://dlive.tv/{identifier}', placeholder: 'Enter channel name' },
  { key: 'goodgame', name: 'GoodGame', urlTemplate: 'https://goodgame.ru/{identifier}', placeholder: 'Enter channel name' },
  { key: 'abematv', name: 'AbemaTV', urlTemplate: 'https://abema.tv/{identifier}', placeholder: 'Enter channel/program path' },
  { key: 'aloula', name: 'Aloula', urlTemplate: 'https://www.aloula.sa/{identifier}', placeholder: 'Enter channel/video path' },
  { key: 'kick', name: 'Kick', urlTemplate: 'https://kick.com/{identifier}', placeholder: 'Enter channel name' },
];

const constructStreamUrl = (platformKey: string, identifier: string): string => {
  const platform = PLATFORMS.find(p => p.key === platformKey);
  if (!platform || !identifier.trim()) return '';
  return platform.urlTemplate.replace('{identifier}', identifier.trim());
};

export function AddScreen() {
  const [selectedPlatform, setSelectedPlatform] = useState<string>('');
  const [identifier, setIdentifier] = useState('');
  const [showPlatformPicker, setShowPlatformPicker] = useState(false);
  const { resolve, resolving } = useStreamResolver();
  const [previewData, setPreviewData] = useState<any>(null);
  const [constructedUrl, setConstructedUrl] = useState('');

  // Update constructed URL whenever platform or identifier changes
  useEffect(() => {
    if (selectedPlatform && identifier.trim()) {
      const url = constructStreamUrl(selectedPlatform, identifier);
      setConstructedUrl(url);
    } else {
      setConstructedUrl('');
    }
  }, [selectedPlatform, identifier]);

  // Function to reset everything
  const handleClear = () => {
    setSelectedPlatform('');
    setIdentifier('');
    setPreviewData(null);
    setConstructedUrl('');
  };

  const handlePreview = async () => {
    if (!selectedPlatform || !identifier.trim()) return;
    
    const url = constructStreamUrl(selectedPlatform, identifier);
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
    if (!user || !previewData || !constructedUrl) return;

    try {
      // Check if URL already exists for this user
      const { data: existingFavorites, error: checkError } = await supabase
        .from('favorites')
        .select('id')
        .eq('user_id', user.id)
        .eq('original_url', constructedUrl);

      if (checkError) {
        Alert.alert("Error", "Failed to check for duplicates.");
        return;
      }

      if (existingFavorites && existingFavorites.length > 0) {
        Alert.alert("Already Added", "This stream is already in your library.");
        return;
      }

      // Insert new favorite
      const { error } = await supabase.from('favorites').insert([
        {
          user_id: user.id,
          streamer_name: previewData.author || "Unknown",
          original_url: constructedUrl,
        },
      ]);

      if (!error) {
        Alert.alert("Saved", "Added to your library.");
        handleClear(); // Reset after successful save
      } else {
        Alert.alert("Error", "Failed to add stream to library.");
      }
    } catch (error) {
      Alert.alert("Error", "Failed to add stream to library.");
    }
  };

  const selectedPlatformData = PLATFORMS.find(p => p.key === selectedPlatform);

  return (
    <ScrollView style={styles.container} contentContainerStyle={{ paddingTop: 80, paddingHorizontal: Spacing.lg }}>
      <Text style={styles.title}>Add New Stream</Text>
      <Text style={styles.subtitle}>Select platform and enter streamer name or stream identifier.</Text>

      {/* Platform Selector */}
      <TouchableOpacity
        style={styles.platformSelector}
        onPress={() => setShowPlatformPicker(true)}
      >
        <Link color={Palette.textMuted} size={20} />
        <Text style={[styles.platformSelectorText, !selectedPlatform && styles.placeholderText]}>
          {selectedPlatform ? selectedPlatformData?.name : 'Select platform'}
        </Text>
        <ChevronDown color={Palette.textMuted} size={20} />
      </TouchableOpacity>

      {/* Identifier Input */}
      <View style={styles.inputWrapper}>
        <TextInput
          style={styles.input}
          placeholder={selectedPlatformData?.placeholder || 'Enter streamer name or stream identifier'}
          placeholderTextColor={Palette.textMuted}
          value={identifier}
          onChangeText={setIdentifier}
          autoCapitalize="none"
          editable={!!selectedPlatform}
        />
        {/* CLEAR BUTTON (X) */}
        {(selectedPlatform || identifier.length > 0) && (
          <TouchableOpacity onPress={handleClear} style={styles.clearIcon}>
            <X color={Palette.textMuted} size={18} />
          </TouchableOpacity>
        )}
      </View>

      {/* URL Preview (optional) */}
      {constructedUrl && (
        <View style={styles.urlPreview}>
          <Text style={styles.urlPreviewLabel}>URL:</Text>
          <Text style={styles.urlPreviewText} numberOfLines={1}>{constructedUrl}</Text>
        </View>
      )}

      <TouchableOpacity
        style={[styles.primaryButton, { opacity: (selectedPlatform && identifier.trim()) ? 1 : 0.5 }]}
        onPress={handlePreview}
        disabled={resolving || !selectedPlatform || !identifier.trim()}
      >
        {resolving ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Verify Link</Text>}
      </TouchableOpacity>

      {/* Platform Picker Modal */}
      <Modal
        visible={showPlatformPicker}
        transparent={true}
        animationType="fade"
        onRequestClose={() => setShowPlatformPicker(false)}
      >
        <View style={styles.modalOverlay}>
          <TouchableOpacity
            style={StyleSheet.absoluteFill}
            activeOpacity={1}
            onPress={() => setShowPlatformPicker(false)}
          />
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Select Platform</Text>
            <FlatList
              data={PLATFORMS}
              keyExtractor={(item) => item.key}
              renderItem={({ item }) => (
                <TouchableOpacity
                  style={[
                    styles.platformOption,
                    selectedPlatform === item.key && styles.platformOptionSelected
                  ]}
                  onPress={() => {
                    setSelectedPlatform(item.key);
                    setShowPlatformPicker(false);
                  }}
                >
                  <Text style={[
                    styles.platformOptionText,
                    selectedPlatform === item.key && styles.platformOptionTextSelected
                  ]}>
                    {item.name}
                  </Text>
                </TouchableOpacity>
              )}
            />
          </View>
        </View>
      </Modal>

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
            url={constructedUrl}
            onPress={() => { }}
            category={previewData.category}
            platform={previewData.platform}
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
  platformSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Palette.card,
    borderRadius: 16,
    paddingHorizontal: 16,
    height: 60,
    borderWidth: 1,
    borderColor: Palette.border,
    marginBottom: 12,
  },
  platformSelectorText: {
    flex: 1,
    color: Palette.text,
    fontSize: 16,
    marginLeft: 12,
  },
  placeholderText: {
    color: Palette.textMuted,
  },
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
  input: { flex: 1, color: Palette.text, fontSize: 16 },
  clearIcon: { padding: 4 },
  urlPreview: {
    marginTop: 12,
    padding: 12,
    backgroundColor: Palette.card,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  urlPreviewLabel: {
    color: Palette.textMuted,
    fontSize: 12,
    fontWeight: '600',
    marginBottom: 4,
  },
  urlPreviewText: {
    color: Palette.text,
    fontSize: 14,
  },
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
  cancelLink: { color: '#EF4444', fontSize: 12, fontWeight: '600' },
  // Modal styles
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalContent: {
    backgroundColor: Palette.card,
    borderRadius: 16,
    width: '80%',
    maxHeight: '70%',
    padding: 20,
  },
  modalTitle: {
    color: Palette.text,
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  platformOption: {
    paddingVertical: 16,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  platformOptionSelected: {
    backgroundColor: Palette.primary + '20',
  },
  platformOptionText: {
    color: Palette.text,
    fontSize: 16,
  },
  platformOptionTextSelected: {
    color: Palette.primary,
    fontWeight: '600',
  },
});