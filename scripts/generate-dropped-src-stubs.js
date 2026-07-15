/**
 * 根据 remote-ui 源码中对已同步删除目录的 import，生成 PhoneCode mobile stub。
 * 用法：node scripts/generate-dropped-src-stubs.js
 */
const fs = require('fs');
const path = require('path');
const {execSync} = require('child_process');

const wt = path.resolve(__dirname, '..');
const paseoSrc = process.env.PASEO_APP_SRC ||
  '/Users/lisq/ai/coding/paseo/packages/app/src';
const stubRoot = path.join(wt, 'packages/remote-ui/shims/app-src');
const appSrc = path.join(wt, 'packages/remote-ui/app/src');

const out = execSync(
  `rg -oN --no-filename '@/desktop/[^"'\\'' ]+|@/schedules/[^"'\\'' ]+|@/browser/[^"'\\'' ]+|@/browser-automation/[^"'\\'' ]+|@/fdroid/[^"'\\'' ]+' "${appSrc}" --glob '*.{ts,tsx}'`,
  {encoding: 'utf8'},
);

const mods = [
  ...new Set(
    out
      .split(/\n/)
      .map((s) => s.trim())
      .filter(Boolean)
      .map((s) => s.replace(/^@\//, '')),
  ),
].sort();

function findSource(rel) {
  for (const ext of ['.ts', '.tsx', '.js', '.jsx']) {
    const p = path.join(paseoSrc, rel + ext);
    if (fs.existsSync(p)) {
      return p;
    }
  }
  for (const ext of ['.ts', '.tsx']) {
    const p = path.join(paseoSrc, rel, 'index' + ext);
    if (fs.existsSync(p)) {
      return p;
    }
  }
  return null;
}

function parseExports(srcPath) {
  const text = fs.readFileSync(srcPath, 'utf8');
  const names = new Set();
  const types = new Set();
  let hasDefault = false;
  for (const m of text.matchAll(/export\s+(?:async\s+)?function\s+(\w+)/g)) {
    names.add(m[1]);
  }
  for (const m of text.matchAll(/export\s+const\s+(\w+)/g)) {
    names.add(m[1]);
  }
  for (const m of text.matchAll(/export\s+class\s+(\w+)/g)) {
    names.add(m[1]);
  }
  for (const m of text.matchAll(/export\s+type\s+(\w+)/g)) {
    types.add(m[1]);
  }
  for (const m of text.matchAll(/export\s+interface\s+(\w+)/g)) {
    types.add(m[1]);
  }
  for (const m of text.matchAll(/export\s+enum\s+(\w+)/g)) {
    names.add(m[1]);
  }
  for (const m of text.matchAll(/export\s+\{\s*([^}]+)\s*\}/g)) {
    for (const part of m[1].split(',')) {
      const cleaned = part.trim();
      if (!cleaned) {
        continue;
      }
      if (cleaned.startsWith('type ')) {
        types.add(
          cleaned
            .replace(/^type\s+/, '')
            .split(/\s+as\s+/)[0]
            .trim(),
        );
        continue;
      }
      const name = cleaned.split(/\s+as\s+/).pop().trim();
      if (name) {
        names.add(name);
      }
    }
  }
  if (/export\s+default\s+/.test(text)) {
    hasDefault = true;
  }
  return {
    names: [...names],
    types: [...types],
    hasDefault,
    isTsx: srcPath.endsWith('.tsx'),
    text,
  };
}

function looksLikeComponent(name, rel, isTsx) {
  return (
    isTsx ||
    rel.includes('/components/') ||
    /Section|Modal|Source|Screen|Button|Callout/.test(name) ||
    (/^[A-Z]/.test(name) && !/^(DEFAULT_|[A-Z0-9_]+$)/.test(name))
  );
}

function stubBody(info, rel) {
  const lines = [];
  lines.push(
    '/** PhoneCode mobile stub：桌面/排程等非远程关键路径占位，避免 Metro 解析失败。 */',
  );
  lines.push("import React from 'react';");
  lines.push('');
  for (const t of info.types) {
    lines.push(`export type ${t} = any;`);
  }
  if (info.types.length) {
    lines.push('');
  }

  for (const name of info.names) {
    if (name.startsWith('DEFAULT_') || /^[A-Z0-9_]+$/.test(name)) {
      lines.push(`export const ${name}: any = {};`);
      continue;
    }
    if (/^use[A-Z]/.test(name)) {
      lines.push(
        `export function ${name}(..._args: any[]): any { return null; }`,
      );
      continue;
    }
    if (looksLikeComponent(name, rel, info.isTsx)) {
      lines.push(
        `export function ${name}(_props: any = {}): React.ReactElement | null { return null; }`,
      );
      continue;
    }
    if (name.startsWith('should') || name.startsWith('is')) {
      lines.push(
        `export function ${name}(..._args: any[]): boolean { return false; }`,
      );
      continue;
    }
    if (name.startsWith('create') && name.includes('Factory')) {
      lines.push(
        `export function ${name}(..._args: any[]): null { return null; }`,
      );
      continue;
    }
    if (name.startsWith('get') && name.includes('Host')) {
      lines.push(
        `export function ${name}(..._args: any[]): null { return null; }`,
      );
      continue;
    }
    if (name.startsWith('listen')) {
      lines.push(
        `export async function ${name}(..._args: any[]): Promise<() => void> { return () => {}; }`,
      );
      continue;
    }
    lines.push(`export async function ${name}(..._args: any[]): Promise<any> {`);
    lines.push(
      `  throw new Error('[PhoneCode stub] ${rel}#${name} 仅桌面可用');`,
    );
    lines.push('}');
  }

  if (info.hasDefault) {
    lines.push(
      'export default function StubDefault(): null { return null; }',
    );
  }

  if (info.names.length === 0 && !info.hasDefault) {
    lines.push('export {};');
  }
  return lines.join('\n') + '\n';
}

const overrides = {
  'desktop/daemon/desktop-daemon': `/** PhoneCode mobile stub */
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
`,
  'desktop/daemon/desktop-daemon-transport': `/** PhoneCode mobile stub */
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
`,
  'desktop/host': `/** PhoneCode mobile stub */
export type DesktopHostBridge = any;
export type DesktopNotificationPermission = 'granted' | 'denied' | 'default';
export function getDesktopHost(): null {
  return null;
}
export function isElectronRuntime(): boolean {
  return false;
}
export function isElectronRuntimeMac(): boolean {
  return false;
}
`,
};

fs.mkdirSync(stubRoot, {recursive: true});

let created = 0;
for (const rel of mods) {
  const preferTsx =
    rel.includes('/components/') ||
    /Callout|Section|Modal|Source/.test(rel);
  const dest = path.join(stubRoot, rel + (preferTsx ? '.tsx' : '.ts'));
  fs.mkdirSync(path.dirname(dest), {recursive: true});

  if (overrides[rel]) {
    fs.writeFileSync(dest.replace(/\.tsx$/, '.ts'), overrides[rel]);
    const tsxDup = dest.endsWith('.tsx') ? dest : null;
    if (tsxDup && fs.existsSync(tsxDup)) {
      fs.unlinkSync(tsxDup);
    }
    created += 1;
    continue;
  }

  const src = findSource(rel);
  const info = src
    ? parseExports(src)
    : {names: [], types: [], hasDefault: true, isTsx: true, text: ''};
  const outPath =
    info.isTsx || preferTsx
      ? path.join(stubRoot, rel + '.tsx')
      : path.join(stubRoot, rel + '.ts');
  fs.writeFileSync(outPath, stubBody(info, rel));
  created += 1;
}

console.log(`已生成 ${created} 个 stub → ${stubRoot}`);
console.log(mods.join('\n'));
