import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Filter, Play, RefreshCw } from 'lucide-react-native';
import { useCallback, useMemo, useRef, useState } from 'react';
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
import type { RootStackParamList } from '../../App';
import { addFavorite } from '../../lib/db';
import { OfflineBanner, OfflineEmpty } from '../components/OfflineState';
import { useConnectivity } from '../hooks/useConnectivity';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { useTwitchTrackerDiscovery } from '../hooks/useTwitchTrackerDiscovery';
import { Spacing, type ThemeColors } from '../theme/Theme';
import { useTheme } from '../theme/ThemeContext';
import type { DiscoveryStream } from '../types';

const LANGUAGES = [
  { key: 'all', label: 'All' },
  { key: 'en', label: 'EN' },
  { key: 'es', label: 'ES' },
  { key: 'pt', label: 'PT' },
  { key: 'fr', label: 'FR' },
  { key: 'de', label: 'DE' },
];

export function DiscoveryScreen() {
  const [selectedLanguage, setSelectedLanguage] = useState('all');
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const { resolve, error: resolveError } = useStreamResolver();
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  const isResolvingRef = useRef(false);
  const { isOffline } = useConnectivity();
  const { streams, loading, refreshing, error, errorKind, hasMore, refresh, loadMore } =
    useTwitchTrackerDiscovery({
      language: selectedLanguage === 'all' ? undefined : selectedLanguage,
    });

  const handleStreamPress = useCallback(
    async (stream: DiscoveryStream) => {
      if (isResolvingRef.current) return;
      isResolvingRef.current = true;
      try {
        const data = await resolve(stream.url);
        if (data && data.status === 'online') {
          navigation.navigate('Player', { streamData: data, url: stream.url });
        } else if (data) {
          Alert.alert('Offline', data.error || 'Stream is not live.');
        } else {
          Alert.alert('Error', resolveError ?? 'Could not connect to engine.');
        }
      } finally {
        isResolvingRef.current = false;
      }
    },
    [navigation, resolve, resolveError]
  );
  const handleAddToLibrary = useCallback(async (stream: DiscoveryStream) => {
    const success = await addFavorite(stream.author, stream.url);
    if (success) {
      Alert.alert('Added', `${stream.author} added to your library`);
    } else {
      Alert.alert('Already in Library', `${stream.author} is already in your library`);
    }
  }, []);

  const handleRefresh = useCallback(async () => {
    await refresh(true);
  }, [refresh]);

  const renderStream = useCallback(
    ({ item }: { item: DiscoveryStream }) => (
      <TouchableOpacity
        style={styles.card}
        onPress={() => handleStreamPress(item)}
        activeOpacity={0.85}
      >
        {/* Live indicator */}
        <View style={styles.liveBadge}>
          <View style={styles.liveDot} />
          <Text style={styles.liveText}>LIVE</Text>
        </View>

        {/* Rank badge */}
        {item._twitchTrackerRank && (
          <View style={styles.rankBadge}>
            <Text style={styles.rankText}>#{item._twitchTrackerRank}</Text>
          </View>
        )}

        {/* Content */}
        <View style={styles.content}>
          <View style={styles.info}>
            <Text style={styles.streamer} numberOfLines={1}>
              {item.author}
            </Text>
            {item.category && (
              <Text style={styles.category} numberOfLines={1}>
                {item.category}
              </Text>
            )}
            {item.viewer_count !== undefined && (
              <Text style={styles.viewers}>{item.viewer_count.toLocaleString()} viewers</Text>
            )}
          </View>

          {/* Action buttons */}
          <View style={styles.actions}>
            <TouchableOpacity
              style={styles.playButton}
              onPress={() => handleStreamPress(item)}
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            >
              <Play color={colors.onPrimary} size={18} fill={colors.onPrimary} />
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.addButton}
              onPress={() => handleAddToLibrary(item)}
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            >
              <Text style={styles.addButtonText}>+</Text>
            </TouchableOpacity>
          </View>
        </View>
      </TouchableOpacity>
    ),
    [handleStreamPress, handleAddToLibrary, styles, colors]
  );

  const renderItemSeparator = () => <View style={styles.separator} />;

  const renderFooter = () => {
    if (error || (!hasMore && !loading && !refreshing)) return null;
    return (
      <View style={styles.footer}>
        <ActivityIndicator size="small" color={colors.primary} />
      </View>
    );
  };

  const renderEmpty = () => {
    if (loading) {
      return (
        <View style={styles.empty}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      );
    }
    if (errorKind === 'offline') {
      return <OfflineEmpty onRetry={() => refresh(true)} retrying={refreshing} />;
    }
    if (error) {
      return (
        <View style={styles.empty}>
          <Text style={styles.emptyText}>{error}</Text>
          <TouchableOpacity
            style={styles.errorRetryButton}
            onPress={() => refresh(true)}
            accessibilityRole="button"
            accessibilityLabel="Retry"
          >
            <Text style={styles.errorRetryText}>Retry</Text>
          </TouchableOpacity>
        </View>
      );
    }
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>No streams found</Text>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Discover</Text>
        <TouchableOpacity
          style={styles.refreshButton}
          onPress={handleRefresh}
          disabled={refreshing}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
        >
          <RefreshCw
            color={refreshing ? colors.textMuted : colors.primary}
            size={20}
            style={refreshing ? { transform: [{ rotate: '180deg' }] } : undefined}
          />
        </TouchableOpacity>
      </View>

      {/* Language filter */}
      <View style={styles.filterContainer}>
        <Filter color={colors.textMuted} size={16} style={styles.filterIcon} />
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.filterScroll}
          contentContainerStyle={styles.filterContent}
        >
          {LANGUAGES.map((lang) => (
            <TouchableOpacity
              key={lang.key}
              style={[styles.chip, selectedLanguage === lang.key && styles.chipActive]}
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
      {isOffline && (
        <OfflineBanner
          message="No internet connection — discovery is unavailable"
          onRetry={handleRefresh}
          retrying={refreshing}
        />
      )}
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
            colors={[colors.primary]}
          />
        }
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
      />
    </View>
  );
}

const makeStyles = (colors: ThemeColors) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: colors.background,
      paddingTop: 60,
    },
    header: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      paddingHorizontal: Spacing.lg,
      paddingVertical: Spacing.md,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
      backgroundColor: colors.background,
    },
    headerTitle: {
      fontSize: 24,
      fontWeight: 'bold',
      color: colors.text,
    },
    refreshButton: {
      padding: Spacing.sm,
    },
    filterContainer: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: Spacing.lg,
      paddingVertical: Spacing.sm,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
      backgroundColor: colors.background,
    },
    filterIcon: {
      marginRight: Spacing.sm,
    },
    filterScroll: {
      flex: 1,
    },
    filterContent: {
      alignItems: 'center',
    },
    chip: {
      paddingHorizontal: Spacing.md,
      paddingVertical: Spacing.sm,
      borderRadius: 16,
      borderWidth: 1,
      borderColor: colors.border,
      marginRight: Spacing.sm,
      backgroundColor: colors.card,
    },
    chipActive: {
      backgroundColor: colors.primary,
      borderColor: colors.primary,
    },
    chipText: {
      fontSize: 14,
      color: colors.textMuted,
    },
    chipTextActive: {
      color: colors.onPrimary,
      fontWeight: '600',
    },
    listContent: {
      paddingBottom: Spacing.lg,
    },
    card: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: Spacing.lg,
      paddingVertical: Spacing.md,
      backgroundColor: colors.card,
      marginHorizontal: Spacing.lg,
      marginBottom: Spacing.sm,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.border,
    },
    liveBadge: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.danger,
      paddingHorizontal: Spacing.sm,
      paddingVertical: 4,
      borderRadius: 4,
      marginRight: Spacing.sm,
    },
    liveDot: {
      width: 6,
      height: 6,
      borderRadius: 3,
      backgroundColor: '#fff',
      marginRight: 4,
    },
    liveText: {
      color: '#fff',
      fontSize: 10,
      fontWeight: '700',
    },
    rankBadge: {
      backgroundColor: 'rgba(0,0,0,0.6)',
      paddingHorizontal: 6,
      paddingVertical: 2,
      borderRadius: 4,
      marginRight: Spacing.sm,
    },
    rankText: {
      color: '#ffd700',
      fontSize: 10,
      fontWeight: '700',
    },
    content: {
      flex: 1,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
    },
    info: {
      flex: 1,
      marginRight: Spacing.sm,
    },
    streamer: {
      color: colors.text,
      fontSize: 16,
      fontWeight: '600',
    },
    category: {
      color: colors.accent,
      fontSize: 12,
      marginTop: 2,
    },
    viewers: {
      color: colors.textMuted,
      fontSize: 12,
      marginTop: 2,
    },
    actions: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
    },
    playButton: {
      backgroundColor: colors.primary,
      padding: 10,
      borderRadius: 20,
    },
    addButton: {
      backgroundColor: 'rgba(16, 185, 129, 0.15)',
      width: 40,
      height: 40,
      borderRadius: 20,
      alignItems: 'center',
      justifyContent: 'center',
    },
    addButtonText: {
      color: colors.live,
      fontSize: 20,
      fontWeight: '600',
      lineHeight: 24,
    },
    separator: {
      height: StyleSheet.hairlineWidth,
      backgroundColor: colors.border,
      marginHorizontal: Spacing.lg,
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
      color: colors.textMuted,
      textAlign: 'center',
    },
    errorRetryButton: {
      marginTop: Spacing.md,
      backgroundColor: colors.primary,
      paddingHorizontal: Spacing.xl,
      paddingVertical: Spacing.sm,
      borderRadius: 12,
      alignItems: 'center',
    },
    errorRetryText: {
      color: colors.onPrimary,
      fontSize: 15,
      fontWeight: '700',
    },
  });
