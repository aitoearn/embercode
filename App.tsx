import React, {useEffect, useMemo, useState} from 'react';
import {
  NativeModules,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import {hrefFromEmbeddedExtras} from './packages/remote-ui/app/src/embedded/embedded-entry';

const PROBE_KEY = 'phonecode.remote.expo.probe';

type LaunchExtras = {
  route?: string;
  hostId?: string;
  agentId?: string;
  embedded?: boolean;
};

/**
 * 远程宿主页：验证 Expo modules + AsyncStorage，并解析 embedded 路由。
 * 全量 expo-router / @phonecode/remote-ui 页面在下一阶段挂载。
 */
export default function App() {
  const [probe, setProbe] = useState('…');
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

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await AsyncStorage.setItem(PROBE_KEY, 'ok');
        const value = await AsyncStorage.getItem(PROBE_KEY);
        if (!cancelled) {
          setProbe(value === 'ok' ? 'AsyncStorage 正常' : 'AsyncStorage 异常');
        }
      } catch (error) {
        if (!cancelled) {
          setProbe(`AsyncStorage 失败: ${String(error)}`);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.card}>
        <Text style={styles.title}>PhoneCode Remote</Text>
        <Text style={styles.body}>
          Expo {Constants.expoConfig?.version ?? Constants.nativeAppVersion ?? '?'} ·{' '}
          {probe}
        </Text>
        <Text style={styles.meta}>初始路由: {href ?? '(无)'}</Text>
        <Text style={styles.meta}>
          extras: {JSON.stringify(extras)}
        </Text>
        <Pressable
          style={styles.button}
          onPress={() => NativeModules.DevSettings?.reload?.()}>
          <Text style={styles.buttonText}>重新加载 JS</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}

function readLaunchExtras(): LaunchExtras {
  // Activity Intent extras 后续由 PhoneCodeBridge 注入；Spike 阶段允许空默认 pair
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
  title: {
    color: '#F5F7FA',
    fontSize: 20,
    fontWeight: '600',
  },
  body: {
    color: '#A8B3C7',
    fontSize: 15,
    lineHeight: 22,
  },
  meta: {
    color: '#7F8CA3',
    fontSize: 13,
    lineHeight: 18,
  },
  button: {
    marginTop: 8,
    alignSelf: 'flex-start',
    backgroundColor: '#2A4A7A',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  buttonText: {
    color: '#F5F7FA',
    fontSize: 14,
    fontWeight: '500',
  },
});
