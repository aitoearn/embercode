const path = require('path');
const {getDefaultConfig} = require('expo/metro-config');
const {mergeConfig} = require('@react-native/metro-config');

const projectRoot = __dirname;
const remoteUiRoot = path.resolve(projectRoot, 'packages/remote-ui');
const appSrc = path.resolve(remoteUiRoot, 'app/src');
const appRootRoutes = path.resolve(appSrc, 'app');

// expo-router 在 monorepo 中显式指定路由目录
process.env.EXPO_ROUTER_APP_ROOT = appRootRoutes;

const config = {
  watchFolders: [
    projectRoot,
    remoteUiRoot,
    path.resolve(remoteUiRoot, 'app'),
    path.resolve(remoteUiRoot, 'client'),
    path.resolve(remoteUiRoot, 'protocol'),
    path.resolve(remoteUiRoot, 'relay'),
    path.resolve(remoteUiRoot, 'highlight'),
  ],
  resolver: {
    nodeModulesPaths: [path.resolve(projectRoot, 'node_modules')],
    extraNodeModules: {
      '@phonecode/remote-ui': remoteUiRoot,
      '@getpaseo/app': path.resolve(remoteUiRoot, 'app'),
      '@getpaseo/client': path.resolve(remoteUiRoot, 'client'),
      '@getpaseo/protocol': path.resolve(remoteUiRoot, 'protocol'),
      '@getpaseo/relay': path.resolve(remoteUiRoot, 'relay'),
      '@getpaseo/highlight': path.resolve(remoteUiRoot, 'highlight'),
      '@getpaseo/expo-two-way-audio': path.resolve(
        remoteUiRoot,
        'shims/expo-two-way-audio',
      ),
      '@': appSrc,
    },
  },
};

module.exports = mergeConfig(getDefaultConfig(projectRoot), config);
