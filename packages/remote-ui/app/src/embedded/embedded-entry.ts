/**
 * PhoneCode embedded 入口：由 Intent extras 解析初始路由。
 */

export type EmbeddedExtras = {
  route?: string;
  hostId?: string;
  agentId?: string;
  embedded?: boolean;
};

export function hrefFromEmbeddedExtras(extras: EmbeddedExtras): string | null {
  if (!extras.embedded) return null;
  switch (extras.route) {
    case 'pair':
      return '/pair-scan?source=phonecode';
    case 'host':
      return extras.hostId
        ? `/h/${extras.hostId}/sessions`
        : '/pair-scan?source=phonecode';
    case 'chat':
      return extras.hostId && extras.agentId
        ? `/h/${extras.hostId}/agent/${extras.agentId}`
        : extras.hostId
          ? `/h/${extras.hostId}/sessions`
          : '/pair-scan?source=phonecode';
    default:
      return '/pair-scan?source=phonecode';
  }
}
