import { ArrowLeft, Mail } from 'lucide-react-native';
import { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';

export function ForgotPasswordScreen({ navigation }: any) {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [otp, setOtp] = useState('');
  const [otpSent, setOtpSent] = useState(false);

  const handleResetPassword = async () => {
    if (!email.trim()) {
      Alert.alert('Required', 'Please enter your email address.');
      return;
    }

    setLoading(true);
    const { error } = await supabase.auth.resetPasswordForEmail(email);
    setLoading(false);

    if (error) {
      Alert.alert('Error', error.message);
    } else {
      setOtpSent(true);
    }
  };

  const handleVerifyOtp = async () => {
    if (!otp.trim()) {
      Alert.alert('Required', 'Please enter the code from your email.');
      return;
    }
    setLoading(true);
    const { error } = await supabase.auth.verifyOtp({
      email: email.trim(),
      token: otp.trim(),
      type: 'recovery',
    });
    setLoading(false);
    if (error) {
      Alert.alert('Error', error.message);
    } else {
      navigation.navigate('ResetPassword');
    }
  };

  if (otpSent) {
    return (
      <View style={styles.container}>
        <View style={styles.successContainer}>
          <Mail color={Palette.primary} size={64} />
          <Text style={styles.successTitle}>Check Your Email</Text>
          <Text style={styles.successText}>Enter the 8-digit code sent to {email}</Text>
          <TextInput
            style={styles.otpInput}
            placeholder="00000000"
            placeholderTextColor={Palette.textMuted}
            value={otp}
            onChangeText={setOtp}
            keyboardType="number-pad"
            maxLength={8}
            autoFocus
            returnKeyType="done"
            onSubmitEditing={handleVerifyOtp}
          />
          <TouchableOpacity
            style={[styles.primaryBtn, { opacity: otp.trim().length === 8 ? 1 : 0.5 }]}
            onPress={handleVerifyOtp}
            disabled={loading || otp.trim().length !== 8}
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.btnText}>Verify Code</Text>
            )}
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.resendBtn}
            onPress={handleResetPassword}
            disabled={loading}
          >
            <Text style={styles.resendText}>Resend Code</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.content}
      >
        <TouchableOpacity style={styles.backBtn} onPress={() => navigation.goBack()}>
          <ArrowLeft color={Palette.text} size={24} />
        </TouchableOpacity>

        <Text style={styles.title}>Reset Password</Text>
        <Text style={styles.subtitle}>
          Enter your email address and we'll send you a code to reset your password.
        </Text>

        <TextInput
          style={styles.input}
          placeholder="Email"
          placeholderTextColor={Palette.textMuted}
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          keyboardType="email-address"
          autoFocus
          returnKeyType="send"
          onSubmitEditing={handleResetPassword}
        />

        <TouchableOpacity
          style={[styles.primaryBtn, { opacity: email.trim() ? 1 : 0.5 }]}
          onPress={handleResetPassword}
          disabled={loading || !email.trim()}
        >
          {loading ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.btnText}>Send Reset Code</Text>
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
    paddingTop: 60,
  },
  backBtn: {
    marginBottom: Spacing.lg,
  },
  title: {
    color: Palette.text,
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: Spacing.sm,
  },
  subtitle: {
    color: Palette.textMuted,
    fontSize: 16,
    lineHeight: 24,
    marginBottom: Spacing.xl,
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
  },
  btnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  successContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: Spacing.xl,
  },
  successTitle: {
    color: Palette.text,
    fontSize: 28,
    fontWeight: 'bold',
    marginTop: Spacing.lg,
    marginBottom: Spacing.sm,
  },
  successText: {
    color: Palette.textMuted,
    fontSize: 16,
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: Spacing.xl,
  },
  otpInput: {
    backgroundColor: Palette.card,
    height: 64,
    width: 200,
    borderRadius: 12,
    paddingHorizontal: 16,
    color: Palette.text,
    marginBottom: Spacing.md,
    borderWidth: 1,
    borderColor: Palette.border,
    fontSize: 32,
    fontWeight: 'bold',
    textAlign: 'center',
    letterSpacing: 8,
  },
  resendBtn: {
    marginTop: Spacing.lg,
    padding: 8,
  },
  resendText: {
    color: Palette.primary,
    fontSize: 14,
    fontWeight: '600',
  },
});
