import React, { useState } from 'react';
import { View, Text, StyleSheet, Image, TouchableOpacity, Dimensions } from 'react-native';
import { Palette, Spacing, PlatformColors } from '../theme/Theme';
import { Play, Trash2, Share2 } from 'lucide-react-native';

const { width } = Dimensions.get('window');

interface StreamCardProps {
  title: string;
  streamer: string;
  thumbnail?: string;
  isLive: boolean;
  url: string;
  onPress?: () => void;
  category?: string;
  platform?: string;
  onDelete?: () => void;
  showDelete?: boolean;
  isCached?: boolean;
  fetchedAt?: number;
  sharedBy?: string;
  sharedAt?: string;
  onShare?: () => void;
  showShare?: boolean;
}

function formatCacheAge(fetchedAt: number): string {
  const seconds = Math.floor((Date.now() - fetchedAt) / 1000);
  if (seconds < 10) return 'just now';
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  return `${Math.floor(minutes / 60)}h`;
}

export function StreamCard({ title, streamer, thumbnail, isLive, url, onPress, category, platform, onDelete, showDelete, isCached, fetchedAt, sharedBy, sharedAt, onShare, showShare }: StreamCardProps) {
  const [imageError, setImageError] = useState(false);
  
  const handlePress = () => {
    if (onPress) {
      onPress();
    } else {
      // Default behavior - could navigate to player
      console.log('Playing stream:', url);
    }
  };

  const platformColor = PlatformColors[platform as keyof typeof PlatformColors] || PlatformColors.default;

  // Fallback image - use a reliable source
  const fallbackImage = 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=300&h=169&fit=crop';
  
  // Use thumbnail from backend, fallback to reliable image if it fails to load
  const imageSource = imageError ? fallbackImage : (thumbnail || fallbackImage);

  return (
    <TouchableOpacity style={styles.card} onPress={handlePress} activeOpacity={0.9}>
      <Image 
        source={{ uri: imageSource }} 
        style={styles.thumbnail}
        onError={() => setImageError(true)}
      />
      
      {/* Live Indicator Overlay */}
      <View style={[styles.badge, { backgroundColor: isLive ? 'rgba(16, 185, 129, 0.9)' : 'rgba(63, 63, 70, 0.9)' }]}>
        <Text style={styles.badgeText}>{isLive ? 'LIVE' : 'OFFLINE'}</Text>
      </View>

      {/* Platform Badge */}
      {platform && (
        <View style={[styles.platformBadge, { backgroundColor: platformColor }]}>
          <Text style={styles.platformText}>{platform.toUpperCase()}</Text>
        </View>
      )}

      {/* Cache Indicator */}
      {isCached && (
        <View style={styles.cacheBadge}>
          <Text style={styles.cacheText}>⚡{fetchedAt ? ` ${formatCacheAge(fetchedAt)}` : ''}</Text>
        </View>
      )}

      <View style={styles.info}>
        <View style={styles.textContainer}>
          <Text style={styles.title} numberOfLines={1}>{title}</Text>
          <Text style={styles.streamer}>{streamer}</Text>
          {sharedBy && (
            <Text style={styles.sharedBy}>@{sharedBy}{sharedAt ? ` · ${sharedAt}` : ''}</Text>
          )}
          {category && (
            <Text style={styles.category}>{category}</Text>
          )}
        </View>
        <View style={styles.actionButtons}>
          {isLive && (
            <View style={styles.playIcon}>
              <Play color="#fff" size={16} fill="#fff" />
            </View>
          )}
          {showShare && onShare && (
            <TouchableOpacity style={styles.shareIcon} onPress={onShare}>
              <Share2 color={Palette.accent} size={16} />
            </TouchableOpacity>
          )}
          {showDelete && onDelete && (
            <TouchableOpacity style={styles.deleteIcon} onPress={onDelete}>
              <Trash2 color="#ff4444" size={16} />
            </TouchableOpacity>
          )}
        </View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: Palette.card, borderRadius: 20, marginBottom: Spacing.md, overflow: 'hidden', borderWidth: 1, borderColor: Palette.border },
  thumbnail: { width: '100%', height: 180, opacity: 0.7 },
  badge: { position: 'absolute', top: 12, left: 12, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  badgeText: { color: '#fff', fontSize: 10, fontWeight: '800', letterSpacing: 0.5 },
  platformBadge: { position: 'absolute', top: 12, right: 12, paddingHorizontal: 6, paddingVertical: 3, borderRadius: 4 },
  platformText: { color: '#fff', fontSize: 9, fontWeight: '700', letterSpacing: 0.3 },
  cacheBadge: { position: 'absolute', top: 12, right: 60, backgroundColor: 'rgba(255, 215, 0, 0.9)', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 3 },
  cacheText: { fontSize: 9, fontWeight: '700', color: '#000' },
  info: { padding: Spacing.md, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  textContainer: { flex: 1, marginRight: 10 },
  title: { color: Palette.text, fontSize: 16, fontWeight: '600' },
  streamer: { color: Palette.textMuted, fontSize: 13, marginTop: 2 },
  sharedBy: { color: Palette.textMuted, fontSize: 11, marginTop: 2 },
  category: { color: Palette.accent, fontSize: 11, marginTop: 2, fontWeight: '500' },
  actionButtons: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  playIcon: { backgroundColor: Palette.primary, padding: 8, borderRadius: 12 },
  shareIcon: { backgroundColor: Palette.accent + '20', padding: 8, borderRadius: 12 },
  deleteIcon: { backgroundColor: 'rgba(255, 68, 68, 0.1)', padding: 8, borderRadius: 12 },
});