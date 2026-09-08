import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useVideoPlayer, VideoView } from 'expo-video';
import { ChevronDown, ChevronRight, ExternalLink, X } from 'lucide-react-native';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Dimensions,
  FlatList,
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import type { RootStackParamList } from '../../App';
import { OfflineBanner } from '../components/OfflineState';
import { useConnectivity } from '../hooks/useConnectivity';
import { PlatformColors, Spacing, type ThemeColors } from '../theme/Theme';
import { useTheme } from '../theme/ThemeContext';
import type { StreamResolution } from '../types';
import { getPlatformName, openInOfficialApp } from '../utils/platformLinks';

const { width } = Dimensions.get('window');

type PlayerScreenProps = NativeStackScreenProps<RootStackParamList, 'Player'>;

export function PlayerScreen({ route, navigation }: PlayerScreenProps) {
  const { streamData, url } = route.params;
  const sources: Pick<
    StreamResolution,
    'title' | 'author' | 'thumbnail' | 'best_quality' | 'all_qualities' | 'category' | 'platform'
  > & { original_url?: string } = streamData ?? { best_quality: url };
  // Ensure original_url and platform are available for the "Open in App" button
  const originalUrl = sources.original_url || url || '';
  const platformKey = sources.platform || 'unknown';
  const [currentQuality, setCurrentQuality] = useState('best');
  const [isChanging, setIsChanging] = useState(false);
  const [showQualityPicker, setShowQualityPicker] = useState(false);
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const { isOffline } = useConnectivity();

  const player = useVideoPlayer(sources.best_quality ?? url, (p) => {
    p.loop = false;
    p.play();
  });

  const changeQuality = async (name: string, url: string) => {
    setIsChanging(true);
    setCurrentQuality(name);
    setShowQualityPicker(false);
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
        <Text style={styles.headerTitle} numberOfLines={1}>
          {sources.author || 'Stream'}
        </Text>
        <View style={{ width: 40 }} />
      </View>

      {/* Video Section */}
      <View style={styles.videoContainer}>
        <VideoView player={player} style={styles.video} nativeControls allowsPictureInPicture />
        {isChanging && (
          <View style={styles.overlay}>
            <ActivityIndicator color={colors.primary} />
          </View>
        )}
      </View>

      {isOffline && <OfflineBanner message="No internet connection — playback may stall" />}

      {/* Metadata & Actions */}
      <ScrollView contentContainerStyle={styles.details}>
        <Text style={styles.streamTitle}>{sources.title || 'Live Stream'}</Text>

        <View style={styles.statusRow}>
          <View style={styles.liveBadge}>
            <Text style={styles.liveText}>LIVE</Text>
          </View>
          <Text style={styles.authorName}>{sources.author || 'Unknown'}</Text>
        </View>

        <View style={styles.divider} />

        {sources.all_qualities && (
          <TouchableOpacity
            style={styles.qualitySelector}
            onPress={() => setShowQualityPicker(true)}
          >
            <Text style={styles.qualitySelectorLabel}>Stream Quality</Text>
            <View style={styles.qualitySelectorValue}>
              <Text style={styles.qualitySelectorText}>{currentQuality}</Text>
              <ChevronDown color={colors.textMuted} size={20} />
            </View>
          </TouchableOpacity>
        )}

        {/* Open in Official App */}
        {originalUrl ? (
          <TouchableOpacity
            style={styles.openAppButton}
            onPress={() => openInOfficialApp(platformKey, originalUrl)}
          >
            <ExternalLink
              color={
                PlatformColors[platformKey as keyof typeof PlatformColors] || PlatformColors.default
              }
              size={20}
            />
            <Text style={styles.openAppLabel}>Open in {getPlatformName(platformKey)}</Text>
            <ChevronRight color={colors.textMuted} size={20} />
          </TouchableOpacity>
        ) : null}

        {/* Quality Picker Modal */}
        {sources.all_qualities && (
          <Modal
            visible={showQualityPicker}
            transparent={true}
            animationType="fade"
            onRequestClose={() => setShowQualityPicker(false)}
          >
            <View style={styles.modalOverlay}>
              <TouchableOpacity
                style={StyleSheet.absoluteFill}
                activeOpacity={1}
                onPress={() => setShowQualityPicker(false)}
              />
              <View style={styles.modalContent}>
                <Text style={styles.modalTitle}>Select Quality</Text>
                <FlatList
                  data={Object.entries(sources.all_qualities)}
                  keyExtractor={([name]) => name}
                  renderItem={({ item: [name, url] }) => (
                    <TouchableOpacity
                      style={[
                        styles.qualityOption,
                        currentQuality === name && styles.qualityOptionSelected,
                      ]}
                      onPress={() => changeQuality(name, url)}
                    >
                      <Text
                        style={[
                          styles.qualityOptionText,
                          currentQuality === name && styles.qualityOptionTextSelected,
                        ]}
                      >
                        {name}
                      </Text>
                    </TouchableOpacity>
                  )}
                />
              </View>
            </View>
          </Modal>
        )}
      </ScrollView>
    </View>
  );
}

const makeStyles = (colors: ThemeColors) =>
  StyleSheet.create({
    container: { flex: 1, backgroundColor: colors.background },
    header: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingTop: 60,
      paddingHorizontal: Spacing.md,
      paddingBottom: 10,
    },
    headerTitle: {
      color: colors.text,
      fontSize: 16,
      fontWeight: '600',
      flex: 1,
      textAlign: 'center',
    },
    closeBtn: { padding: 8, backgroundColor: '#1A1A1A', borderRadius: 20 },
    videoContainer: { width: width, height: width * (9 / 16), backgroundColor: '#000' },
    video: { flex: 1 },
    overlay: {
      ...StyleSheet.absoluteFill,
      backgroundColor: 'rgba(0,0,0,0.5)',
      justifyContent: 'center',
      alignItems: 'center',
    },
    details: { padding: Spacing.lg },
    streamTitle: { color: colors.text, fontSize: 22, fontWeight: 'bold', lineHeight: 30 },
    statusRow: { flexDirection: 'row', alignItems: 'center', marginTop: 12, gap: 10 },
    liveBadge: {
      backgroundColor: colors.live,
      paddingHorizontal: 8,
      paddingVertical: 2,
      borderRadius: 4,
    },
    liveText: { color: '#fff', fontSize: 10, fontWeight: '900' },
    authorName: { color: colors.textMuted, fontSize: 16 },
    divider: { height: 1, backgroundColor: colors.border, marginVertical: 25 },
    qualitySelector: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      backgroundColor: colors.card,
      padding: Spacing.md,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
    },
    qualitySelectorLabel: {
      color: colors.text,
      fontSize: 16,
      fontWeight: '600',
    },
    qualitySelectorValue: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
    },
    qualitySelectorText: {
      color: colors.primary,
      fontSize: 16,
      fontWeight: '600',
    },
    modalOverlay: {
      flex: 1,
      backgroundColor: 'rgba(0, 0, 0, 0.5)',
      justifyContent: 'center',
      alignItems: 'center',
    },
    modalContent: {
      backgroundColor: colors.card,
      borderRadius: 16,
      width: '80%',
      maxHeight: '60%',
      padding: 20,
    },
    modalTitle: {
      color: colors.text,
      fontSize: 20,
      fontWeight: 'bold',
      marginBottom: 16,
    },
    qualityOption: {
      paddingVertical: 16,
      paddingHorizontal: 12,
      borderRadius: 8,
      marginBottom: 8,
    },
    qualityOptionSelected: {
      backgroundColor: `${colors.primary}20`,
    },
    qualityOptionText: {
      color: colors.text,
      fontSize: 16,
    },
    qualityOptionTextSelected: {
      color: colors.primary,
      fontWeight: '600',
    },
    openAppButton: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.card,
      padding: Spacing.md,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
      marginTop: 12,
      gap: 12,
    },
    openAppLabel: {
      flex: 1,
      color: colors.text,
      fontSize: 16,
      fontWeight: '600',
    },
  });
