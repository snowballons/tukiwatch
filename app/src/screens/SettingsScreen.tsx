import { useState } from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { ProfileTab } from '../components/settings/SettingsProfileTab';
import { ConnectionTab, SystemTab } from '../components/settings/SettingsSupporterSystemTabs';
import { sharedSettingsStyles } from '../components/settings/SharedSettingsComponents';
import { Palette } from '../theme/Theme';

const TABS = ['Profile', 'Connection', 'System'] as const;
type Tab = (typeof TABS)[number];

export function SettingsScreen() {
  const [activeTab, setActiveTab] = useState<Tab>('Profile');

  return (
    <View style={styles.container}>
      <Text style={sharedSettingsStyles.headerTitle}>Settings</Text>
      <View style={sharedSettingsStyles.tabBar}>
        {TABS.map((tab) => {
          const active = activeTab === tab;
          return (
            <TouchableOpacity
              key={tab}
              style={sharedSettingsStyles.tab}
              onPress={() => setActiveTab(tab)}
              activeOpacity={0.8}
            >
              <Text
                style={[
                  sharedSettingsStyles.tabLabel,
                  active && sharedSettingsStyles.tabLabelActive,
                ]}
              >
                {tab}
              </Text>
              {active && <View style={sharedSettingsStyles.tabIndicator} />}
            </TouchableOpacity>
          );
        })}
      </View>
      <ScrollView style={sharedSettingsStyles.scroll} showsVerticalScrollIndicator={false}>
        {activeTab === 'Profile' && <ProfileTab />}
        {activeTab === 'Connection' && <ConnectionTab />}
        {activeTab === 'System' && <SystemTab />}
        <View style={sharedSettingsStyles.bottomSpacer} />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
});
