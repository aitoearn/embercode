/** PhoneCode stub：语音非本阶段验收项，提供空实现避免 Metro 解析失败。 */
const noop = () => undefined;

/** 与 expo-modules-core PermissionResponse 形状对齐的拒绝态占位。 */
const deniedPermission = async () => ({
  granted: false,
  status: 'denied',
  expires: 'never',
  canAskAgain: false,
});

/**
 * 对齐真实 @getpaseo/expo-two-way-audio 的扁平 CJS 导出。
 * 消费方见 packages/app/src/voice/audio-engine.native.ts
 */
function addExpoTwoWayAudioEventListener(_eventName, _handler) {
  return {
    remove() {},
  };
}

module.exports = {
  addExpoTwoWayAudioEventListener,
  initialize: async () => true,
  getMicrophonePermissionsAsync: deniedPermission,
  requestMicrophonePermissionsAsync: deniedPermission,
  toggleRecording: () => false,
  playPCMData: noop,
  resumePlayback: noop,
  stopPlayback: noop,
  tearDown: noop,
};
