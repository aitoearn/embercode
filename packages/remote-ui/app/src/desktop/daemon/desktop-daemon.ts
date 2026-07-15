/** PhoneCode mobile stub */
export type DesktopDaemonState = 'starting' | 'running' | 'stopped' | 'errored';
export type DesktopDaemonStatus = {
  serverId: string;
  status: DesktopDaemonState;
  listen: string | null;
  hostname: string | null;
  pid: number | null;
  home: string;
  version: string | null;
  desktopManaged: boolean;
  error: string | null;
};
export type DesktopDaemonLogs = { logPath: string; contents: string };
export type DesktopPairingOffer = {
  relayEnabled: boolean;
  url: string | null;
  qr: string | null;
};
export type LocalTransportTarget = {
  transportType: 'socket' | 'pipe';
  transportPath: string;
  [key: string]: unknown;
};
export type InstallStatus = { installed: boolean };
export type SkillsState = 'not-installed' | 'up-to-date' | 'drift';
export type SkillOp = { kind: 'add' | 'update' | 'delete'; name: string };
export type SkillsStatus = { state: SkillsState; ops: SkillOp[] };
export type LocalTransportEventUnlisten = () => void;
export type LocalTransportEventHandler = (payload: any) => void;

export function shouldUseDesktopDaemon(): boolean {
  return false;
}
async function unavailable(name: string): Promise<never> {
  throw new Error('[PhoneCode stub] desktop-daemon#' + name + ' 仅桌面可用');
}
export const getDesktopDaemonStatus = () => unavailable('getDesktopDaemonStatus');
export const startDesktopDaemon = () => unavailable('startDesktopDaemon');
export const stopDesktopDaemon = () => unavailable('stopDesktopDaemon');
export const restartDesktopDaemon = () => unavailable('restartDesktopDaemon');
export const getDesktopDaemonLogs = async (): Promise<DesktopDaemonLogs> => ({
  logPath: '',
  contents: '',
});
export const getDesktopDaemonPairing = () => unavailable('getDesktopDaemonPairing');
export const getCliDaemonStatus = async () => 'unavailable';
export const listenToLocalTransportEvents = async () => () => {};
export const openLocalTransportSession = () => unavailable('openLocalTransportSession');
export const sendLocalTransportMessage = async () => {};
export const closeLocalTransportSession = async () => {};
export const getCliInstallStatus = async (): Promise<InstallStatus> => ({
  installed: false,
});
export const installCli = () => unavailable('installCli');
export const getSkillsStatus = async (): Promise<SkillsStatus> => ({
  state: 'not-installed',
  ops: [],
});
export const installSkills = () => unavailable('installSkills');
export const updateSkills = () => unavailable('updateSkills');
export const uninstallSkills = () => unavailable('uninstallSkills');
