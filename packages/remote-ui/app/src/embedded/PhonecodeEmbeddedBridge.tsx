/**
 * PhoneCode embedded 桥接组件：把「Intent extras → 首屏路由」与
 * 「Android 物理返回 → 摘要回写」这两个与 Compose 壳耦合的关注点，
 * 从 `_layout.tsx` 中拆出来单独维护，避免继续膨胀那个文件。
 *
 * 只在被 PhoneCode 壳以 embedded 方式启动时（extras.embedded === true）生效；
 * 非 embedded（例如独立 RN/Expo 调试）环境下渲染为空，不产生副作用。
 */
import {useEffect, useRef} from 'react';
import {BackHandler, Platform} from 'react-native';
import {useNavigationContainerRef, useRouter, type Href} from 'expo-router';
import {getHostRuntimeStore, isHostRuntimeConnected, useHosts} from '@/runtime/host-runtime';
import {readPhonecodeLaunchExtras, resolvePhonecodeBootHref} from './phonecode-boot';
import {finishPhonecodeWithHostSnapshots, type HostSnapshotForSummary} from './phonecode-finish';

/** 导航容器就绪前的轮询间隔；只在启动那一刻短暂生效。 */
const NAVIGATION_READY_POLL_MS = 50;

/** 采集当前 host-runtime 里的主机快照，供返回时回传摘要。 */
function collectCurrentHostSnapshots(
  hosts: ReturnType<typeof useHosts>,
): HostSnapshotForSummary[] {
  const store = getHostRuntimeStore();
  return hosts.map(host => ({
    serverId: host.serverId,
    label: host.label,
    connected: isHostRuntimeConnected(store.getSnapshot(host.serverId)),
    // agent/会话摘要目前无法从 store 同步、完整地取到 title/preview 等展示字段，
    // 先给空数组；后续若 host-runtime 暴露会话摘要读取接口再补齐。
    agents: [],
  }));
}

export function PhonecodeEmbeddedBridge() {
  const router = useRouter();
  const navigationRef = useNavigationContainerRef();
  const hosts = useHosts();
  const extrasRef = useRef(readPhonecodeLaunchExtras());
  const hasBootedRef = useRef(false);
  const isEmbedded = extrasRef.current.embedded === true;

  // 首次导航容器就绪后，按 Intent extras 跳一次目标路由。
  // `navigationRef` 是跨渲染稳定的全局 ref，就绪时机不会触发重渲染，
  // 所以用短轮询代替一次性的 effect 依赖判断（就绪后立刻清掉定时器）。
  useEffect(() => {
    if (!isEmbedded || hasBootedRef.current) return;

    const bootOnceReady = () => {
      if (hasBootedRef.current || !navigationRef.current?.isReady()) return;
      hasBootedRef.current = true;
      router.replace(resolvePhonecodeBootHref(extrasRef.current) as Href);
    };

    bootOnceReady();
    if (hasBootedRef.current) return;

    const pollHandle = setInterval(bootOnceReady, NAVIGATION_READY_POLL_MS);
    return () => clearInterval(pollHandle);
  }, [isEmbedded, navigationRef, router]);

  // Android 物理返回：栈内还能后退时交给系统默认行为；
  // 到栈底（即将离开应用）时改为把当前主机/会话摘要回写给 Compose 再结束。
  useEffect(() => {
    if (!isEmbedded || Platform.OS !== 'android') return;

    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (router.canGoBack()) {
        return false;
      }
      finishPhonecodeWithHostSnapshots(collectCurrentHostSnapshots(hosts));
      return true;
    });

    return () => subscription.remove();
  }, [hosts, isEmbedded, router]);

  return null;
}
