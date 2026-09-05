import { ChevronRight } from 'lucide-react-native';
import type React from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Palette, Spacing } from '../../theme/Theme';

export const sharedSettingsStyles = StyleSheet.create({
  // Tab bar
  headerTitle: {
    color: Palette.text,
    fontSize: 28,
    fontWeight: 'bold',
    paddingHorizontal: Spacing.lg,
    paddingTop: 60,
    paddingBottom: Spacing.md,
  },
  tabBar: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
    paddingHorizontal: Spacing.lg,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: Spacing.md,
    position: 'relative',
  },
  tabLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: Palette.textMuted,
  },
  tabLabelActive: {
    color: Palette.primary,
  },
  tabIndicator: {
    position: 'absolute',
    bottom: -1,
    left: 16,
    right: 16,
    height: 2,
    backgroundColor: Palette.primary,
    borderRadius: 1,
  },
  scroll: {
    flex: 1,
  },
  bottomSpacer: {
    height: 40,
  },
  gapMd: {
    marginTop: Spacing.lg,
  },
  tabContent: {
    paddingTop: Spacing.md,
  },

  // Card
  card: {
    backgroundColor: Palette.card,
    borderRadius: 12,
    marginHorizontal: Spacing.lg,
    overflow: 'hidden',
  },
  cardSectionLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: Palette.textMuted,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.8,
    paddingHorizontal: Spacing.md,
    paddingTop: Spacing.md,
    paddingBottom: Spacing.sm,
  },
  cardRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  cardRowLeft: {
    flex: 1,
    marginRight: Spacing.md,
  },
  cardRowLabel: {
    fontSize: 12,
    color: Palette.textMuted,
    marginBottom: 2,
  },
  cardRowValue: {
    fontSize: 15,
    color: Palette.text,
    fontFamily: 'monospace',
  },

  // Connection status
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    marginRight: Spacing.sm,
  },
  statusDotOk: {
    backgroundColor: Palette.live,
  },
  statusDotErr: {
    backgroundColor: '#EF4444',
  },
  statusTexts: {
    flex: 1,
  },
  statusTextPrimary: {
    fontSize: 15,
    color: Palette.text,
    fontWeight: '600',
  },
  statusTextSub: {
    fontSize: 13,
    color: Palette.textMuted,
    marginTop: 2,
  },
  loadingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  loadingText: {
    fontSize: 14,
    color: Palette.textMuted,
  },

  // Edit server row
  editServerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  editServerText: {
    fontSize: 15,
    color: Palette.primary,
    fontWeight: '600',
  },

  // Destructive row
  destructiveRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.md,
  },
  destructiveRowLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.sm,
  },
  destructiveText: {
    fontSize: 15,
    color: '#EF4444',
    fontWeight: '500',
  },

  // Divider
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: Palette.border,
    marginLeft: Spacing.md,
    marginRight: Spacing.md,
  },

  // Section title
  sectionTitle: {
    fontSize: 11,
    fontWeight: '700',
    color: Palette.textMuted,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.8,
    paddingHorizontal: Spacing.lg,
    marginBottom: Spacing.sm,
    marginTop: Spacing.lg,
  },

  // Modal (shared by ProfileTab and SupporterTab)
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: Spacing.xl,
  },
  modalCard: {
    backgroundColor: Palette.card,
    borderRadius: 16,
    padding: Spacing.lg,
    width: '100%',
    maxWidth: 320,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: Palette.text,
    marginBottom: Spacing.md,
    textAlign: 'center',
  },
  modalInput: {
    backgroundColor: Palette.background,
    borderRadius: 10,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.md,
    fontSize: 16,
    color: Palette.text,
    borderWidth: 1,
    borderColor: Palette.border,
    marginBottom: Spacing.md,
  },
  modalActions: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  modalBtnCancel: {
    flex: 1,
    paddingVertical: Spacing.md,
    borderRadius: 10,
    backgroundColor: Palette.secondary,
    alignItems: 'center',
  },
  modalBtnCancelText: {
    fontSize: 15,
    color: Palette.textMuted,
    fontWeight: '600',
  },
  modalBtnSave: {
    flex: 1,
    paddingVertical: Spacing.md,
    borderRadius: 10,
    backgroundColor: Palette.primary,
    alignItems: 'center',
  },
  modalBtnSaveDisabled: {
    opacity: 0.5,
  },
  modalBtnSaveText: {
    fontSize: 15,
    color: '#fff',
    fontWeight: '600',
  },
});

// ─── Reusable sub-components ────────────────────────────────────────────────

export const SectionTitle: React.FC<{ children: string }> = ({ children }) => (
  <Text style={sharedSettingsStyles.sectionTitle}>{children}</Text>
);

export const Card: React.FC<{ children: React.ReactNode; style?: object }> = ({
  children,
  style,
}) => <View style={[sharedSettingsStyles.card, style]}>{children}</View>;

export const CardRow: React.FC<{
  label: string;
  value: string;
  onPress?: () => void;
  right?: React.ReactNode;
}> = ({ label, value, onPress, right }) => (
  <TouchableOpacity
    style={sharedSettingsStyles.cardRow}
    onPress={onPress}
    activeOpacity={onPress ? 0.7 : 1}
  >
    <View style={sharedSettingsStyles.cardRowLeft}>
      <Text style={sharedSettingsStyles.cardRowLabel}>{label}</Text>
      <Text style={sharedSettingsStyles.cardRowValue}>{value}</Text>
    </View>
    {right ?? <ChevronRight color={Palette.textMuted} size={18} />}
  </TouchableOpacity>
);
