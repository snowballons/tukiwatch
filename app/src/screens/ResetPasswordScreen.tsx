import React, { useState } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ActivityIndicator, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';
import { Lock } from 'lucide-react-native';

export function ResetPasswordScreen({ navigation }: any) {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  
  const confirmPasswordInputRef = React.useRef<TextInput>(null);

  const handleUpdatePassword = async () => {
    const { data: { session } } = await supabase.auth.getSession();
    if (!session) {
      Alert.alert('Session Missing', 'Your reset session has expired or is invalid. Please request a new password reset link.', [
        { text: 'Go Back', onPress: () => navigation.navigate('Auth') }
      ]);
      return;
    }
    
    if (!password.trim() || !confirmPassword.trim()) {
      Alert.alert('Required', 'Please fill in all fields.');
      return;
    }

    if (password !== confirmPassword) {
      Alert.alert('Error', 'Passwords do not match.');
      return;
    }

    if (password.length < 6) {
      Alert.alert('Error', 'Password must be at least 6 characters.');
      return;
    }

    setLoading(true);
    const { error } = await supabase.auth.updateUser({ password });
    setLoading(false);

    if (error) {
      Alert.alert('Error', error.message);
    } else {
      Alert.alert('Success', 'Your password has been updated!', [
        { text: 'OK', onPress: () => navigation.navigate('MainTabs') }
      ]);
    }
  };

  return (
    <View style={styles.container}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={styles.content}>
        <View style={styles.iconContainer}>
          <Lock color={Palette.primary} size={48} />
        </View>

        <Text style={styles.title}>Create New Password</Text>
        <Text style={styles.subtitle}>
          Enter your new password below.
        </Text>

        <TextInput
          style={styles.input}
          placeholder="New Password"
          placeholderTextColor={Palette.textMuted}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoFocus
          returnKeyType="next"
          onSubmitEditing={() => confirmPasswordInputRef.current?.focus()}
        />

        <TextInput
          ref={confirmPasswordInputRef}
          style={styles.input}
          placeholder="Confirm Password"
          placeholderTextColor={Palette.textMuted}
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          secureTextEntry
          returnKeyType="done"
          onSubmitEditing={handleUpdatePassword}
        />

        <TouchableOpacity
          style={[styles.primaryBtn, { opacity: password && confirmPassword ? 1 : 0.5 }]}
          onPress={handleUpdatePassword}
          disabled={loading || !password || !confirmPassword}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.btnText}>Update Password</Text>
          )}
        </TouchableOpacity>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
  content: {
    flex: 1,
    paddingHorizontal: Spacing.lg,
    justifyContent: 'center',
  },
  iconContainer: {
    alignSelf: 'center',
    marginBottom: Spacing.lg,
  },
  title: {
    color: Palette.text,
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: Spacing.sm,
    textAlign: 'center',
  },
  subtitle: {
    color: Palette.textMuted,
    fontSize: 16,
    lineHeight: 24,
    marginBottom: Spacing.xl,
    textAlign: 'center',
  },
  input: {
    backgroundColor: Palette.card,
    height: 56,
    borderRadius: 12,
    paddingHorizontal: 16,
    color: Palette.text,
    marginBottom: Spacing.md,
    borderWidth: 1,
    borderColor: Palette.border,
    fontSize: 16,
  },
  primaryBtn: {
    backgroundColor: Palette.primary,
    height: 56,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: Spacing.md,
  },
  btnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
});
