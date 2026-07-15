/**
 * PhoneCode 嵌入式 RN：禁用 Expo 开发启动器相关原生模块的 RN CLI autolinking，
 * 避免 Debug 下劫持 RemoteRnActivity。
 */
module.exports = {
  project: {
    android: {
      // 本仓库无 android/ 子目录，Gradle 根即仓库根；显式声明包名供 RNGP 使用。
      sourceDir: '.',
      packageName: 'dev.phonecode.app',
    },
  },
  dependencies: {
    'expo-dev-client': {platforms: {android: null, ios: null}},
    'expo-dev-launcher': {platforms: {android: null, ios: null}},
    'expo-dev-menu': {platforms: {android: null, ios: null}},
    'expo-updates': {platforms: {android: null, ios: null}},
  },
};
