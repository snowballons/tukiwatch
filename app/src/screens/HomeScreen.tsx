import { useNavigation } from '@react-navigation/native';
import { Search, X } from 'lucide-react-native';
import { useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { StreamCard } from '../components/StreamCard';
import { useStreams } from '../context/StreamContext';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { Palette, Spacing } from '../theme/Theme';

function getTimeGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good Morning';
  if (hour < 17) return 'Good Afternoon';
  return 'Good Evening';
}


export function HomeScreen() {
  const { streams, loading, refreshStreams } = useStreams();
  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [greeting, setGreeting] = useState(getTimeGreeting);
  const [filterPlatform, setFilterPlatform] = useState('all');
  const { resolve, resolving } = useStreamResolver();
  const navigation = useNavigation<any>();
  const isResolvingRef = useRef(false);

  const platforms = useMemo(() => {
    const liveStreams = streams.filter((s) => s.status === 'online');
    const unique = new Set(liveStreams.map((s) => s.platform).filter((p): p is string => !!p));
    return ['all', ...Array.from(unique)];
  }, [streams]);

  const filteredStreams = useMemo(() => {
    let result = streams.filter((stream) => stream.status === 'online');

    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      result = result.filter(
        (s) => s.author?.toLowerCase().includes(query) || s.title?.toLowerCase().includes(query)
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
      const data = await resolve(stream.url);
      if (data && data.status === 'online') {
        navigation.navigate('Player', { streamData: data, url: stream.url });
      } else if (data) {
        Alert.alert('Offline', data.error || 'Stream is not live.');
      } else {
        Alert.alert('Error', 'Could not connect to engine.');
      }
    } finally {
      isResolvingRef.current = false;
    }
  };

  if (loading) {
  
  // Update greeting every minute
  useEffect(() => {
    const interval = setInterval(() => {
      setGreeting(getTimeGreeting());
    }, 60000);
    return () => clearInterval(interval);
  }, []);

  return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color={Palette.primary} />
        <Text style={styles.loadingText}>Checking your favorites...</Text>
      </View>
    );
  }


  // Update greeting every minute
  useEffect(() => {
    const interval = setInterval(() => {
      setGreeting(getTimeGreeting());
    }, 60000);
    return () => clearInterval(interval);
  }, []);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.welcomeText}>{greeting}</Text>
        <Text style={styles.title}>Live Now</Text>
      </View>

      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <Search color={Palette.textMuted} size={20} style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search live streams..."
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

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      >
        {filteredStreams.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyTitle}>No favorites are live</Text>
            <Text style={styles.emptySubtitle}>
              Add streamers to your library and they'll appear here when live
            </Text>
          </View>
        ) : (
          filteredStreams.map((stream) => (
            <StreamCard
              key={stream.id}
              title={stream.title}
              streamer={stream.author}
              thumbnail={stream.thumbnail}
              isLive={true}
              url={stream.url}
              onPress={() => handleStreamPress(stream)}
              category={stream.category}
              platform={stream.platform}
              isCached={stream._cached}
              fetchedAt={stream._fetchedAt}
            />
          ))
        )}
      </ScrollView>

      {resolving && (
        <View style={styles.overlay}>
          <ActivityIndicator size="large" color={Palette.primary} />
          <Text style={styles.overlayText}>Loading stream...</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Palette.background, paddingTop: 60 },
  header: { paddingHorizontal: Spacing.lg, marginBottom: Spacing.lg },
  welcomeText: { color: Palette.textMuted, fontSize: 14, fontWeight: '500' },
  title: { color: Palette.text, fontSize: 28, fontWeight: 'bold' },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Palette.card,
    borderRadius: 12,
    paddingHorizontal: 12,
    marginHorizontal: Spacing.lg,
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
    marginHorizontal: Spacing.lg,
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
  scrollContent: { paddingHorizontal: Spacing.lg },
  centered: { justifyContent: 'center', alignItems: 'center' },
  loadingText: { color: Palette.textMuted, marginTop: Spacing.md },
  emptyState: {
    alignItems: 'center',
    paddingVertical: Spacing.xl * 2,
    paddingHorizontal: Spacing.lg,
  },
  emptyTitle: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: '600',
    marginBottom: Spacing.sm,
  },
  emptySubtitle: {
    color: Palette.textMuted,
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
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
