import { X } from 'lucide-react-native';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Palette, Spacing } from '../theme/Theme';

interface PrivacyPolicyScreenProps {
  onClose: () => void;
}

export function PrivacyPolicyScreen({ onClose }: PrivacyPolicyScreenProps) {
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Privacy Policy</Text>
        <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
          <X color={Palette.text} size={24} />
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.content} contentContainerStyle={styles.scrollContent}>
        <Text style={styles.lastUpdated}>Last Updated: February 9, 2026</Text>

        <Text style={styles.sectionTitle}>1. Information We Collect</Text>
        <Text style={styles.paragraph}>
          We collect information you provide directly to us, including:
        </Text>
        <Text style={styles.bullet}>• Account information (username, email address, password)</Text>
        <Text style={styles.bullet}>• Stream preferences and favorites</Text>
        <Text style={styles.bullet}>• Usage data and app interactions</Text>

        <Text style={styles.sectionTitle}>2. How We Use Your Information</Text>
        <Text style={styles.paragraph}>We use the information we collect to:</Text>
        <Text style={styles.bullet}>• Provide, maintain, and improve our services</Text>
        <Text style={styles.bullet}>• Create and manage your account</Text>
        <Text style={styles.bullet}>• Send you technical notices and support messages</Text>
        <Text style={styles.bullet}>• Monitor and analyze trends and usage</Text>

        <Text style={styles.sectionTitle}>3. Information Sharing</Text>
        <Text style={styles.paragraph}>
          We do not sell, trade, or rent your personal information to third parties. We may share
          your information only in the following circumstances:
        </Text>
        <Text style={styles.bullet}>• With your consent</Text>
        <Text style={styles.bullet}>• To comply with legal obligations</Text>
        <Text style={styles.bullet}>• To protect our rights and prevent fraud</Text>

        <Text style={styles.sectionTitle}>4. Data Security</Text>
        <Text style={styles.paragraph}>
          We implement appropriate security measures to protect your personal information. However,
          no method of transmission over the internet is 100% secure, and we cannot guarantee
          absolute security.
        </Text>

        <Text style={styles.sectionTitle}>5. Third-Party Services</Text>
        <Text style={styles.paragraph}>
          Our app may contain links to third-party streaming platforms. We are not responsible for
          the privacy practices of these external sites. We encourage you to read their privacy
          policies.
        </Text>

        <Text style={styles.sectionTitle}>6. Data Retention</Text>
        <Text style={styles.paragraph}>
          We retain your information for as long as your account is active or as needed to provide
          you services. You may request deletion of your account at any time.
        </Text>

        <Text style={styles.sectionTitle}>7. Your Rights</Text>
        <Text style={styles.paragraph}>You have the right to:</Text>
        <Text style={styles.bullet}>• Access your personal information</Text>
        <Text style={styles.bullet}>• Correct inaccurate data</Text>
        <Text style={styles.bullet}>• Request deletion of your account</Text>
        <Text style={styles.bullet}>• Opt-out of communications</Text>

        <Text style={styles.sectionTitle}>8. Children's Privacy</Text>
        <Text style={styles.paragraph}>
          Our service is not intended for users under the age of 13. We do not knowingly collect
          personal information from children under 13.
        </Text>

        <Text style={styles.sectionTitle}>9. Changes to This Policy</Text>
        <Text style={styles.paragraph}>
          We may update this Privacy Policy from time to time. We will notify you of any changes by
          posting the new policy in the app.
        </Text>

        <Text style={styles.sectionTitle}>10. Contact Us</Text>
        <Text style={styles.paragraph}>
          If you have questions about this Privacy Policy, please contact us at:
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
