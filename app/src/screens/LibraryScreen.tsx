import { useNavigation } from '@react-navigation/native';
import { Search, X } from 'lucide-react-native';
import { useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { supabase } from '../../lib/supabase';
import { ShareStreamModal } from '../components/ShareStreamModal';
import { StreamCard } from '../components/StreamCard';
import { useStreams } from '../context/StreamContext';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { Palette, Spacing } from '../theme/Theme';

export function LibraryScreen() {
  const { streams, loading, refreshStreams } = useStreams();
  const { resolve, resolving } = useStreamResolver();
  const navigation = useNavigation<any>();
  const [searchQuery, setSearchQuery] = useState('');
  const [filterPlatform, setFilterPlatform] = useState('all');
  const [refreshing, setRefreshing] = useState(false);
  const isResolvingRef = useRef(false);
  const [streamToShare, setStreamToShare] = useState<{
    original_url: string;
    streamer_name: string;
  } | null>(null);
  const [shareModalVisible, setShareModalVisible] = useState(false);

  const platforms = useMemo(() => {
    const unique = new Set(streams.map((s) => s.platform).filter((p): p is string => !!p));
    return ['all', ...Array.from(unique)];
  }, [streams]);

  const filteredStreams = useMemo(() => {
    let result = streams;

    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      result = result.filter(
        (s) =>
          s.streamer_name?.toLowerCase().includes(query) ||
          s.author?.toLowerCase().includes(query) ||
          s.title?.toLowerCase().includes(query)
      );
    }

    if (filterPlatform !== 'all') {
      result = result.filter((s) => s.platform === filterPlatform);
    }

    return result;
  }, [streams, searchQuery, filterPlatform]);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshStreams(true);
    setRefreshing(false);
  };

  const handleStreamPress = async (stream: any) => {
    if (isResolvingRef.current) return;
    isResolvingRef.current = true;
    try {
      if (stream.status === 'online') {
        // Online streams need a resolve call to get playback URLs
        const data = await resolve(stream.url);
        if (data && data.status === 'online') {
          navigation.navigate('Player', { streamData: data, url: stream.url });
          await refreshStreams();
        } else {
          Alert.alert('Stream Unavailable', 'Stream went offline. Refreshing library...');
          await refreshStreams();
        }
      } else if (stream.status === 'error') {
        // Error streams — show the stored error, no need to re-resolve
        const detail = stream.error_details;
        if (detail?.alternative) {
          Alert.alert(detail.error || 'Stream Error', detail.alternative);
        } else {
          Alert.alert('Stream Error', stream.error || 'This stream could not be checked.');
        }
      } else {
        // Offline streams — no need to re-resolve, tell user to refresh
        Alert.alert(
          'Offline',
          'This stream is currently offline. Pull down to refresh and check again.'
        );
      }
    } finally {
      isResolvingRef.current = false;
    }
  };

  const handleDeleteStream = async (streamId: number, streamerName: string) => {
    Alert.alert('Delete Stream', `Remove "${streamerName}" from your library?`, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            const { error } = await supabase.from('favorites').delete().eq('id', streamId);

            if (!error) {
              Alert.alert('Deleted', 'Stream removed from library.');
              await refreshStreams();
            } else {
              Alert.alert('Error', 'Failed to delete stream.');
            }
          } catch (_error) {
            Alert.alert('Error', 'Failed to delete stream.');
          }
        },
      },
    ]);
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator color={Palette.primary} />
        <Text style={styles.loadingText}>Loading library...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.headerTitle}>My Library</Text>

      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <Search color={Palette.textMuted} size={20} style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search streams or streamers..."
          placeholderTextColor={Palette.textMuted}
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
        {searchQuery.length > 0 && (
          <TouchableOpacity onPress={() => setSearchQuery('')}>
            <X color={Palette.textMuted} size={20} />
          </TouchableOpacity>
        )}
      </View>

      {/* Platform Filter */}
      <View style={styles.filterSectionWrapper}>
        {/* "All" button (constant) */}
        <TouchableOpacity
          key="all"
          style={[styles.filterChip, filterPlatform === 'all' && styles.filterChipActive]}
          onPress={() => setFilterPlatform('all')}
        >
          <Text
            style={[styles.filterChipText, filterPlatform === 'all' && styles.filterChipTextActive]}
          >
            All
          </Text>
        </TouchableOpacity>

        {/* Scrollable platforms */}
        {platforms.length > 1 && ( // Only render scrollable if there are other platforms
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.scrollableFilterContent} // New style for scrollable content
          >
            {platforms
              .filter((p) => p !== 'all')
              .map(
                (
                  platform // Filter out 'all'
                ) => (
                  <TouchableOpacity
                    key={platform}
                    style={[
                      styles.filterChip,
                      filterPlatform === platform && styles.filterChipActive,
                    ]}
                    onPress={() => setFilterPlatform(platform)}
                  >
                    <Text
                      style={[
                        styles.filterChipText,
                        filterPlatform === platform && styles.filterChipTextActive,
                      ]}
                    >
                      {platform}
                    </Text>
                  </TouchableOpacity>
                )
              )}
          </ScrollView>
        )}
      </View>

      <FlatList
        data={filteredStreams}
        keyExtractor={(item) => item.id.toString()}
        contentContainerStyle={{ paddingBottom: 100 }}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={Palette.primary}
          />
        }
        renderItem={({ item }) => (
          <StreamCard
            title={item.streamer_name || item.title}
            streamer={item.author || 'Unknown'}
            thumbnail={item.thumbnail}
            isLive={item.status === 'online'}
            url={item.url}
            onPress={() => handleStreamPress(item)}
            category={item.category}
            platform={item.platform}
            showDelete={true}
            onDelete={() => handleDeleteStream(item.id, item.streamer_name || item.title)}
            showShare={true}
            onShare={() => {
              setStreamToShare({
                original_url: item.url,
                streamer_name: item.streamer_name || item.title,
              });
              setShareModalVisible(true);
            }}
            isCached={item._cached}
            fetchedAt={item._fetchedAt}
          />
        )}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No streams in library</Text>
            <Text style={styles.emptySubtext}>Add some streams to get started!</Text>
          </View>
        }
      />

      {resolving && (
        <View style={styles.overlay}>
          <ActivityIndicator size="large" color={Palette.primary} />
          <Text style={styles.overlayText}>Loading stream...</Text>
        </View>
      )}

      <ShareStreamModal
        visible={shareModalVisible}
        stream={streamToShare}
        onClose={() => {
          setShareModalVisible(false);
          setStreamToShare(null);
        }}
        onShared={() => {
          setShareModalVisible(false);
          setStreamToShare(null);
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
    paddingTop: 60,
    paddingHorizontal: Spacing.lg,
  },
  headerTitle: {
    color: Palette.text,
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: Spacing.lg,
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Palette.card,
    borderRadius: 12,
    paddingHorizontal: 12,
    marginBottom: 12,
    height: 48,
  },
  searchIcon: {
    marginRight: 8,
  },
  searchInput: {
    flex: 1,
    color: Palette.text,
    fontSize: 16,
  },
  filterSectionWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
    maxHeight: 40, // Keeping this to maintain overall height
  },
  filterContainer: {
    flex: 1, // Takes remaining space
  },
  scrollableFilterContent: {
    gap: 8,
    paddingRight: Spacing.lg, // Add padding to the end of the scrollable content
  },
  filterChip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: Palette.card,
    borderWidth: 1,
    borderColor: Palette.border,
  },
  filterChipActive: {
    backgroundColor: Palette.primary,
    borderColor: Palette.primary,
  },
  filterChipText: {
    color: Palette.textMuted,
    fontSize: 14,
    fontWeight: '600',
    textTransform: 'capitalize',
  },
  filterChipTextActive: {
    color: '#fff',
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    color: Palette.textMuted,
    marginTop: Spacing.md,
  },
  emptyContainer: {
    marginTop: 100,
    alignItems: 'center',
  },
  emptyText: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: '600',
  },
  emptySubtext: {
    color: Palette.textMuted,
    fontSize: 14,
    marginTop: 8,
  },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  overlayText: {
    color: '#fff',
    marginTop: Spacing.md,
    fontSize: 16,
  },
});
