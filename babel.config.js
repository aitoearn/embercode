module.exports = function (api) {
  api.cache(true);
  return {
    presets: [
      [
        'babel-preset-expo',
        {unstable_transformImportMeta: true},
      ],
    ],
    plugins: [
      [
        'react-native-unistyles/plugin',
        {
          // 相对仓库根：unistyles 扫描 StyleSheet.create 的源码根
          root: 'packages/remote-ui/app/src',
        },
      ],
      'react-native-reanimated/plugin',
    ],
  };
};
