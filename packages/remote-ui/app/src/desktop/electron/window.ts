/** PhoneCode mobile stub：桌面/排程等非远程关键路径占位，避免 Metro 解析失败。 */
import React from 'react';

export async function getDesktopWindow(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] desktop/electron/window#getDesktopWindow 仅桌面可用');
}
export async function toggleDesktopMaximize(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] desktop/electron/window#toggleDesktopMaximize 仅桌面可用');
}
export function isDesktopFullscreen(..._args: any[]): boolean { return false; }
export async function updateDesktopWindowControls(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] desktop/electron/window#updateDesktopWindowControls 仅桌面可用');
}
