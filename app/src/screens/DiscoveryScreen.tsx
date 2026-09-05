import { useNavigation } from '@react-navigation/native';
import { Filter, RefreshCw } from 'lucide-react-native';
import { useCallback, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { addFavorite } from '../../lib/db';
import { StreamCard } from '../components/StreamCard';
import { useTwitchTrackerDiscovery } from '../hooks/useTwitchTrackerDiscovery';
import { Palette, PlatformColors, Spacing } from '../theme/Theme';
import type { DiscoveryStream } from '../types';

const PLATFORMS = [{ key: 'twitch', label: 'Twitch', color: PlatformColors.twitch }];

const LANGUAGES = [
  { key: 'all', label: 'All Languages' },
  { key: 'en', label: 'English' },
  { key: 'es', label: 'Spanish' },
  { key: 'pt', label: 'Portuguese' },
  { key: 'fr', label: 'French' },
  { key: 'de', label: 'German' },
];

export function DiscoveryScreen() {
  const [selectedPlatform, setSelectedPlatform] = useState('twitch');
  const [selectedLanguage, setSelectedLanguage] = useState('all');
  const navigation = useNavigation<any>();

  const { streams, loading, refreshing, error, hasMore, refresh, loadMore } = useTwitchTrackerDiscovery({
    language: selectedLanguage === 'all' ? undefined : selectedLanguage,
  });

  const handleStreamPress = useCallback(
    (stream: DiscoveryStream) => {
      navigation.navigate('Player', { streamData: null, url: stream.url });
    },
    [navigation]
  );

  const handleAddToLibrary = useCallback(async (stream: DiscoveryStream) => {
    const success = await addFavorite(stream.author, stream.url);
    if (success) {
      Alert.alert('Added to Library', `${stream.author} has been added to your library.`);
    } else {
      Alert.alert('Already in Library', `${stream.author} is already in your library.`);
    }
  }, []);

  const handleRefresh = useCallback(async () => {
    await refresh(true);
  }, [refresh]);

  const renderStream = useCallback(
    ({ item }: { item: DiscoveryStream }) => (
      <StreamCard
        key={item.id}
        title={item.title}
        streamer={item.author}
        thumbnail={item.thumbnail}
        isLive={true}
        url={item.url}
        onPress={() => handleStreamPress(item)}
        category={item.game_name}
        platform={item.platform || 'twitch'}
        showAddButton={true}
        onAddToLibrary={() => handleAddToLibrary(item)}
      />
    ),
    [handleStreamPress, handleAddToLibrary]
  );

  const renderItemSeparator = () => <View style={styles.separator} />;

  const renderFooter = () => {
    if (!hasMore && !loading && !refreshing) return null;
    return (
      <View style={styles.footer}>
        <ActivityIndicator size="small" color={Palette.primary} />
      </View>
    );
  };

  const renderEmpty = () => (
    <View style={styles.empty}>
      <Text style={styles.emptyText}>{error || 'No streams found'}</Text>
    </View>
  );

  return (
    <View style={styles.container}>
      {/* Header with refresh button */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Discover</Text>
        <TouchableOpacity
          style={styles.refreshButton}
          onPress={handleRefresh}
          disabled={refreshing}
        >
          <RefreshCw
            color={refreshing ? Palette.textMuted : Palette.primary}
            size={20}
            style={refreshing ? { transform: [{ rotate: '180deg' }] } : undefined}
          />
        </TouchableOpacity>
      </View>

      {/* Platform filter chips */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.filterRow}
        contentContainerStyle={styles.filterRowContent}
      >
        {PLATFORMS.map((platform) => (
          <TouchableOpacity
            key={platform.key}
            style={[
              styles.chip,
              selectedPlatform === platform.key && {
                backgroundColor: platform.color,
                borderColor: platform.color,
              },
            ]}
            onPress={() => setSelectedPlatform(platform.key)}
          >
            <Text
              style={[styles.chipText, selectedPlatform === platform.key && styles.chipTextActive]}
            >
              {platform.label}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* Language filter */}
      <View style={styles.filterRow}>
        <Filter color={Palette.textMuted} size={16} />
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterRowContent}
        >
          {LANGUAGES.map((lang) => (
            <TouchableOpacity
              key={lang.key}
              style={[
                styles.chip,
                selectedLanguage === lang.key && {
                  backgroundColor: Palette.primary,
                  borderColor: Palette.primary,
                },
              ]}
              onPress={() => setSelectedLanguage(lang.key)}
            >
              <Text
                style={[styles.chipText, selectedLanguage === lang.key && styles.chipTextActive]}
              >
                {lang.label}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      {/* Stream list */}
      <FlatList
        data={streams}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderStream}
        ItemSeparatorComponent={renderItemSeparator}
        ListFooterComponent={renderFooter}
        ListEmptyComponent={renderEmpty}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            colors={[Palette.primary]}
          />
        }
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: Palette.text,
  },
  refreshButton: {
    padding: Spacing.sm,
  },
  filterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  filterRowContent: {
    paddingHorizontal: Spacing.sm,
  },
  chip: {
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Palette.border,
    marginRight: Spacing.sm,
    backgroundColor: Palette.card,
  },
  chipText: {
    fontSize: 14,
    color: Palette.textMuted,
  },
  chipTextActive: {
    color: Palette.text,
    fontWeight: '600',
  },
  listContent: {
    paddingBottom: Spacing.lg,
  },
  separator: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: Palette.border,
    marginLeft: Spacing.lg,
    marginRight: Spacing.lg,
  },
  footer: {
    padding: Spacing.lg,
    alignItems: 'center',
  },
  empty: {
    padding: Spacing.xl,
    alignItems: 'center',
  },
  emptyText: {
    fontSize: 16,
    color: Palette.textMuted,
    textAlign: 'center',
  },
});
