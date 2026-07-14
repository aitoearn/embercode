/** PhoneCode stub：语音非本阶段验收项，提供空实现避免 Metro 解析失败。 */
const noop = async () => undefined;
module.exports = {
  ExpoTwoWayAudio: {
    startRecording: noop,
    stopRecording: noop,
    playPCMData: noop,
    tearDown: noop,
  },
  default: {
    startRecording: noop,
    stopRecording: noop,
    playPCMData: noop,
    tearDown: noop,
  },
};
