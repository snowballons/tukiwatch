import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Modal,
  TouchableOpacity,
  ScrollView,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { Palette, Spacing } from '../theme/Theme';
import { COMMUNITY_CATEGORIES, COMMUNITY_COUNTRIES, COMMUNITY_LANGUAGES } from '../types';
import { detectPlatformFromUrl } from '../utils/detectPlatform';
import { useProfile } from '../hooks/useProfile';
import { shareStream } from '../services/communityService';
import { X, ChevronDown, Share2 } from 'lucide-react-native';

interface ShareStreamModalProps {
  visible: boolean;
  stream: { original_url: string; streamer_name: string } | null;
  onClose: () => void;
  onShared: () => void;
}

interface PickerState {
  visible: boolean;
  type: 'category' | 'country' | 'language' | null;
}

export function ShareStreamModal({ visible, stream, onClose, onShared }: ShareStreamModalProps) {
  const { profile } = useProfile();
  const [loading, setLoading] = useState(false);
  const [category, setCategory] = useState('');
  const [country, setCountry] = useState('');
  const [language, setLanguage] = useState('English');
  const [picker, setPicker] = useState<PickerState>({ visible: false, type: null });

  const platform = stream ? detectPlatformFromUrl(stream.original_url) : 'other';

  const handleShare = async () => {
    if (!stream || !category || !country) {
      Alert.alert('Missing Info', 'Please select category and country.');
      return;
    }

    if (!profile?.username) {
      Alert.alert('Error', 'Unable to get your profile. Please try again.');
      return;
    }

    setLoading(true);
    try {
      await shareStream({
        original_url: stream.original_url,
        streamer_name: stream.streamer_name,
        platform,
        category,
        country,
        language,
        username: profile.username,
      });
      Alert.alert('Shared!', 'Stream added to community.');
      resetForm();
      onShared();
    } catch (error: any) {
      Alert.alert('Error', error.message || 'Failed to share stream.');
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setCategory('');
    setCountry('');
    setLanguage('English');
    setPicker({ visible: false, type: null });
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const openPicker = (type: 'category' | 'country' | 'language') => {
    setPicker({ visible: true, type });
  };

  const closePicker = () => {
    setPicker({ visible: false, type: null });
  };

  const selectOption = (value: string) => {
    if (picker.type === 'category') setCategory(value);
    if (picker.type === 'country') setCountry(value);
    if (picker.type === 'language') setLanguage(value);
    closePicker();
  };

  const getCurrentValue = () => {
    if (picker.type === 'category') return category;
    if (picker.type === 'country') return country;
    if (picker.type === 'language') return language;
    return '';
  };

  const getOptions = () => {
    if (picker.type === 'category') return [...COMMUNITY_CATEGORIES];
    if (picker.type === 'country') return [...COMMUNITY_COUNTRIES];
    if (picker.type === 'language') return [...COMMUNITY_LANGUAGES];
    return [];
  };

  const getPickerLabel = () => {
    if (picker.type === 'category') return 'Select Category';
    if (picker.type === 'country') return 'Select Country';
    if (picker.type === 'language') return 'Select Language';
    return '';
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={handleClose}>
      <View style={styles.overlay}>
        <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={handleClose} />
        <View style={styles.content}>
          <View style={styles.header}>
            <Text style={styles.title}>Share to Community</Text>
            <TouchableOpacity onPress={handleClose}>
              <X color={Palette.textMuted} size={24} />
            </TouchableOpacity>
          </View>

          {stream && (
            <ScrollView style={styles.form} showsVerticalScrollIndicator={false}>
              <Text style={styles.fieldLabel}>Stream</Text>
              <View style={styles.readOnlyField}>
                <Text style={styles.streamName}>{stream.streamer_name}</Text>
              </View>

              <Text style={styles.fieldLabel}>Platform</Text>
              <View style={styles.readOnlyField}>
                <Text style={styles.platformBadge}>{platform}</Text>
              </View>

              <Text style={styles.fieldLabel}>Category *</Text>
              <TouchableOpacity style={styles.selectButton} onPress={() => openPicker('category')}>
                <Text style={[styles.selectText, !category && styles.placeholderText]}>
                  {category || 'Select category'}
                </Text>
                <ChevronDown color={Palette.textMuted} size={20} />
              </TouchableOpacity>

              <Text style={styles.fieldLabel}>Country *</Text>
              <TouchableOpacity style={styles.selectButton} onPress={() => openPicker('country')}>
                <Text style={[styles.selectText, !country && styles.placeholderText]}>
                  {country || 'Select country'}
                </Text>
                <ChevronDown color={Palette.textMuted} size={20} />
              </TouchableOpacity>

              <Text style={styles.fieldLabel}>Language</Text>
              <TouchableOpacity style={styles.selectButton} onPress={() => openPicker('language')}>
                <Text style={styles.selectText}>{language}</Text>
                <ChevronDown color={Palette.textMuted} size={20} />
              </TouchableOpacity>
            </ScrollView>
          )}

          <TouchableOpacity
            style={[
              styles.shareButton,
              { opacity: category && country ? 1 : 0.5 },
            ]}
            onPress={handleShare}
            disabled={loading || !category || !country}
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <>
                <Share2 color="#fff" size={20} />
                <Text style={styles.shareButtonText}>Share to Community</Text>
              </>
            )}
          </TouchableOpacity>
        </View>

        <Modal visible={picker.visible} transparent animationType="fade" onRequestClose={closePicker}>
          <View style={styles.pickerOverlay}>
            <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={closePicker} />
            <View style={styles.pickerContent}>
              <Text style={styles.pickerTitle}>{getPickerLabel()}</Text>
              <ScrollView style={styles.pickerList}>
                {getOptions().map((option) => (
                  <TouchableOpacity
                    key={option}
                    style={[
                      styles.pickerOption,
                      getCurrentValue() === option && styles.pickerOptionSelected,
                    ]}
                    onPress={() => selectOption(option)}
                  >
                    <Text
                      style={[
                        styles.pickerOptionText,
                        getCurrentValue() === option && styles.pickerOptionTextSelected,
                      ]}
                    >
                      {option}
                    </Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          </View>
        </Modal>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  content: {
    backgroundColor: Palette.card,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: Spacing.lg,
    maxHeight: '85%',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: Spacing.lg,
  },
  title: {
    color: Palette.text,
    fontSize: 20,
    fontWeight: 'bold',
  },
  form: {
    marginBottom: Spacing.lg,
  },
  fieldLabel: {
    color: Palette.textMuted,
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    marginBottom: 8,
    marginTop: 16,
  },
  readOnlyField: {
    backgroundColor: Palette.background,
    borderRadius: 8,
    padding: 12,
  },
  streamName: {
    color: Palette.text,
    fontSize: 16,
    fontWeight: '600',
  },
  platformBadge: {
    color: Palette.accent,
    fontSize: 14,
    fontWeight: '600',
    textTransform: 'capitalize',
  },
  selectButton: {
    backgroundColor: Palette.background,
    borderRadius: 8,
    padding: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  selectText: {
    color: Palette.text,
    fontSize: 16,
  },
  placeholderText: {
    color: Palette.textMuted,
  },
  shareButton: {
    backgroundColor: Palette.primary,
    borderRadius: 12,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  shareButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  pickerOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  pickerContent: {
    backgroundColor: Palette.card,
    borderRadius: 16,
    width: '80%',
    maxHeight: '60%',
    padding: 20,
  },
  pickerTitle: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  pickerList: {
    maxHeight: 300,
  },
  pickerOption: {
    paddingVertical: 14,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginBottom: 4,
  },
  pickerOptionSelected: {
    backgroundColor: Palette.primary + '20',
  },
  pickerOptionText: {
    color: Palette.text,
    fontSize: 16,
  },
  pickerOptionTextSelected: {
    color: Palette.primary,
    fontWeight: '600',
  },
});
