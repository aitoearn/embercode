/**
 * PhoneCode embedded 返回：把 host-runtime 当前的主机/会话快照
 * 序列化后回写给 Compose 壳的 `PhoneCodeBridge.finishWithSummaries`。
 *
 * 注意：本文件不直接依赖 `@/runtime/host-runtime`，
 * 从运行时 store 采集 `HostSnapshotForSummary[]` 的适配逻辑放在
 * `PhonecodeEmbeddedBridge.tsx` 里完成，再传入 `finishPhonecodeWithHostSnapshots`。
 * 这样本文件可保持纯函数、单测无需真实 React Native 环境。
 */
import {NativeModules} from 'react-native';
import {exportSessionSummaries} from './session-summary-export';

export type HostSnapshotForSummary = {
  serverId: string;
  label: string;
  connected: boolean;
  agents: Array<{id: string; title?: string; updatedAt?: number; preview?: string}>;
};

/** 把主机快照数组映射为 Compose 端可解析的摘要 JSON 字符串。 */
export function buildSummariesJsonFromHosts(input: {
  hosts: HostSnapshotForSummary[];
}): string {
  const payload = exportSessionSummaries({
    hosts: input.hosts.map(h => ({
      id: h.serverId,
      label: h.label,
      connected: h.connected,
    })),
    agentsByHost: Object.fromEntries(
      input.hosts.map(h => [h.serverId, h.agents]),
    ),
  });
  return JSON.stringify(payload);
}

/** 直接把主机快照数组交给 Bridge 完成 Activity 结束（Compose 侧读取摘要）。 */
export function finishPhonecodeWithHostSnapshots(
  hosts: HostSnapshotForSummary[],
): void {
  const bridge = NativeModules.PhoneCodeBridge as
    | {finishWithSummaries?: (json: string) => void}
    | undefined;
  const json = buildSummariesJsonFromHosts({hosts});
  bridge?.finishWithSummaries?.(json);
}
