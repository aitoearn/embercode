/** PhoneCode mobile stub：原生走 native-file-attachment-store，此模块不会被调用。 */
import type { AttachmentStore } from '@/attachments/types';

export type DesktopAttachmentBridge = any;

export function createDesktopAttachmentStore(
  _bridge: DesktopAttachmentBridge,
): AttachmentStore {
  throw new Error('[PhoneCode stub] createDesktopAttachmentStore 仅桌面可用');
}
