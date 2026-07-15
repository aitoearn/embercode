/** PhoneCode mobile stub */
import type { LocalTransportTarget } from './desktop-daemon';

export function buildLocalDaemonTransportUrl(target: LocalTransportTarget): string {
  return (
    'paseo+local://' +
    target.transportType +
    '?path=' +
    encodeURIComponent(target.transportPath)
  );
}

export function createDesktopLocalDaemonTransportFactory(): null {
  return null;
}
