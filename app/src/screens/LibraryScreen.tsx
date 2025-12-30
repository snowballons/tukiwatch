import React, { useState } from 'react';
import { View, Text, StyleSheet, FlatList, ActivityIndicator, Alert } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useStreams } from '../context/StreamContext';
import { StreamCard } from '../components/StreamCard';
import { useStreamResolver } from '../hooks/useStreamResolver';
import { Palette, Spacing } from '../theme/Theme';

export function LibraryScreen() {
  const { streams, loading, refreshStreams } = useStreams();
  const { resolve, resolving } = useStreamResolver();
  const navigation = useNavigation<any>();

  const offlineStreams = streams.filter(stream => stream.status === 'offline');

  const handleStreamPress = async (stream: any) => {
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

      <FlatList
        data={offlineStreams}
        keyExtractor={(item) => item.id.toString()}
        contentContainerStyle={{ paddingBottom: 100 }}
        renderItem={({ item }) => (
          <StreamCard
            title={item.streamer_name || item.title}
            streamer={item.author || 'Unknown'}
            thumbnail={item.thumbnail}
            isLive={false}
            url={item.url}
            onPress={() => handleStreamPress(item)}
          />
        )}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>All favorites are live!</Text>
            <Text style={styles.emptySubtext}>Check the Home tab to watch them.</Text>
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