import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, FlatList, ActivityIndicator, Alert, TextInput, TouchableOpacity, ScrollView } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useStreams } from '../context/StreamContext';
import { StreamCard } from '../components/StreamCard';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';
import { Search, X } from 'lucide-react-native';

export function LibraryScreen() {
  const { streams, loading, refreshStreams } = useStreams();
  const { resolve, resolving } = useStreamResolver();
  const navigation = useNavigation<any>();
  const [searchQuery, setSearchQuery] = useState('');
  const [filterPlatform, setFilterPlatform] = useState('all');

  const platforms = useMemo(() => {
    const unique = new Set(streams.map(s => s.platform).filter(Boolean));
    return ['all', ...Array.from(unique)];
  }, [streams]);

  const filteredStreams = useMemo(() => {
    let result = streams;
    
    if (searchQuery.trim()) {
      const query = searchQuery.toLowerCase();
      result = result.filter(s => 
        s.streamer_name?.toLowerCase().includes(query) || 
        s.author?.toLowerCase().includes(query) ||
        s.title?.toLowerCase().includes(query)
      );
    }
    
    if (filterPlatform !== 'all') {
      result = result.filter(s => s.platform === filterPlatform);
    }
    
    return result;
  }, [streams, searchQuery, filterPlatform]);

  const handleStreamPress = async (stream: any) => {
    // If stream is already known to be online, resolve and play directly
    if (stream.status === 'online') {
      const data = await resolve(stream.url);
      if (data && data.status === 'online') {
        navigation.navigate('Player', { streamData: data });
        // Refresh streams after successful play to update status
        await refreshStreams();
      } else {
        Alert.alert("Stream Unavailable", "Stream went offline. Refreshing library...");
        await refreshStreams();
      }
    } else {
      // For offline streams, check if they're now online
      const data = await resolve(stream.url);
      if (data && data.status === 'online') {
        navigation.navigate('Player', { streamData: data });
        // Refresh streams after successful play to update status
        await refreshStreams();
      } else if (data) {
        Alert.alert("Offline", data.error || "Stream is not live.");
      } else {
        Alert.alert("Error", "Could not connect to engine.");
      }
    }
  };

  const handleDeleteStream = async (streamId: number, streamerName: string) => {
    Alert.alert(
      "Delete Stream",
      `Remove "${streamerName}" from your library?`,
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Delete",
          style: "destructive",
          onPress: async () => {
            try {
              const { error } = await supabase
                .from('favorites')
                .delete()
                .eq('id', streamId);

              if (!error) {
                Alert.alert("Deleted", "Stream removed from library.");
                await refreshStreams();
              } else {
                Alert.alert("Error", "Failed to delete stream.");
              }
            } catch (error) {
              Alert.alert("Error", "Failed to delete stream.");
            }
          }
        }
      ]
    );
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
      {platforms.length > 1 && (
        <ScrollView 
          horizontal 
          showsHorizontalScrollIndicator={false}
          style={styles.filterContainer}
          contentContainerStyle={styles.filterContent}
        >
          {platforms.map(platform => (
            <TouchableOpacity
              key={platform}
              style={[
                styles.filterChip,
                filterPlatform === platform && styles.filterChipActive
              ]}
              onPress={() => setFilterPlatform(platform)}
            >
              <Text style={[
                styles.filterChipText,
                filterPlatform === platform && styles.filterChipTextActive
              ]}>
                {platform === 'all' ? 'All' : platform}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      )}

      <FlatList
        data={filteredStreams}
        keyExtractor={(item) => item.id.toString()}
        contentContainerStyle={{ paddingBottom: 100 }}
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
            isCached={item._cached}
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
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
    paddingTop: 60,
    paddingHorizontal: Spacing.lg
  },
  headerTitle: {
    color: Palette.text,
    fontSize: 28,
    fontWeight: 'bold',
    marginBottom: Spacing.lg
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Palette.card,
    borderRadius: 12,
    paddingHorizontal: 12,
    marginBottom: 12,
    height: 48
  },
  searchIcon: {
    marginRight: 8
  },
  searchInput: {
    flex: 1,
    color: Palette.text,
    fontSize: 16
  },
  filterContainer: {
    marginBottom: 16,
    maxHeight: 40
  },
  filterContent: {
    gap: 8
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
    borderColor: Palette.primary
  },
  filterChipText: {
    color: Palette.textMuted,
    fontSize: 14,
    fontWeight: '600',
    textTransform: 'capitalize'
  },
  filterChipTextActive: {
    color: '#fff'
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center'
  },
  loadingText: {
    color: Palette.textMuted,
    marginTop: Spacing.md
  },
  emptyContainer: {
    marginTop: 100,
    alignItems: 'center',
  },
  emptyText: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: '600'
  },
  emptySubtext: {
    color: Palette.textMuted,
    fontSize: 14,
    marginTop: 8
  },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center'
  },
  overlayText: {
    color: '#fff',
    marginTop: Spacing.md,
    fontSize: 16
  }
});