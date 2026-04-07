import React, { useState, useEffect, useRef } from 'react';
import { View, Text, StyleSheet, FlatList, ActivityIndicator, Alert, TextInput, TouchableOpacity, ScrollView, RefreshControl } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useCommunity } from '../context/CommunityContext';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { StreamCard } from '../components/StreamCard';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';
import { Search, X, Users } from 'lucide-react-native';
import { CommunityStream, COMMUNITY_CATEGORIES, COMMUNITY_COUNTRIES, CommunityFilters } from '../types';

function formatRelativeTime(dateString: string): string {
  const date = new Date(dateString);
  const now = new Date();
  const seconds = Math.floor((now.getTime() - date.getTime()) / 1000);
  
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h`;
  const days = Math.floor(hours / 24);
  return `${days}d`;
}

const FILTER_OPTIONS: { key: 'platform' | 'category' | 'country'; label: string }[] = [
  { key: 'platform', label: 'Platform' },
  { key: 'category', label: 'Category' },
  { key: 'country', label: 'Country' },
];

export function CommunityScreen() {
  const { streams, loading, filters, setFilters, refresh } = useCommunity();
  const { resolve, resolving } = useStreamResolver();
  const navigation = useNavigation<any>();
  const [searchQuery, setSearchQuery] = useState('');
  const [activeFilter, setActiveFilter] = useState<'platform' | 'category' | 'country' | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const isResolvingRef = useRef(false);

  useEffect(() => {
    refresh({ search: searchQuery });
  }, [searchQuery]);

  const onRefresh = async () => {
    setRefreshing(true);
    await refresh({ ...filters, search: searchQuery });
    setRefreshing(false);
  };

  const handleStreamPress = async (stream: CommunityStream) => {
    if (isResolvingRef.current) return;
    isResolvingRef.current = true;
    try {
      const data = await resolve(stream.original_url);
      if (data && data.status === 'online') {
        navigation.navigate('Player', { streamData: data, url: stream.original_url });
      } else {
        Alert.alert("Unavailable", "This stream is currently offline.");
      }
    } catch (error) {
      Alert.alert("Error", "Could not load this stream.");
    } finally {
      isResolvingRef.current = false;
    }
  };

  const handleLongPress = async (stream: CommunityStream) => {
    Alert.alert(
      'Add to My List',
      `Add "${stream.streamer_name}" to your library?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Add',
          onPress: async () => {
            try {
              const { data: { user } } = await supabase.auth.getUser();
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
                Alert.alert('Already Added', 'This stream is already in your library.');
                return;
              }

              const { error } = await supabase.from('favorites').insert([{
                user_id: user.id,
                streamer_name: stream.streamer_name,
                original_url: stream.original_url,
              }]);

              if (!error) {
                Alert.alert('Added!', 'Stream added to your library.');
              } else {
                Alert.alert('Error', 'Failed to add stream.');
              }
            } catch (error) {
              Alert.alert('Error', 'Failed to add stream.');
            }
          },
        },
      ]
    );
  };

  const applyFilter = (type: 'platform' | 'category' | 'country', value: string) => {
    const newFilters: CommunityFilters = { ...filters, [type]: value };
    setFilters(newFilters);
    setActiveFilter(null);
  };

  const clearFilter = (type: 'platform' | 'category' | 'country') => {
    const newFilters: CommunityFilters = { ...filters };
    delete newFilters[type];
    setFilters(newFilters);
  };

  const getFilterOptions = (type: 'platform' | 'category' | 'country') => {
    if (type === 'category') return [...COMMUNITY_CATEGORIES];
    if (type === 'country') return [...COMMUNITY_COUNTRIES];
    if (type === 'platform') {
      const platforms = [...new Set(streams.map(s => s.platform))];
      return platforms;
    }
    return [];
  };

  const getCurrentFilterValue = (type: 'platform' | 'category' | 'country') => {
    return filters[type] || '';
  };

  if (loading && streams.length === 0) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator color={Palette.primary} />
        <Text style={styles.loadingText}>Loading community...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.headerTitle}>Community</Text>

      <View style={styles.searchContainer}>
        <Search color={Palette.textMuted} size={20} style={styles.searchIcon} />
        <TextInput
          style={styles.searchInput}
          placeholder="Search streamers..."
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

      <View style={styles.filterRow}>
        {FILTER_OPTIONS.map(filter => (
          <TouchableOpacity
            key={filter.key}
            style={[
              styles.filterChip,
              filters[filter.key] && styles.filterChipActive
            ]}
            onPress={() => setActiveFilter(activeFilter === filter.key ? null : filter.key)}
          >
            <Text style={[
              styles.filterChipText,
              filters[filter.key] && styles.filterChipTextActive
            ]}>
              {filters[filter.key] || filter.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {activeFilter && (
        <View style={styles.filterOptions}>
          {getFilterOptions(activeFilter).map(option => (
            <TouchableOpacity
              key={option}
              style={[
                styles.filterOption,
                getCurrentFilterValue(activeFilter) === option && styles.filterOptionActive
              ]}
              onPress={() => applyFilter(activeFilter, option)}
            >
              <Text style={[
                styles.filterOptionText,
                getCurrentFilterValue(activeFilter) === option && styles.filterOptionTextActive
              ]}>
                {option}
              </Text>
            </TouchableOpacity>
          ))}
          {getCurrentFilterValue(activeFilter) && (
            <TouchableOpacity style={styles.clearFilter} onPress={() => clearFilter(activeFilter)}>
              <Text style={styles.clearFilterText}>Clear</Text>
            </TouchableOpacity>
          )}
        </View>
      )}

      <FlatList
        data={streams}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ paddingBottom: 100 }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={Palette.primary} />
        }
        renderItem={({ item }) => (
          <TouchableOpacity
            onPress={() => handleStreamPress(item)}
            onLongPress={() => handleLongPress(item)}
            delayLongPress={500}
          >
            <StreamCard
              title={item.streamer_name}
              streamer={item.streamer_name}
              thumbnail={undefined}
              isLive={true}
              url={item.original_url}
              category={item.category}
              platform={item.platform}
              sharedBy={item.username}
              sharedAt={formatRelativeTime(item.created_at)}
            />
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Users color={Palette.textMuted} size={48} />
            <Text style={styles.emptyText}>No streams yet</Text>
            <Text style={styles.emptySubtext}>Be the first to share!</Text>
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
  filterRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 12,
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
    backgroundColor: Palette.accent,
    borderColor: Palette.accent,
  },
  filterChipText: {
    color: Palette.textMuted,
    fontSize: 14,
    fontWeight: '600',
  },
  filterChipTextActive: {
    color: '#000',
  },
  filterOptions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 12,
  },
  filterOption: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: Palette.card,
  },
  filterOptionActive: {
    backgroundColor: Palette.accent,
  },
  filterOptionText: {
    color: Palette.text,
    fontSize: 13,
  },
  filterOptionTextActive: {
    color: '#000',
    fontWeight: '600',
  },
  clearFilter: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: 'rgba(255,68,68,0.1)',
  },
  clearFilterText: {
    color: '#EF4444',
    fontSize: 13,
    fontWeight: '600',
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
    gap: 12,
  },
  emptyText: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: '600',
  },
  emptySubtext: {
    color: Palette.textMuted,
    fontSize: 14,
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
