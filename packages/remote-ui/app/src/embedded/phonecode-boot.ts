/**
 * PhoneCode embedded 启动：从 Compose 壳传入的 Intent extras 解析初始路由。
 */
import {NativeModules} from 'react-native';
import {hrefFromEmbeddedExtras, type EmbeddedExtras} from './embedded-entry';

/** 从 `PhoneCodeBridge` NativeModule 读取本次启动传入的 Intent extras。 */
export function readPhonecodeLaunchExtras(): EmbeddedExtras {
  const bridge = NativeModules.PhoneCodeBridge as
    | {getLaunchExtras?: () => EmbeddedExtras}
    | undefined;
  const fromBridge = bridge?.getLaunchExtras?.();
  if (fromBridge && typeof fromBridge === 'object') {
    return {...fromBridge, embedded: fromBridge.embedded ?? true};
  }
  // 无 Bridge（如非 embedded 环境）时，默认走配对页。
  return {embedded: true, route: 'pair'};
}

/** 把 extras 解析为 expo-router 的初始 href；无法识别时兜底跳配对页。 */
export function resolvePhonecodeBootHref(
  extras: EmbeddedExtras | undefined,
): string {
  const href = hrefFromEmbeddedExtras(extras ?? {embedded: true, route: 'pair'});
  return href ?? '/pair-scan?source=phonecode';
}
