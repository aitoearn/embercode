import React, {useMemo} from 'react';
import {SafeAreaView, StyleSheet, Text, View} from 'react-native';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {hrefFromEmbeddedExtras} from './packages/remote-ui/app/src/embedded/embedded-entry';
import {EmbeddedPairScanScreen} from './packages/remote-ui/app/src/embedded/pair-scan-screen';
import {NativeModules} from 'react-native';

type LaunchExtras = {
  route?: string;
  hostId?: string;
  agentId?: string;
  embedded?: boolean;
};

/**
 * 远程宿主根组件：按 Intent extras 进入 pair / host / chat。
 * pair：embedded 扫码页；其余路由暂显示占位。
 */
export default function App() {
  const extras = useMemo<LaunchExtras>(() => readLaunchExtras(), []);
  const href = useMemo(
    () =>
      hrefFromEmbeddedExtras({
        embedded: extras.embedded ?? true,
        route: extras.route,
        hostId: extras.hostId,
        agentId: extras.agentId,
      }),
    [extras],
  );

  const route = extras.route ?? 'pair';

  return (
    <SafeAreaProvider>
      {route === 'pair' ? (
        <EmbeddedPairScanScreen
          onBack={() => {
            const bridge = NativeModules.PhoneCodeBridge as
              | {finishWithSummaries?: (json: string) => void}
              | undefined;
            bridge?.finishWithSummaries?.('{"hosts":[]}');
          }}
        />
      ) : (
        <SafeAreaView style={styles.root}>
          <View style={styles.card}>
            <Text style={styles.title}>远程路由占位</Text>
            <Text style={styles.body}>route={route}</Text>
            <Text style={styles.meta}>href={href ?? '(无)'}</Text>
            <Text style={styles.meta}>{JSON.stringify(extras)}</Text>
          </View>
        </SafeAreaView>
      )}
    </SafeAreaProvider>
  );
}

function readLaunchExtras(): LaunchExtras {
  const bridge = NativeModules.PhoneCodeBridge as
    | {getLaunchExtras?: () => LaunchExtras}
    | undefined;
  const fromBridge = bridge?.getLaunchExtras?.();
  if (fromBridge && typeof fromBridge === 'object') {
    return {...fromBridge, embedded: fromBridge.embedded ?? true};
  }
  return {embedded: true, route: 'pair'};
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#0B1220',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    backgroundColor: '#152238',
    borderRadius: 12,
    padding: 20,
    gap: 12,
  },
  title: {color: '#F5F7FA', fontSize: 20, fontWeight: '600'},
  body: {color: '#A8B3C7', fontSize: 15, lineHeight: 22},
  meta: {color: '#7F8CA3', fontSize: 13, lineHeight: 18},
});
