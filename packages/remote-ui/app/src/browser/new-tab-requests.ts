/** PhoneCode mobile stub：桌面/排程等非远程关键路径占位，避免 Metro 解析失败。 */
import React from 'react';

export type BrowserNewTabRequest = any;

export async function resolveBrowserNewTabRequest(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] browser/new-tab-requests#resolveBrowserNewTabRequest 仅桌面可用');
}
export function useDesktopBrowserNewTabRequests(..._args: any[]): any { return null; }
