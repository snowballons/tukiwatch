export interface ThemeColors {
  background: string;
  backgroundGradient: [string, string];
  card: string;
  text: string;
  textMuted: string;
  primary: string;
  secondary: string;
  live: string;
  offline: string;
  accent: string;
  border: string;
  danger: string;
  warning: string;
  /** Text/icons drawn on top of primary-colored surfaces. */
  onPrimary: string;
}

export const DarkColors: ThemeColors = {
  // Dark Mode (Primary)
  background: '#0A0A0A',
  backgroundGradient: ['#1A1A2E', '#0A0A0A'],
  card: '#161616',
  text: '#FFFFFF',
  textMuted: '#A1A1AA',

  // Accents
  primary: '#2B35FF',
  secondary: '#1C1C1E',
  live: '#10B981', // Vivid Emerald
  offline: '#3F3F46', // Zinc Gray
  accent: '#00D1FF',
  border: '#27272A',

  // Functional (mode-independent)
  danger: '#EF4444',
  warning: '#FBBF24',
  onPrimary: '#FFFFFF',
};

export const LightColors: ThemeColors = {
  background: '#F9FAFB',
  backgroundGradient: ['#FFFFFF', '#E8EAF0'],
  card: '#FFFFFF',
  text: '#111827',
  textMuted: '#6B7280',

  // Accents (brand hues kept; accent deepened for contrast on white)
  primary: '#2B35FF',
  secondary: '#E5E7EB',
  live: '#10B981',
  offline: '#9CA3AF',
  accent: '#0284C7',
  border: '#E5E7EB',

  // Functional (mode-independent)
  danger: '#EF4444',
  warning: '#B45309',
  onPrimary: '#FFFFFF',
};

export const PlatformColors = {
  twitch: '#9146FF',
  youtube: '#FF0000',
  kick: '#53FC18',
  facebook: '#1877F2',
  instagram: '#E4405F',
  tiktok: '#000000',
  bigo: '#FF6B35',
  dailymotion: '#0066DC',
  vimeo: '#1AB7EA',
  steam: '#171A21',
  bilibili: '#FB7299',
  huya: '#FF7F00',
  picarto: '#1DA1F2',
  trovo: '#00D7FF',
  ustreamtv: '#3388CC',
  vk: '#4680C2',
  dlive: '#FFD700',
  goodgame: '#00AA00',
  abematv: '#00D4AA',
  aloula: '#FF6B6B',
  unknown: '#6B7280',
  default: '#6B7280',
};

export const Spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
};
