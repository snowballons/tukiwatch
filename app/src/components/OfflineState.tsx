import { RefreshCw, WifiOff } from 'lucide-react-native';
import { useMemo } from 'react';
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Spacing, type ThemeColors } from '../theme/Theme';
import { useTheme } from '../theme/ThemeContext';

interface OfflineBannerProps {
  message?: string;
  onRetry?: () => void;
  retrying?: boolean;
}

/** Slim non-blocking banner shown under the screen header when offline. */
export function OfflineBanner({
  message = 'No internet connection',
  onRetry,
  retrying = false,
}: OfflineBannerProps) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  return (
    <View style={styles.banner} accessibilityRole="alert">
      <WifiOff color={colors.warning} size={16} />
      <Text style={styles.bannerText} numberOfLines={2}>
        {message}
      </Text>
      {onRetry && (
        <TouchableOpacity
          onPress={onRetry}
          disabled={retrying}
          style={styles.bannerRetry}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          accessibilityRole="button"
          accessibilityLabel="Retry"
        >
          {retrying ? (
            <ActivityIndicator size="small" color={colors.warning} />
          ) : (
            <RefreshCw color={colors.warning} size={16} />
          )}
        </TouchableOpacity>
      )}
    </View>
  );
}

interface OfflineEmptyProps {
  title?: string;
  message?: string;
  onRetry: () => void;
  retrying?: boolean;
  retryLabel?: string;
}

/** Full empty state with an explicit retry button for offline failures. */
export function OfflineEmpty({
  title = 'You are offline',
  message = 'Check your internet connection and try again.',
  onRetry,
  retrying = false,
  retryLabel = 'Retry',
}: OfflineEmptyProps) {
  const { colors } = useTheme();
  const styles = useMemo(() => makeStyles(colors), [colors]);
  return (
    <View style={styles.empty}>
      <View style={styles.iconCircle}>
        <WifiOff color={colors.textMuted} size={28} />
      </View>
      <Text style={styles.emptyTitle}>{title}</Text>
      <Text style={styles.emptyMessage}>{message}</Text>
      <TouchableOpacity
        style={[styles.retryButton, retrying && styles.retryButtonDisabled]}
        onPress={onRetry}
        disabled={retrying}
        accessibilityRole="button"
        accessibilityLabel={retryLabel}
      >
        {retrying ? (
          <ActivityIndicator color={colors.onPrimary} />
        ) : (
          <Text style={styles.retryText}>{retryLabel}</Text>
        )}
      </TouchableOpacity>
    </View>
  );
}

const makeStyles = (colors: ThemeColors) =>
  StyleSheet.create({
    banner: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      marginHorizontal: Spacing.lg,
      marginBottom: Spacing.sm,
      paddingHorizontal: Spacing.md,
      paddingVertical: Spacing.sm,
      borderRadius: 12,
      borderWidth: 1,
      borderColor: colors.warning,
      backgroundColor: colors.card,
    },
    bannerText: {
      flex: 1,
      color: colors.warning,
      fontSize: 13,
      fontWeight: '600',
    },
    bannerRetry: {
      padding: 4,
    },
    empty: {
      padding: Spacing.xl,
      alignItems: 'center',
    },
    iconCircle: {
      width: 64,
      height: 64,
      borderRadius: 32,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: colors.card,
      borderWidth: 1,
      borderColor: colors.border,
      marginBottom: Spacing.md,
    },
    emptyTitle: {
      fontSize: 18,
      fontWeight: '700',
      color: colors.text,
      marginBottom: Spacing.sm,
    },
    emptyMessage: {
      fontSize: 14,
      color: colors.textMuted,
      textAlign: 'center',
      lineHeight: 20,
      marginBottom: Spacing.lg,
    },
    retryButton: {
      backgroundColor: colors.primary,
      paddingHorizontal: Spacing.xl,
      paddingVertical: Spacing.md,
      borderRadius: 12,
      minWidth: 140,
      alignItems: 'center',
    },
    retryButtonDisabled: {
      opacity: 0.6,
    },
    retryText: {
      color: colors.onPrimary,
      fontSize: 16,
      fontWeight: '700',
    },
  });
