import React, { useState } from 'react';
import { StyleSheet, Text, View, TouchableOpacity, TextInput, ActivityIndicator, Alert, KeyboardAvoidingView, Platform, Image, Modal } from 'react-native';
import { LogIn } from 'lucide-react-native';
import { supabase } from '../../lib/supabase';
import { Palette, Spacing } from '../theme/Theme';
import { PrivacyPolicyScreen } from './PrivacyPolicyScreen';
import { TermsOfServiceScreen } from './TermsOfServiceScreen';

export function AuthScreen({ navigation }: any) {
  const [view, setView] = useState<'initial' | 'login' | 'signup'>('initial');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [username, setUsername] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPrivacy, setShowPrivacy] = useState(false);
  const [showTerms, setShowTerms] = useState(false);
  
  const emailInputRef = React.useRef<TextInput>(null);
  const passwordInputRef = React.useRef<TextInput>(null);

  const handleSignIn = async () => {
    if (!email || !password) return Alert.alert("Required", "Please fill in all fields.");
    setLoading(true);
    
    const { error } = await supabase.auth.signInWithPassword({ 
      email: email.trim(), 
      password 
    });
    
    if (error) Alert.alert("Error", error.message);
    setLoading(false);
  };

  const handleSignUp = async () => {
    if (!email || !password || !username) return Alert.alert("Required", "Please fill in all fields.");
    if (username.length < 3) return Alert.alert("Error", "Username must be at least 3 characters.");
    
    setLoading(true);
    const { error } = await supabase.auth.signUp({ 
      email, 
      password,
      options: {
        data: { username }
      }
    });
    if (error) Alert.alert("Error", error.message);
    else Alert.alert("Success", "Account created! Confirm email address and sign in.");
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
          
          {view === 'signup' && (
            <TextInput 
              style={styles.input} 
              placeholder="Username" 
              placeholderTextColor="#666" 
              value={username}
              onChangeText={setUsername}
              autoCapitalize="none"
              autoFocus
              returnKeyType="next"
              onSubmitEditing={() => emailInputRef.current?.focus()}
            />
          )}
          
          <TextInput 
            ref={view === 'login' ? emailInputRef : undefined}
            style={styles.input} 
            placeholder="Email" 
            placeholderTextColor="#666" 
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
            autoFocus={view === 'login'}
            returnKeyType="next"
            onSubmitEditing={() => passwordInputRef.current?.focus()}
          />
          <TextInput 
            ref={passwordInputRef}
            style={styles.input} 
            placeholder="Password" 
            placeholderTextColor="#666" 
            secureTextEntry 
            value={password}
            onChangeText={setPassword}
            returnKeyType="done"
            onSubmitEditing={view === 'login' ? handleSignIn : handleSignUp}
          />
          
          {view === 'login' && (
            <TouchableOpacity onPress={() => navigation.navigate('ForgotPassword')} style={styles.forgotLink}>
              <Text style={styles.forgotText}>Forgot Password?</Text>
            </TouchableOpacity>
          )}
          
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

      <View style={styles.footerContainer}>
        <Text style={styles.footerText}>By continuing, you agree to StreamWatch's </Text>
        <TouchableOpacity onPress={() => setShowTerms(true)}>
          <Text style={styles.link}>Terms of Service</Text>
        </TouchableOpacity>
        <Text style={styles.footerText}> and </Text>
        <TouchableOpacity onPress={() => setShowPrivacy(true)}>
          <Text style={styles.link}>Privacy Policy</Text>
        </TouchableOpacity>
        <Text style={styles.footerText}>.</Text>
      </View>

      {/* Privacy Policy Modal */}
      <Modal
        visible={showPrivacy}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => setShowPrivacy(false)}
      >
        <PrivacyPolicyScreen onClose={() => setShowPrivacy(false)} />
      </Modal>

      {/* Terms of Service Modal */}
      <Modal
        visible={showTerms}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => setShowTerms(false)}
      >
        <TermsOfServiceScreen onClose={() => setShowTerms(false)} />
      </Modal>
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
  card: { width: '100%', backgroundColor: 'rgba(255,255,255,0.03)', borderRadius: 28, padding: 24, borderWidth: 1, borderColor: 'rgba(255,255,255,0.08)', marginBottom: 20 },
  primaryBtn: { backgroundColor: Palette.primary, height: 56, borderRadius: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', marginBottom: 12 },
  secondaryBtn: { backgroundColor: Palette.secondary, height: 56, borderRadius: 16, alignItems: 'center', justifyContent: 'center', marginBottom: 24 },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  footerContainer: { position: 'absolute', bottom: 40, flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', paddingHorizontal: 40 },
  footerText: { color: 'rgba(255,255,255,0.2)', fontSize: 10, textAlign: 'center' },
  link: { color: Palette.accent, fontSize: 10, textDecorationLine: 'underline' },
  backBtn: { marginBottom: 20, alignSelf: 'flex-start' },
  backText: { color: Palette.text, fontWeight: 'bold', fontSize: 16 },
  formContainer: { width: '100%', justifyContent: 'center' },
  title: { color: '#fff', fontSize: 32, fontWeight: 'bold', marginBottom: 30 },
  input: { backgroundColor: 'rgba(255,255,255,0.05)', height: 56, borderRadius: 12, paddingHorizontal: 16, color: '#fff', marginBottom: 12, borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
  forgotLink: { alignSelf: 'flex-end', marginBottom: 20 },
  forgotText: { color: Palette.accent, fontSize: 14, fontWeight: '600' }
});