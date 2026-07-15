/** PhoneCode mobile stub：桌面/排程等非远程关键路径占位，避免 Metro 解析失败。 */
import React from 'react';

export async function invokeDesktopCommand(..._args: any[]): Promise<any> {
  throw new Error('[PhoneCode stub] desktop/electron/invoke#invokeDesktopCommand 仅桌面可用');
}
