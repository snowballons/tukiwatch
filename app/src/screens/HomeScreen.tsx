import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, RefreshControl, ActivityIndicator, Alert } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useStreams } from '../context/StreamContext';
import { StreamCard } from '../components/StreamCard';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { Palette, Spacing } from '../theme/Theme';

export function HomeScreen() {
  const { streams, loading, refreshStreams } = useStreams();
  const [refreshing, setRefreshing] = useState(false);
  const { resolve, resolving } = useStreamResolver();
  const navigation = useNavigation<any>();

  const liveStreams = streams.filter(stream => stream.status === 'online');

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshStreams(true); // Bypass cache on manual refresh
    setRefreshing(false);
  };

  const handleStreamPress = async (stream: any) => {
    const data = await resolve(stream.url);
    if (data && data.status === 'online') {
      navigation.navigate('Player', { streamData: data });
    } else if (data) {
      Alert.alert("Offline", data.error || "Stream is not live.");
    } else {
      Alert.alert("Error", "Could not connect to engine.");
    }
  };

  if (loading) {
    return (
      <View style={[styles.container, styles.centered]}>
        <ActivityIndicator size="large" color={Palette.primary} />
        <Text style={styles.loadingText}>Checking your favorites...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.welcomeText}>Good Evening</Text>
        <Text style={styles.title}>Live Now</Text>
      </View>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      >
        {liveStreams.length === 0 ? (
          <View style={styles.emptyState}>
            <Text style={styles.emptyTitle}>No favorites are live</Text>
            <Text style={styles.emptySubtitle}>
              Add streamers to your library and they'll appear here when live
            </Text>
          </View>
        ) : (
          liveStreams.map((stream) => (
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
  scrollContent: { paddingHorizontal: Spacing.lg },
  centered: { justifyContent: 'center', alignItems: 'center' },
  loadingText: { color: Palette.textMuted, marginTop: Spacing.md },
  emptyState: {
    alignItems: 'center',
    paddingVertical: Spacing.xl * 2,
    paddingHorizontal: Spacing.lg
  },
  emptyTitle: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: '600',
    marginBottom: Spacing.sm
  },
  emptySubtitle: {
    color: Palette.textMuted,
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20
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