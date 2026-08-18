import { CameraView, useCameraPermissions } from 'expo-camera';
import { X } from 'lucide-react-native';
import { useRef, useState } from 'react';
import { ActivityIndicator, Modal, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Palette, Spacing } from '../theme/Theme';

interface QrScannerModalProps {
  visible: boolean;
  onClose: () => void;
  onScanned: (data: string) => void;
}

export function QrScannerModal({ visible, onClose, onScanned }: QrScannerModalProps) {
  const [permission, requestPermission] = useCameraPermissions();
  const [processing, setProcessing] = useState(false);
  const handledRef = useRef(false);

  const handleBarcodeScanned = ({ data }: { data: string }) => {
    if (handledRef.current) return;
    handledRef.current = true;
    setProcessing(true);
    onScanned(data);
  };

  const resetState = () => {
    handledRef.current = false;
    setProcessing(false);
  };

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.title}>Scan Backend QR</Text>
          <TouchableOpacity onPress={onClose} style={styles.closeButton}>
            <X color={Palette.text} size={22} />
          </TouchableOpacity>
        </View>
        <Text style={styles.subtitle}>
          Point the camera at the QR code printed by your self-hosted backend.
        </Text>

        <View style={styles.cameraArea}>
          {permission?.granted ? (
            <CameraView
              style={StyleSheet.absoluteFill}
              facing="back"
              barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
              onBarcodeScanned={handledRef.current ? undefined : handleBarcodeScanned}
            />
          ) : permission?.canAskAgain ? (
            <View style={styles.center}>
              <Text style={styles.subtitle}>Camera permission is required to scan a QR code.</Text>
              <TouchableOpacity style={styles.primaryButton} onPress={requestPermission}>
                <Text style={styles.primaryButtonText}>Grant Permission</Text>
              </TouchableOpacity>
            </View>
          ) : (
            <View style={styles.center}>
              <Text style={styles.subtitle}>
                Camera access is disabled. Enable it in your device settings.
              </Text>
              <TouchableOpacity style={styles.primaryButton} onPress={requestPermission}>
                <Text style={styles.primaryButtonText}>Open Settings</Text>
              </TouchableOpacity>
            </View>
          )}

          {processing && (
            <View style={styles.overlay}>
              <ActivityIndicator size="large" color={Palette.primary} />
              <Text style={styles.overlayText}>Connecting to backend...</Text>
            </View>
          )}
        </View>

        <TouchableOpacity
          style={styles.cancelButton}
          onPress={() => {
            resetState();
            onClose();
          }}
        >
          <Text style={styles.cancelText}>Cancel</Text>
        </TouchableOpacity>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Palette.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingTop: 60,
    paddingHorizontal: Spacing.lg,
  },
  title: {
    color: Palette.text,
    fontSize: 24,
    fontWeight: 'bold',
  },
  closeButton: {
    padding: Spacing.sm,
  },
  subtitle: {
    color: Palette.textMuted,
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
    marginTop: Spacing.sm,
    marginBottom: Spacing.lg,
    paddingHorizontal: Spacing.lg,
  },
  cameraArea: {
    flex: 1,
    marginHorizontal: Spacing.lg,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: '#000',
  },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: Spacing.lg,
    gap: Spacing.md,
  },
  primaryButton: {
    backgroundColor: Palette.primary,
    borderRadius: 12,
    paddingVertical: Spacing.md,
    paddingHorizontal: Spacing.lg,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  overlayText: {
    color: '#fff',
    marginTop: Spacing.md,
    fontSize: 16,
  },
  cancelButton: {
    alignItems: 'center',
    paddingVertical: Spacing.lg,
  },
  cancelText: {
    color: Palette.textMuted,
    fontSize: 16,
  },
});
