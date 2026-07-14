/**
 * 远程会话摘要导出：供 PhoneCode Compose 抽屉列表缓存。
 */

export type SummaryPayload = {
  hosts: Array<{
    hostId: string;
    hostLabel: string;
    connectionState: 'Connected' | 'Disconnected' | 'Unknown';
    sessions: Array<{
      id: string;
      title: string;
      updatedAt: number;
      preview: string;
    }>;
  }>;
};

export function exportSessionSummaries(input: {
  hosts: Array<{id: string; label: string; connected: boolean}>;
  agentsByHost: Record<
    string,
    Array<{id: string; title?: string; updatedAt?: number; preview?: string}>
  >;
}): SummaryPayload {
  return {
    hosts: input.hosts.map(h => ({
      hostId: h.id,
      hostLabel: h.label,
      connectionState: h.connected ? 'Connected' : 'Disconnected',
      sessions: (input.agentsByHost[h.id] ?? []).map(a => ({
        id: a.id,
        title: a.title ?? a.id,
        updatedAt: a.updatedAt ?? 0,
        preview: a.preview ?? '',
      })),
    })),
  };
}
