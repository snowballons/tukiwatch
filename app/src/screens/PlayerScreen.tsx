import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator, Dimensions, ScrollView } from 'react-native';
import { useVideoPlayer, VideoView } from 'expo-video';
import { Palette, Spacing } from '../theme/Theme';
import { X, Maximize, Settings2 } from 'lucide-react-native';

const { width } = Dimensions.get('window');

export function PlayerScreen({ route, navigation }: any) {
  const { streamData } = route.params; // We pass the resolved data from the previous screen
  const [currentQuality, setCurrentQuality] = useState('best');
  const [isChanging, setIsChanging] = useState(false);

  const player = useVideoPlayer(streamData.best_quality, (p) => {
    p.loop = false;
    p.play();
  });

  const changeQuality = async (name: string, url: string) => {
    setIsChanging(true);
    setCurrentQuality(name);
    await player.replaceAsync(url);
    player.play();
    setIsChanging(false);
  };

  return (
    <View style={styles.container}>
      {/* Header with Close Button */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.closeBtn}>
          <X color="#fff" size={24} />
        </TouchableOpacity>
        <Text style={styles.headerTitle} numberOfLines={1}>{streamData.author}</Text>
        <View style={{ width: 40 }} /> 
      </View>

      {/* Video Section */}
      <View style={styles.videoContainer}>
        <VideoView 
          player={player} 
          style={styles.video} 
          nativeControls 
          allowsPictureInPicture 
        />
        {isChanging && (
          <View style={styles.overlay}>
            <ActivityIndicator color={Palette.primary} />
          </View>
        )}
      </View>

      {/* Metadata & Actions */}
      <ScrollView contentContainerStyle={styles.details}>
        <Text style={styles.streamTitle}>{streamData.title}</Text>
        
        <View style={styles.statusRow}>
          <View style={styles.liveBadge}><Text style={styles.liveText}>LIVE</Text></View>
          <Text style={styles.authorName}>{streamData.author}</Text>
        </View>

        <View style={styles.divider} />

        <View style={styles.sectionHeader}>
          <Settings2 color={Palette.textMuted} size={18} />
          <Text style={styles.sectionTitle}>Stream Quality</Text>
        </View>

        <View style={styles.qualityGrid}>
          {Object.entries(streamData.all_qualities).map(([name, url]) => (
            <TouchableOpacity 
              key={name} 
              style={[styles.qualityBtn, currentQuality === name && styles.activeBtn]}
              onPress={() => changeQuality(name, url as string)}
            >
              <Text style={[styles.qualityLabel, currentQuality === name && styles.activeText]}>
                {name}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Palette.background },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingTop: 60, paddingHorizontal: Spacing.md, paddingBottom: 10 },
  headerTitle: { color: Palette.text, fontSize: 16, fontWeight: '600', flex: 1, textAlign: 'center' },
  closeBtn: { padding: 8, backgroundColor: '#1A1A1A', borderRadius: 20 },
  videoContainer: { width: width, height: width * (9/16), backgroundColor: '#000' },
  video: { flex: 1 },
  overlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', alignItems: 'center' },
  details: { padding: Spacing.lg },
  streamTitle: { color: Palette.text, fontSize: 22, fontWeight: 'bold', lineHeight: 30 },
  statusRow: { flexDirection: 'row', alignItems: 'center', marginTop: 12, gap: 10 },
  liveBadge: { backgroundColor: Palette.live, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 4 },
  liveText: { color: '#fff', fontSize: 10, fontWeight: '900' },
  authorName: { color: Palette.textMuted, fontSize: 16 },
  divider: { height: 1, backgroundColor: Palette.border, marginVertical: 25 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 15 },
  sectionTitle: { color: Palette.text, fontSize: 14, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 1 },
  qualityGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  qualityBtn: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: 12, backgroundColor: Palette.card, borderWidth: 1, borderColor: Palette.border },
  activeBtn: { backgroundColor: Palette.primary, borderColor: Palette.primary },
  qualityLabel: { color: Palette.textMuted, fontSize: 13, fontWeight: '600' },
  activeText: { color: '#fff' }
});