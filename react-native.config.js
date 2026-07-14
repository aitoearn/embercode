/**
 * Gradle 根即仓库根（无 android/ 子目录）。
 * @type {import('@react-native-community/cli-types').Config}
 */
module.exports = {
  project: {
    ios: {},
    android: {
      sourceDir: '.',
      appName: 'app',
      // 须与 android.namespace 一致（BuildConfig 生成包名），不是 applicationId
      packageName: 'dev.phonecode.app',
    },
  },
};
