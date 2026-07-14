const path = require('path');
const {getDefaultConfig} = require('expo/metro-config');
const {mergeConfig} = require('@react-native/metro-config');

const projectRoot = __dirname;
const remoteUiRoot = path.resolve(projectRoot, 'packages/remote-ui');

/**
 * Metro：工程根即 Android Gradle 根；监视 @phonecode/remote-ui。
 */
const config = {
  watchFolders: [projectRoot, remoteUiRoot],
  resolver: {
    nodeModulesPaths: [path.resolve(projectRoot, 'node_modules')],
    extraNodeModules: {
      '@phonecode/remote-ui': remoteUiRoot,
    },
  },
};

module.exports = mergeConfig(getDefaultConfig(projectRoot), config);
