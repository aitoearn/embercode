/** PhoneCode mobile stub：桌面/排程等非远程关键路径占位，避免 Metro 解析失败。 */
import React from 'react';

export type DesktopSettings = any;
export type DesktopSettingsPatch = any;

export function useDesktopSettings(..._args: any[]): any { return null; }
export async function loadDesktopSettings(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] desktop/settings/desktop-settings#loadDesktopSettings 仅桌面可用');
}
export async function updatePersistedDesktopSettings(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] desktop/settings/desktop-settings#updatePersistedDesktopSettings 仅桌面可用');
}
export async function migrateLegacyDesktopSettings(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] desktop/settings/desktop-settings#migrateLegacyDesktopSettings 仅桌面可用');
}
export const DEFAULT_DESKTOP_SETTINGS: any = {};
