import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { X } from 'lucide-react-native';
import { Palette, Spacing } from '../theme/Theme';

interface TermsOfServiceScreenProps {
  onClose: () => void;
}

export function TermsOfServiceScreen({ onClose }: TermsOfServiceScreenProps) {
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Terms of Service</Text>
        <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
          <X color={Palette.text} size={24} />
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.content} contentContainerStyle={styles.scrollContent}>
        <Text style={styles.lastUpdated}>Last Updated: February 9, 2026</Text>

        <Text style={styles.sectionTitle}>1. Acceptance of Terms</Text>
        <Text style={styles.paragraph}>
          By accessing or using StreamWatch ("the App"), you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the App.
        </Text>

        <Text style={styles.sectionTitle}>2. Description of Service</Text>
        <Text style={styles.paragraph}>
          StreamWatch is a mobile application that allows users to organize, monitor, and access live streaming content from various platforms. The App provides tools to manage favorite streams and receive notifications.
        </Text>

        <Text style={styles.sectionTitle}>3. User Accounts</Text>
        <Text style={styles.paragraph}>
          To use certain features, you must create an account. You agree to:
        </Text>
        <Text style={styles.bullet}>• Provide accurate and complete information</Text>
        <Text style={styles.bullet}>• Maintain the security of your password</Text>
        <Text style={styles.bullet}>• Notify us immediately of any unauthorized access</Text>
        <Text style={styles.bullet}>• Be responsible for all activities under your account</Text>

        <Text style={styles.sectionTitle}>4. Acceptable Use</Text>
        <Text style={styles.paragraph}>
          You agree not to:
        </Text>
        <Text style={styles.bullet}>• Use the App for any illegal purpose</Text>
        <Text style={styles.bullet}>• Attempt to gain unauthorized access to our systems</Text>
        <Text style={styles.bullet}>• Interfere with or disrupt the App's functionality</Text>
        <Text style={styles.bullet}>• Upload malicious code or viruses</Text>
        <Text style={styles.bullet}>• Harass, abuse, or harm other users</Text>
        <Text style={styles.bullet}>• Impersonate any person or entity</Text>

        <Text style={styles.sectionTitle}>5. Content and Intellectual Property</Text>
        <Text style={styles.paragraph}>
          The App and its original content, features, and functionality are owned by StreamWatch and are protected by international copyright, trademark, and other intellectual property laws.
        </Text>
        <Text style={styles.paragraph}>
          Stream content accessed through the App is owned by respective content creators and platforms. We do not claim ownership of third-party content.
        </Text>

        <Text style={styles.sectionTitle}>6. Third-Party Services</Text>
        <Text style={styles.paragraph}>
          The App integrates with third-party streaming platforms. Your use of these platforms is subject to their respective terms of service. We are not responsible for the availability, content, or policies of third-party services.
        </Text>

        <Text style={styles.sectionTitle}>7. Disclaimer of Warranties</Text>
        <Text style={styles.paragraph}>
          THE APP IS PROVIDED "AS IS" WITHOUT WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED. WE DO NOT GUARANTEE THAT THE APP WILL BE UNINTERRUPTED, SECURE, OR ERROR-FREE.
        </Text>

        <Text style={styles.sectionTitle}>8. Limitation of Liability</Text>
        <Text style={styles.paragraph}>
          TO THE MAXIMUM EXTENT PERMITTED BY LAW, STREAMWATCH SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES ARISING FROM YOUR USE OF THE APP.
        </Text>

        <Text style={styles.sectionTitle}>9. Termination</Text>
        <Text style={styles.paragraph}>
          We reserve the right to suspend or terminate your account at any time for violations of these Terms. You may delete your account at any time through the App settings.
        </Text>

        <Text style={styles.sectionTitle}>10. Changes to Terms</Text>
        <Text style={styles.paragraph}>
          We may modify these Terms at any time. Continued use of the App after changes constitutes acceptance of the modified Terms.
        </Text>

        <Text style={styles.sectionTitle}>11. Governing Law</Text>
        <Text style={styles.paragraph}>
          These Terms shall be governed by and construed in accordance with applicable laws, without regard to conflict of law provisions.
        </Text>

        <Text style={styles.sectionTitle}>12. Contact Information</Text>
        <Text style={styles.paragraph}>
          For questions about these Terms, contact us at:
        </Text>
        <Text style={styles.contact}>streamwatch@snowballons.com</Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: Spacing.lg,
    paddingTop: 60,
    paddingBottom: Spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: Palette.border,
  },
  title: {
    color: Palette.text,
    fontSize: 24,
    fontWeight: 'bold',
  },
  closeBtn: {
    padding: 8,
  },
  content: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.lg,
    paddingBottom: 40,
  },
  lastUpdated: {
    color: Palette.textMuted,
    fontSize: 12,
    marginBottom: Spacing.lg,
    fontStyle: 'italic',
  },
  sectionTitle: {
    color: Palette.text,
    fontSize: 18,
    fontWeight: 'bold',
    marginTop: Spacing.lg,
    marginBottom: Spacing.sm,
  },
  paragraph: {
    color: Palette.text,
    fontSize: 14,
    lineHeight: 22,
    marginBottom: Spacing.sm,
  },
  bullet: {
    color: Palette.text,
    fontSize: 14,
    lineHeight: 22,
    marginBottom: 4,
    paddingLeft: Spacing.md,
  },
  contact: {
    color: Palette.primary,
    fontSize: 14,
    marginTop: 4,
  },
});
