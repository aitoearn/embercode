/**
 * PhoneCode embedded 配对页：扫码读 #offer=，不依赖全量 host-runtime / unistyles。
 * 完整 connectToDaemon 接入前，先验证相机与 offer 解析。
 */
import React, {useCallback, useEffect, useRef, useState} from 'react';
import {
  Alert,
  NativeModules,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {CameraView, useCameraPermissions, type BarcodeScanningResult} from 'expo-camera';
import {useSafeAreaInsets} from 'react-native-safe-area-context';

type Props = {
  onBack?: () => void;
};

type ParsedOffer = {
  serverId: string;
  relayEndpoint: string;
};

export function EmbeddedPairScanScreen({onBack}: Props) {
  const insets = useSafeAreaInsets();
  const [permission, requestPermission] = useCameraPermissions();
  const [isPairing, setIsPairing] = useState(false);
  const [lastOffer, setLastOffer] = useState<ParsedOffer | null>(null);
  const lastScannedRef = useRef<string | null>(null);

  useEffect(() => {
    if (permission?.granted) return;
    void requestPermission().catch(() => undefined);
  }, [permission, requestPermission]);

  const finishStub = useCallback((offer: ParsedOffer) => {
    const payload = {
      hosts: [
        {
          hostId: offer.serverId,
          hostLabel: offer.relayEndpoint,
          connectionState: 'Disconnected',
          sessions: [],
        },
      ],
    };
    const bridge = NativeModules.PhoneCodeBridge as
      | {finishWithSummaries?: (json: string) => void}
      | undefined;
    if (bridge?.finishWithSummaries) {
      bridge.finishWithSummaries(JSON.stringify(payload));
      return;
    }
    onBack?.();
  }, [onBack]);

  const handleScan = useCallback(
    async (result: BarcodeScanningResult) => {
      if (isPairing) return;
      const offerUrl = extractOfferUrlFromScan(result);
      if (!offerUrl) return;
      if (lastScannedRef.current === offerUrl) return;
      lastScannedRef.current = offerUrl;

      try {
        setIsPairing(true);
        const offer = parseOfferFromUrl(offerUrl);
        setLastOffer(offer);
        Alert.alert(
          '已识别配对码',
          `主机 ${offer.serverId}\n中继 ${offer.relayEndpoint}\n\n下一步将接入 daemon 连接；当前先写回摘要并关闭。`,
          [
            {text: '取消', style: 'cancel', onPress: () => { lastScannedRef.current = null; }},
            {text: '完成', onPress: () => finishStub(offer)},
          ],
        );
      } catch (error) {
        lastScannedRef.current = null;
        const message = error instanceof Error ? error.message : '无法解析配对码';
        Alert.alert('配对失败', message);
      } finally {
        setIsPairing(false);
      }
    },
    [finishStub, isPairing],
  );

  const granted = Boolean(permission?.granted);

  return (
    <View style={[styles.root, {paddingTop: insets.top, paddingBottom: insets.bottom}]}>
      <View style={styles.header}>
        <Pressable onPress={onBack} hitSlop={12}>
          <Text style={styles.back}>返回</Text>
        </Pressable>
        <Text style={styles.title}>远程配对</Text>
        <View style={styles.headerSpacer} />
      </View>

      <View style={styles.body}>
        {!granted ? (
          <View style={styles.card}>
            <Text style={styles.cardTitle}>需要相机权限</Text>
            <Text style={styles.cardBody}>扫描 paseo 主机上的配对二维码以连接远程 daemon。</Text>
            <Pressable style={styles.button} onPress={() => void requestPermission()}>
              <Text style={styles.buttonText}>授权相机</Text>
            </Pressable>
          </View>
        ) : (
          <View style={styles.cameraWrap}>
            <CameraView
              style={StyleSheet.absoluteFill}
              facing="back"
              barcodeScannerSettings={{barcodeTypes: ['qr']}}
              onBarcodeScanned={handleScan}
            />
            <View style={styles.overlay} pointerEvents="none">
              <View style={styles.frame} />
              <Text style={styles.hint}>
                {isPairing ? '正在解析…' : lastOffer ? `已识别 ${lastOffer.serverId}` : '对准含 #offer= 的二维码'}
              </Text>
            </View>
          </View>
        )}
      </View>
    </View>
  );
}

function extractOfferUrlFromScan(result: BarcodeScanningResult): string | null {
  const raw = typeof result.data === 'string' ? result.data.trim() : '';
  if (!raw) return null;
  if (raw.includes('#offer=')) return raw;
  return null;
}

function parseOfferFromUrl(offerUrl: string): ParsedOffer {
  const idx = offerUrl.indexOf('#offer=');
  if (idx < 0) throw new Error('缺少 #offer= 片段');
  const encoded = offerUrl.slice(idx + '#offer='.length).trim();
  const json = decodeBase64UrlToUtf8(encoded);
  const payload = JSON.parse(json) as {
    serverId?: string;
    relay?: {endpoint?: string};
  };
  if (!payload.serverId || !payload.relay?.endpoint) {
    throw new Error('配对载荷缺少 serverId 或 relay.endpoint');
  }
  return {
    serverId: payload.serverId,
    relayEndpoint: payload.relay.endpoint,
  };
}

function decodeBase64UrlToUtf8(input: string): string {
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  const binary = globalThis.atob(padded);
  const bytes = Uint8Array.from(binary, char => char.charCodeAt(0));
  return new TextDecoder('utf-8', {fatal: true}).decode(bytes);
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#0B1220'},
  header: {
    height: 52,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  back: {color: '#8AB4F8', fontSize: 16},
  title: {color: '#F5F7FA', fontSize: 17, fontWeight: '600'},
  headerSpacer: {width: 40},
  body: {flex: 1, paddingHorizontal: 16, paddingBottom: 16},
  card: {
    marginTop: 24,
    padding: 20,
    borderRadius: 16,
    backgroundColor: '#152238',
    gap: 12,
  },
  cardTitle: {color: '#F5F7FA', fontSize: 18, fontWeight: '600'},
  cardBody: {color: '#A8B3C7', fontSize: 15, lineHeight: 22},
  button: {
    alignSelf: 'flex-start',
    backgroundColor: '#2A4A7A',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  buttonText: {color: '#F5F7FA', fontWeight: '600'},
  cameraWrap: {
    flex: 1,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: '#111827',
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
  },
  frame: {
    width: 240,
    height: 240,
    borderRadius: 16,
    borderWidth: 3,
    borderColor: '#5B8DEF',
  },
  hint: {
    color: '#F5F7FA',
    fontSize: 14,
    textAlign: 'center',
    paddingHorizontal: 24,
  },
});
