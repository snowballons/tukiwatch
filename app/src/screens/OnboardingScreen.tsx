import React, { useState, useRef, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  Animated,
  useWindowDimensions,
  SafeAreaView,
  AccessibilityInfo
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Palette, PlatformColors } from '../theme/Theme';

// --- VISUAL COMPONENTS ---

const FEATURED_PLATFORMS = [
  { name: 'Twitch', color: PlatformColors.twitch },
  { name: 'YouTube', color: PlatformColors.youtube },
  { name: 'Kick', color: PlatformColors.kick },
  { name: 'TikTok', color: '#EE1D52' },
  { name: 'Facebook', color: PlatformColors.facebook },
  { name: 'Instagram', color: PlatformColors.instagram },
];

function Slide1Visual() {
  const [reduceMotion, setReduceMotion] = useState(false);
  const anims = useRef(FEATURED_PLATFORMS.map(() => new Animated.Value(0))).current;

  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(enabled => {
      setReduceMotion(enabled);
      if (enabled) {
        anims.forEach(anim => anim.setValue(1));
      } else {
        Animated.stagger(
          80,
          anims.map(anim =>
            Animated.timing(anim, {
              toValue: 1,
              duration: 300,
              useNativeDriver: true,
            })
          )
        ).start();
      }
    });
  }, []);

  return (
    <View style={styles.visualContainer}>
      <View style={styles.platformGrid}>
        {FEATURED_PLATFORMS.map((platform, i) => {
          const translateY = anims[i].interpolate({
            inputRange: [0, 1],
            outputRange: [20, 0],
          });
          return (
            <Animated.View
              key={platform.name}
              style={[
                styles.platformPill,
                { borderLeftColor: platform.color },
                { opacity: anims[i], transform: [{ translateY }] }
              ]}
            >
              <View style={[styles.platformDot, { backgroundColor: platform.color }]} />
              <Text style={styles.platformText}>{platform.name}</Text>
            </Animated.View>
          );
        })}
      </View>
      <Animated.Text style={[styles.platformMore, { opacity: anims[FEATURED_PLATFORMS.length - 1] }]}>
        + 17 more platforms
      </Animated.Text>
    </View>
  );
}

function Slide2Visual() {
  const [reduceMotion, setReduceMotion] = useState(false);
  const slideAnim = useRef(new Animated.Value(0)).current;
  const pulseAnim = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(enabled => {
      setReduceMotion(enabled);
      if (enabled) {
        slideAnim.setValue(1);
      } else {
        Animated.spring(slideAnim, {
          toValue: 1,
          friction: 8,
          tension: 60,
          useNativeDriver: true,
        }).start(() => {
          Animated.sequence([
            Animated.delay(300),
            Animated.loop(
              Animated.sequence([
                Animated.timing(pulseAnim, { toValue: 1.3, duration: 750, useNativeDriver: true }),
                Animated.timing(pulseAnim, { toValue: 1.0, duration: 750, useNativeDriver: true })
              ])
            )
          ]).start();
        });
      }
    });
  }, []);

  const translateY = slideAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [-40, 0]
  });

  return (
    <View style={styles.visualContainer}>
      <Animated.View style={[styles.notificationCard, { transform: [{ translateY }] }]}>
        <View style={styles.cardHeader}>
          <View style={styles.liveContainer}>
            <Animated.View style={[styles.liveDot, { transform: [{ scale: pulseAnim }] }]} />
            <Text style={styles.liveText}>LIVE</Text>
          </View>
          <Text style={styles.streamerName}>Ninja</Text>
        </View>
        <Text style={styles.cardDesc}>just went LIVE</Text>
        <Text style={styles.cardDesc}>Tap to watch →</Text>
      </Animated.View>

      <Animated.View style={[styles.notificationCard, styles.notificationCardPartial, { transform: [{ translateY }] }]}>
        <View style={styles.cardHeader}>
          <View style={styles.liveContainer}>
            <View style={styles.liveDot} />
            <Text style={styles.liveText}>LIVE</Text>
          </View>
          <Text style={styles.streamerName}>xQc</Text>
        </View>
      </Animated.View>
    </View>
  );
}

function Slide3Visual() {
  const [reduceMotion, setReduceMotion] = useState(false);
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const shimmerAnim = useRef(new Animated.Value(0.5)).current;

  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(enabled => {
      setReduceMotion(enabled);
      if (enabled) {
        fadeAnim.setValue(1);
        shimmerAnim.setValue(1);
      } else {
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 400,
          useNativeDriver: true,
        }).start(() => {
          Animated.sequence([
            Animated.delay(300),
            Animated.parallel([
              Animated.loop(
                Animated.sequence([
                  Animated.timing(pulseAnim, { toValue: 1.1, duration: 1000, useNativeDriver: true }),
                  Animated.timing(pulseAnim, { toValue: 1.0, duration: 1000, useNativeDriver: true })
                ])
              ),
              Animated.loop(
                Animated.sequence([
                  Animated.timing(shimmerAnim, { toValue: 1, duration: 800, useNativeDriver: true }),
                  Animated.timing(shimmerAnim, { toValue: 0.5, duration: 800, useNativeDriver: true })
                ])
              )
            ])
          ]).start();
        });
      }
    });
  }, []);

  const scale = fadeAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [0.92, 1]
  });

  return (
    <View style={styles.visualContainer}>
      <Animated.View style={[styles.playerFrame, { opacity: fadeAnim, transform: [{ scale }] }]}>
        <View style={styles.playerTopBar}>
          <View />
          <View style={styles.playerLiveBadge}>
            <Text style={styles.playerLiveText}>LIVE</Text>
          </View>
        </View>
        
        <View style={styles.playerCenter}>
          <Animated.View style={[styles.playButton, { transform: [{ scale: pulseAnim }] }]}>
            <Text style={styles.playIcon}>▶</Text>
          </Animated.View>
        </View>

        <View style={styles.playerBottomBar}>
          <View style={styles.progressBarBg}>
            <Animated.View style={[styles.progressBarFill, { opacity: shimmerAnim }]} />
          </View>
          <View style={styles.qualityPill}>
            <Text style={styles.qualityText}>1080p ▾</Text>
          </View>
        </View>
      </Animated.View>
    </View>
  );
}

const SLIDES = [
  { 
    id: 0, 
    headline: 'All Your Streams, One Place', 
    description: 'Add channels from Twitch, YouTube, Kick, and 17+ more platforms. One app, every streamer you follow.', 
    Visual: Slide1Visual 
  },
  { 
    id: 1, 
    headline: 'Never Miss a Live Moment', 
    description: 'Get instant alerts the second your favorites go live — even when the app is closed.', 
    Visual: Slide2Visual 
  },
  { 
    id: 2, 
    headline: 'Watch Without Ads', 
    description: 'Integrated player with quality selection. No interruptions, no redirects — just the stream.', 
    Visual: Slide3Visual 
  },
];

function SlideWrapper({ width, slide }: { width: number, slide: any }) {
  return (
    <View style={[styles.slide, { width }]}>
      <View style={styles.visualZone}>
        <slide.Visual />
      </View>
      <View style={styles.textZone}>
        <Text style={styles.headline}>{slide.headline}</Text>
        <Text style={styles.description}>{slide.description}</Text>
      </View>
    </View>
  );
}

function Dots({ current, total }: { current: number, total: number }) {
  return (
    <View style={styles.dotsContainer}>
      {Array.from({ length: total }).map((_, i) => {
        return <Dot key={i} isActive={i === current} />;
      })}
    </View>
  );
}

function Dot({ isActive }: { isActive: boolean }) {
  const widthAnim = useRef(new Animated.Value(isActive ? 24 : 8)).current;
  const bgColorAnim = useRef(new Animated.Value(isActive ? 1 : 0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(widthAnim, {
        toValue: isActive ? 24 : 8,
        duration: 300,
        useNativeDriver: false,
      }),
      Animated.timing(bgColorAnim, {
        toValue: isActive ? 1 : 0,
        duration: 300,
        useNativeDriver: false,
      }),
    ]).start();
  }, [isActive]);

  const backgroundColor = bgColorAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [Palette.textMuted, Palette.primary]
  });

  return (
    <Animated.View
      style={[
        styles.dot,
        { width: widthAnim, backgroundColor }
      ]}
    />
  );
}

export function OnboardingScreen({ onComplete }: { onComplete: () => void }) {
  const [index, setIndex] = useState(0);
  const flatListRef = useRef<FlatList>(null);
  const { width } = useWindowDimensions();

  const finish = async () => {
    await AsyncStorage.setItem('onboarding_complete', 'true');
    onComplete();
  };

  const goNext = () => {
    if (index < SLIDES.length - 1) {
      flatListRef.current?.scrollToIndex({ index: index + 1, animated: true });
      setIndex(index + 1);
    } else {
      finish();
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <TouchableOpacity 
        style={styles.skip} 
        onPress={finish}
        accessibilityLabel="Skip onboarding"
      >
        <Text style={styles.skipText}>Skip</Text>
      </TouchableOpacity>

      <FlatList
        ref={flatListRef}
        data={SLIDES}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={e => {
          setIndex(Math.round(e.nativeEvent.contentOffset.x / width));
        }}
        renderItem={({ item }) => (
          <SlideWrapper width={width} slide={item} />
        )}
        keyExtractor={item => String(item.id)}
      />

      <View style={styles.bottomChrome}>
        <Dots current={index} total={SLIDES.length} />

        <TouchableOpacity 
          style={styles.cta} 
          onPress={goNext}
          accessibilityLabel={index === SLIDES.length - 1 ? 'Get Started' : 'Next slide'}
        >
          <Text style={styles.ctaText}>
            {index === SLIDES.length - 1 ? 'Get Started' : 'Next →'}
          </Text>
        </TouchableOpacity>

        {index === SLIDES.length - 1 && (
          <TouchableOpacity onPress={finish} style={styles.signInLink}>
            <Text style={styles.signInText}>
              Already have an account? <Text style={styles.signInBold}>Sign In</Text>
            </Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
  skip: {
    position: 'absolute',
    top: 50,
    right: 20,
    zIndex: 10,
    minWidth: 44,
    minHeight: 44,
    justifyContent: 'center',
    alignItems: 'center',
  },
  skipText: {
    color: Palette.textMuted,
    fontSize: 16,
  },
  slide: {
    flex: 1,
    paddingTop: 80,
    alignItems: 'center',
  },
  visualZone: {
    height: '45%',
    width: '100%',
    justifyContent: 'center',
    alignItems: 'center',
  },
  textZone: {
    paddingHorizontal: 32,
    alignItems: 'center',
    marginTop: 20,
  },
  headline: {
    color: Palette.text,
    fontSize: 28,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 16,
  },
  description: {
    color: Palette.textMuted,
    fontSize: 16,
    textAlign: 'center',
    lineHeight: 24,
  },
  bottomChrome: {
    paddingHorizontal: 24,
    paddingBottom: 40,
    alignItems: 'center',
  },
  dotsContainer: {
    flexDirection: 'row',
    marginBottom: 32,
    alignItems: 'center',
  },
  dot: {
    height: 8,
    borderRadius: 4,
    marginHorizontal: 4,
  },
  cta: {
    backgroundColor: Palette.primary,
    width: '100%',
    height: 56,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: 44,
  },
  ctaText: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: '600',
  },
  signInLink: {
    marginTop: 20,
    minHeight: 44,
    justifyContent: 'center',
  },
  signInText: {
    color: Palette.textMuted,
    fontSize: 16,
  },
  signInBold: {
    color: Palette.text,
    fontWeight: 'bold',
  },
  // Visual specific styles
  visualContainer: {
    flex: 1,
    width: '100%',
    justifyContent: 'center',
    alignItems: 'center',
  },
  platformGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 12,
    paddingHorizontal: 20,
  },
  platformPill: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Palette.card,
    borderRadius: 20,
    borderLeftWidth: 3,
    paddingVertical: 10,
    paddingHorizontal: 16,
    margin: 6,
  },
  platformDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
  },
  platformText: {
    color: Palette.text,
    fontSize: 14,
    fontWeight: '500',
  },
  platformMore: {
    color: Palette.textMuted,
    fontSize: 14,
    marginTop: 24,
  },
  notificationCard: {
    backgroundColor: Palette.card,
    borderRadius: 12,
    borderLeftWidth: 3,
    borderLeftColor: Palette.live,
    padding: 16,
    width: '80%',
    marginBottom: 12,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 5,
  },
  notificationCardPartial: {
    opacity: 0.5,
    position: 'absolute',
    bottom: -60, // Partially hidden
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  liveContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginRight: 8,
  },
  liveDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: Palette.live,
    marginRight: 4,
  },
  liveText: {
    color: Palette.live,
    fontSize: 12,
    fontWeight: 'bold',
  },
  streamerName: {
    color: Palette.text,
    fontSize: 16,
    fontWeight: 'bold',
  },
  cardDesc: {
    color: Palette.textMuted,
    fontSize: 14,
    marginTop: 4,
  },
  playerFrame: {
    backgroundColor: '#111',
    width: '85%',
    aspectRatio: 16 / 9,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Palette.border,
    overflow: 'hidden',
    justifyContent: 'space-between',
  },
  playerTopBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 12,
  },
  playerLiveBadge: {
    backgroundColor: Palette.live,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  playerLiveText: {
    color: 'white',
    fontSize: 10,
    fontWeight: 'bold',
  },
  playerCenter: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  playButton: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: Palette.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  playIcon: {
    color: 'white',
    fontSize: 20,
    marginLeft: 4, // Visual center tweak
  },
  playerBottomBar: {
    padding: 12,
  },
  progressBarBg: {
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.2)',
    borderRadius: 2,
    marginBottom: 8,
  },
  progressBarFill: {
    width: '30%',
    height: '100%',
    backgroundColor: Palette.primary,
    borderRadius: 2,
  },
  qualityPill: {
    alignSelf: 'flex-start',
    backgroundColor: 'rgba(0,0,0,0.5)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  qualityText: {
    color: 'white',
    fontSize: 12,
  },
});
