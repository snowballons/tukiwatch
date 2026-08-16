import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef } from 'react';
import { Animated, Image, StyleSheet, Text, View } from 'react-native';
import { Palette } from '../theme/Theme';

export function CustomSplashScreen() {
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0.8)).current;
  const dot1Anim = useRef(new Animated.Value(0.3)).current;
  const dot2Anim = useRef(new Animated.Value(0.3)).current;
  const dot3Anim = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    // Logo entrance animation
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 800,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        tension: 50,
        friction: 7,
        useNativeDriver: true,
      }),
    ]).start();

    // Loading dots animation
    const animateLoadingDots = () => {
      Animated.sequence([
        Animated.timing(dot1Anim, { toValue: 1, duration: 300, useNativeDriver: true }),
        Animated.timing(dot2Anim, { toValue: 1, duration: 300, useNativeDriver: true }),
        Animated.timing(dot3Anim, { toValue: 1, duration: 300, useNativeDriver: true }),
        Animated.timing(dot1Anim, { toValue: 0.3, duration: 300, useNativeDriver: true }),
        Animated.timing(dot2Anim, { toValue: 0.3, duration: 300, useNativeDriver: true }),
        Animated.timing(dot3Anim, { toValue: 0.3, duration: 300, useNativeDriver: true }),
      ]).start(() => animateLoadingDots());
    };

    // Start loading animation after logo appears
    setTimeout(animateLoadingDots, 500);
  }, [dot2Anim, scaleAnim, fadeAnim, dot3Anim, dot1Anim]);

  return (
    <LinearGradient colors={Palette.backgroundGradient} style={styles.container}>
      <Animated.View
        style={[
          styles.content,
          {
            opacity: fadeAnim,
            transform: [{ scale: scaleAnim }],
          },
        ]}
      >
        {/* Logo */}
        <View style={styles.logoContainer}>
          <Image
            source={require('../../assets/logo.png')}
            style={styles.logo}
            resizeMode="contain"
          />
        </View>

        {/* App Name */}
        <Text style={styles.appName}>TukiWatch</Text>
        <Text style={styles.tagline}>Your Streaming Hub</Text>

        {/* Loading indicator */}
        <View style={styles.loadingContainer}>
          <Animated.View style={[styles.loadingDot, { opacity: dot1Anim }]} />
          <Animated.View style={[styles.loadingDot, { opacity: dot2Anim }]} />
          <Animated.View style={[styles.loadingDot, { opacity: dot3Anim }]} />
        </View>
      </Animated.View>
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  content: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoContainer: {
    marginBottom: 30,
    shadowColor: Palette.primary,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.3,
    shadowRadius: 20,
    elevation: 10,
  },
  logo: {
    width: 120,
    height: 120,
  },
  appName: {
    fontSize: 32,
    fontWeight: 'bold',
    color: Palette.text,
    marginBottom: 8,
    letterSpacing: 1,
  },
  tagline: {
    fontSize: 16,
    color: Palette.textMuted,
    marginBottom: 50,
    letterSpacing: 0.5,
  },
  loadingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: Palette.primary,
    marginHorizontal: 4,
  },
});
