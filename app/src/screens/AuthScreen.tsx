import React, { useState } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, TextInput, ActivityIndicator, Alert, KeyboardAvoidingView, Platform, Image } from 'react-native';
// import { LinearGradient } from 'expo-linear-gradient';
import { LogIn, ArrowRight } from 'lucide-react-native';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';

// We define the gradient colors as a constant tuple to satisfy TypeScript
const GRADIENT_COLORS: [string, string, ...string[]] = ['#1A1A2E', '#0A0A0A'];

export function AuthScreen() {
  const [view, setView] = useState<'initial' | 'login' | 'signup'>('initial');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSignIn = async () => {
    if (!email || !password) return Alert.alert("Required", "Please fill in all fields.");
    setLoading(true);
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    if (error) Alert.alert("Error", error.message);
    setLoading(false);
  };

  const handleSignUp = async () => {
    if (!email || !password) return Alert.alert("Required", "Please fill in all fields.");
    setLoading(true);
    const { error } = await supabase.auth.signUp({ email, password });
    if (error) Alert.alert("Error", error.message);
    else Alert.alert("Success", "Check your email for the confirmation link!");
    setLoading(false);
  };

  const handleGuestEntry = async () => {
    setLoading(true);
    const { error } = await supabase.auth.signInAnonymously();
    if (error) Alert.alert("Error", error.message);
    setLoading(false);
  };

  if (view === 'login' || view === 'signup') {
    return (
      <View style={[styles.container, styles.gradientBg]}>
        <KeyboardAvoidingView 
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'} 
          style={styles.formContainer}
        >
          <TouchableOpacity style={styles.backBtn} onPress={() => setView('initial')}>
            <Text style={styles.backText}>← Back</Text>
          </TouchableOpacity>
          
          <Text style={styles.title}>{view === 'login' ? 'Welcome Back' : 'Join StreamWatch'}</Text>
          
          <TextInput 
            style={styles.input} 
            placeholder="Email" 
            placeholderTextColor="#666" 
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
          />
          <TextInput 
            style={styles.input} 
            placeholder="Password" 
            placeholderTextColor="#666" 
            secureTextEntry 
            value={password}
            onChangeText={setPassword}
          />
          
          <TouchableOpacity 
            style={styles.primaryBtn} 
            onPress={view === 'login' ? handleSignIn : handleSignUp}
            disabled={loading}
          >
            {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.btnText}>{view === 'login' ? 'Sign In' : 'Create Account'}</Text>}
          </TouchableOpacity>
        </KeyboardAvoidingView>
      </View>
    );
  }

  return (
    <View style={[styles.container, styles.gradientBg]}>
      {/* Logo Section */}
      <View style={styles.logoSection}>
        <View style={styles.logoContainer}>
            <Image 
              source={require('../../assets/logo.png')} 
              style={styles.logoImage}
              resizeMode="contain" 
            />
        </View>
        <Text style={styles.brandName}>StreamWatch</Text>
        <Text style={styles.tagline}>Monitor. Organize. <Text style={{color: Palette.accent}}>Stream.</Text></Text>
      </View>

      {/* Main Actions Card */}
      <View style={styles.card}>
        <TouchableOpacity style={styles.primaryBtn} onPress={() => setView('login')}>
          <LogIn color="#fff" size={20} style={{marginRight: 10}} />
          <Text style={styles.btnText}>Sign In</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.secondaryBtn} onPress={() => setView('signup')}>
          <Text style={styles.btnText}>Create Account</Text>
        </TouchableOpacity>
      </View>

      {/* Guest Entry */}
      <TouchableOpacity style={styles.guestLink} onPress={handleGuestEntry}>
         <Text style={styles.guestTextMuted}>Just looking? </Text>
         <Text style={styles.guestTextAction}>Continue as Guest <ArrowRight color={Palette.accent} size={14} /></Text>
      </TouchableOpacity>

      <Text style={styles.footerText}>
        By continuing, you agree to StreamWatch's Terms of Service and Privacy Policy.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: Spacing.lg },
  gradientBg: { backgroundColor: '#0A0A0A' }, // Temporary solid color replacement
  logoSection: { 
    alignItems: 'center', 
    marginBottom: 40 
  },
  logoContainer: { 
    width: 100, 
    height: 100, 
    marginBottom: 10, 
    justifyContent: 'center', 
    alignItems: 'center' 
  },
  logoImage: { 
    width: '100%', 
    height: '100%' 
  },
  brandName: { color: '#fff', fontSize: 42, fontWeight: '900', letterSpacing: -1 },
  tagline: { color: Palette.textMuted, fontSize: 18, fontWeight: '500', marginTop: 4 },
  card: { width: '100%', backgroundColor: 'rgba(255,255,255,0.03)', borderRadius: 28, padding: 24, borderWidth: 1, borderColor: 'rgba(255,255,255,0.08)' },
  primaryBtn: { backgroundColor: Palette.primary, height: 56, borderRadius: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginBottom: 12 },
  secondaryBtn: { backgroundColor: Palette.secondary, height: 56, borderRadius: 16, alignItems: 'center', justifyContent: 'center', marginBottom: 24 },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  dividerContainer: { flexDirection: 'row', alignItems: 'center', marginBottom: 24 },
  dividerLine: { flex: 1, height: 1, backgroundColor: 'rgba(255,255,255,0.1)' },
  dividerText: { color: 'rgba(255,255,255,0.3)', fontSize: 11, fontWeight: '700', marginHorizontal: 15 },
  socialRow: { flexDirection: 'row', gap: 12 },
  socialBtn: { flex: 1, height: 48, backgroundColor: 'rgba(255,255,255,0.05)', borderRadius: 12, justifyContent: 'center', alignItems: 'center', borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  socialBtnText: { color: '#fff', fontSize: 14, fontWeight: '600' },
  guestLink: { marginTop: 40, flexDirection: 'row', alignItems: 'center' },
  guestTextMuted: { color: Palette.textMuted, fontSize: 15 },
  guestTextAction: { color: Palette.accent, fontSize: 15, fontWeight: '700' },
  footerText: { position: 'absolute', bottom: 40, color: 'rgba(255,255,255,0.2)', fontSize: 10, textAlign: 'center', paddingHorizontal: 40 },
  backBtn: { marginBottom: 20, alignSelf: 'flex-start' },
  backText: { color: Palette.text, fontWeight: 'bold', fontSize: 16 },
  formContainer: { width: '100%', justifyContent: 'center' },
  title: { color: '#fff', fontSize: 32, fontWeight: 'bold', marginBottom: 30 },
  input: { backgroundColor: 'rgba(255,255,255,0.05)', height: 56, borderRadius: 12, paddingHorizontal: 16, color: '#fff', marginBottom: 12, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' }
});