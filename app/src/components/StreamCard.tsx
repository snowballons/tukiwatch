import React from 'react';
import { View, Text, StyleSheet, Image, TouchableOpacity, Dimensions } from 'react-native';
import { Palette, Spacing } from '../theme/Theme';
import { Play } from 'lucide-react-native';

const { width } = Dimensions.get('window');

interface StreamCardProps {
  title: string;
  streamer: string;
  thumbnail?: string;
  isLive: boolean;
  url: string;
  onPress?: () => void;
}

export function StreamCard({ title, streamer, thumbnail, isLive, url, onPress }: StreamCardProps) {
  const handlePress = () => {
    if (onPress) {
      onPress();
    } else {
      // Default behavior - could navigate to player
      console.log('Playing stream:', url);
    }
  };

  return (
    <TouchableOpacity style={styles.card} onPress={handlePress} activeOpacity={0.9}>
      <Image 
        source={{ uri: thumbnail || 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80' }} 
        style={styles.thumbnail} 
      />
      
      {/* Live Indicator Overlay */}
      <View style={[styles.badge, { backgroundColor: isLive ? 'rgba(16, 185, 129, 0.9)' : 'rgba(63, 63, 70, 0.9)' }]}>
        <Text style={styles.badgeText}>{isLive ? 'LIVE' : 'OFFLINE'}</Text>
      </View>

      <View style={styles.info}>
        <View style={styles.textContainer}>
          <Text style={styles.title} numberOfLines={1}>{title}</Text>
          <Text style={styles.streamer}>{streamer}</Text>
        </View>
        {isLive && (
          <View style={styles.playIcon}>
            <Play color="#fff" size={16} fill="#fff" />
          </View>
        )}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: { backgroundColor: Palette.card, borderRadius: 20, marginBottom: Spacing.md, overflow: 'hidden', borderWidth: 1, borderColor: Palette.border },
  thumbnail: { width: '100%', height: 180, opacity: 0.7 },
  badge: { position: 'absolute', top: 12, left: 12, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  badgeText: { color: '#fff', fontSize: 10, fontWeight: '800', letterSpacing: 0.5 },
  info: { padding: Spacing.md, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  textContainer: { flex: 1, marginRight: 10 },
  title: { color: Palette.text, fontSize: 16, fontWeight: '600' },
  streamer: { color: Palette.textMuted, fontSize: 13, marginTop: 2 },
  playIcon: { backgroundColor: Palette.primary, padding: 8, borderRadius: 12 },
});