/** PhoneCode mobile stub：桌面更新相关；纯函数返回安全默认值，避免启动/开项目页崩。 */
export type DesktopReleaseChannel = 'stable' | 'beta';
export type DesktopAppUpdateCheckIntent = 'automatic' | 'manual';
export type DesktopAppUpdateCheckResult = Record<string, unknown>;
export type DesktopAppUpdateInstallResult = Record<string, unknown>;
export type DesktopRuntimeInfo = {version?: string | null; [key: string]: unknown};
export type LocalDaemonUpdateResult = {ok?: boolean; message?: string; [key: string]: unknown};
export type LocalDaemonVersionResult = {version?: string | null; [key: string]: unknown};

export function shouldShowDesktopUpdateSection(): boolean {
  return false;
}

export function parseLocalDaemonVersionResult(raw: unknown): LocalDaemonVersionResult {
  return typeof raw === 'object' && raw ? (raw as LocalDaemonVersionResult) : {version: null};
}

export async function getLocalDaemonVersion(): Promise<LocalDaemonVersionResult> {
  return {version: null};
}

export function parseDesktopRuntimeInfo(raw: unknown): DesktopRuntimeInfo {
  return typeof raw === 'object' && raw ? (raw as DesktopRuntimeInfo) : {};
}

export async function getDesktopRuntimeInfo(): Promise<DesktopRuntimeInfo> {
  return {};
}

export async function checkDesktopAppUpdate(_input?: unknown): Promise<DesktopAppUpdateCheckResult> {
  return {available: false};
}

export async function installDesktopAppUpdate(_input?: unknown): Promise<DesktopAppUpdateInstallResult> {
  return {ok: false};
}

export async function runLocalDaemonUpdate(): Promise<LocalDaemonUpdateResult> {
  return {ok: false, message: 'unavailable on PhoneCode'};
}

export function normalizeVersionForComparison(
  version: string | null | undefined,
): string | null {
  if (typeof version !== 'string') {
    return null;
  }
  const trimmed = version.trim();
  return trimmed.length > 0 ? trimmed.replace(/^v/i, '') : null;
}

export function isVersionMismatch(
  _a?: string | null,
  _b?: string | null,
): boolean {
  return false;
}

export function formatVersionWithPrefix(version: string | null | undefined): string {
  if (typeof version !== 'string' || version.trim().length === 0) {
    return '';
  }
  const trimmed = version.trim();
  return trimmed.startsWith('v') || trimmed.startsWith('V') ? trimmed : `v${trimmed}`;
}

export function buildMacAppleSiliconDownloadUrl(
  _version: string | null | undefined,
): string | null {
  return null;
}

export function buildDaemonUpdateDiagnostics(result: LocalDaemonUpdateResult): string {
  return result.message ?? 'unavailable';
}
