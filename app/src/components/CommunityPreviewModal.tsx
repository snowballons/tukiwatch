import { useNavigation } from '@react-navigation/native';
import { Play, Plus, WifiOff, X } from 'lucide-react-native';
import { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { supabase } from '../../lib/supabase';
import { resolveStream } from '../services/engine';
import { Palette, PlatformColors, Spacing } from '../theme/Theme';
import type { CommunityStream } from '../types';

interface CommunityPreviewModalProps {
  visible: boolean;
  stream: CommunityStream | null;
  onClose: () => void;
  onAdded: () => void;
}

const FALLBACK_IMAGE =
  'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=300&h=169&fit=crop';

export function CommunityPreviewModal({
  visible,
  stream,
  onClose,
  onAdded,
}: CommunityPreviewModalProps) {
  const navigation = useNavigation<any>();
  const [resolving, setResolving] = useState(false);
  const [adding, setAdding] = useState(false);

  const handlePlay = async () => {
    if (!stream) return;
    setResolving(true);
    try {
      const data = await resolveStream(stream.original_url);
      onClose();
      navigation.navigate('Player', { streamData: data, url: stream.original_url });
    } catch {
      Alert.alert('Error', 'Could not load stream.');
    } finally {
      setResolving(false);
    }
  };

  const handleAddToLibrary = async () => {
    if (!stream) return;
    setAdding(true);
    try {
      const {
        data: { user },
      } = await supabase.auth.getUser();
      if (!user) {
        Alert.alert('Error', 'Please sign in first.');
        return;
      }

      const { data: existing } = await supabase
        .from('favorites')
        .select('id')
        .eq('user_id', user.id)
        .eq('original_url', stream.original_url)
        .maybeSingle();

      if (existing) {
        Alert.alert('Already in Library', 'This stream is already in your library.');
        return;
      }

      const { error } = await supabase.from('favorites').insert([
        {
          user_id: user.id,
          streamer_name: stream.streamer_name,
          original_url: stream.original_url,
        },
      ]);

      if (error) throw error;
      Alert.alert('Added!', `"${stream.streamer_name}" added to your library.`);
      onAdded();
      onClose();
    } catch {
      Alert.alert('Error', 'Failed to add stream to library.');
    } finally {
      setAdding(false);
    }
  };

  if (!stream) return null;

  const isOnline = stream.is_online;
  const platformColor =
    PlatformColors[stream.platform as keyof typeof PlatformColors] || PlatformColors.default;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={onClose} />
        <View style={styles.content}>
          {/* Header */}
          <View style={styles.header}>
            <Text style={styles.title} numberOfLines={1}>
              {stream.streamer_name}
            </Text>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <X color={Palette.textMuted} size={22} />
            </TouchableOpacity>
          </View>

          {/* Thumbnail */}
          <View style={styles.thumbnailContainer}>
            <Image source={{ uri: FALLBACK_IMAGE }} style={styles.thumbnail} />
            <View
              style={[
                styles.statusBadge,
                { backgroundColor: isOnline ? 'rgba(16,185,129,0.9)' : 'rgba(63,63,70,0.9)' },
              ]}
            >
              <Text style={styles.statusText}>{isOnline ? 'LIVE' : 'OFFLINE'}</Text>
            </View>
            <View style={[styles.platformBadge, { backgroundColor: platformColor }]}>
              <Text style={styles.platformText}>{stream.platform.toUpperCase()}</Text>
            </View>
          </View>

          {/* Info */}
          <View style={styles.info}>
            <Text style={styles.streamTitle} numberOfLines={2}>
              {stream.streamer_name}
            </Text>
            {stream.category && <Text style={styles.category}>{stream.category}</Text>}
            {!isOnline && (
              <View style={styles.offlineRow}>
                <WifiOff color={Palette.textMuted} size={14} />
                <Text style={styles.offlineText}>Stream is currently offline</Text>
              </View>
            )}
          </View>

          {/* Actions */}
          <View style={styles.actions}>
            {isOnline && (
              <TouchableOpacity
                style={[styles.playButton, resolving && { opacity: 0.6 }]}
                onPress={handlePlay}
                disabled={resolving}
              >
                {resolving ? (
                  <ActivityIndicator color="#fff" size="small" />
                ) : (
                  <>
                    <Play color="#fff" size={18} fill="#fff" />
                    <Text style={styles.playButtonText}>Play Now</Text>
                  </>
                )}
              </TouchableOpacity>
            )}
            <TouchableOpacity
              style={[styles.addButton, adding && { opacity: 0.6 }]}
              onPress={handleAddToLibrary}
              disabled={adding}
            >
              {adding ? (
                <ActivityIndicator color={Palette.live} size="small" />
              ) : (
                <>
                  <Plus color={Palette.live} size={18} />
                  <Text style={styles.addButtonText}>
                    {isOnline ? 'Add to Library' : 'Add Anyway'}
                  </Text>
                </>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  content: {
    backgroundColor: Palette.card,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: Spacing.lg,
    paddingBottom: 36,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.md,
  },
  title: { color: Palette.text, fontSize: 18, fontWeight: 'bold', flex: 1, marginRight: 12 },
  closeBtn: { padding: 4 },
  thumbnailContainer: { borderRadius: 12, overflow: 'hidden', marginBottom: Spacing.md },
  thumbnail: { width: '100%', height: 180, opacity: 0.8 },
  statusBadge: {
    position: 'absolute',
    top: 10,
    left: 10,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  statusText: { color: '#fff', fontSize: 10, fontWeight: '800', letterSpacing: 0.5 },
  platformBadge: {
    position: 'absolute',
    top: 10,
    right: 10,
    paddingHorizontal: 6,
    paddingVertical: 3,
    borderRadius: 4,
  },
  platformText: { color: '#fff', fontSize: 9, fontWeight: '700' },
  info: { marginBottom: Spacing.md },
  streamTitle: { color: Palette.text, fontSize: 16, fontWeight: '600', marginBottom: 4 },
  category: { color: Palette.accent, fontSize: 12, fontWeight: '500' },
  offlineRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 8 },
  offlineText: { color: Palette.textMuted, fontSize: 13 },
  actions: { flexDirection: 'row', gap: 12 },
  playButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: Palette.primary,
    borderRadius: 12,
    paddingVertical: 14,
  },
  playButtonText: { color: '#fff', fontSize: 15, fontWeight: '700' },
  addButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: 'rgba(16,185,129,0.15)',
    borderRadius: 12,
    paddingVertical: 14,
    borderWidth: 1,
    borderColor: Palette.live,
  },
  addButtonText: { color: Palette.live, fontSize: 15, fontWeight: '700' },
});
