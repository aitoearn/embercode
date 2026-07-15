const path = require('path');
const fs = require('fs');
const {getDefaultConfig} = require('expo/metro-config');
const {mergeConfig} = require('@react-native/metro-config');

const projectRoot = __dirname;
const remoteUiRoot = path.resolve(projectRoot, 'packages/remote-ui');
const appSrc = path.resolve(remoteUiRoot, 'app/src');
/** 相对项目根；须与 app.json extra.router.root 一致 */
const routerRoot = 'packages/remote-ui/app/src/app';
const appRootRoutes = path.resolve(projectRoot, routerRoot);

/**
 * 排除 Android 工程产物/原生资源，避免 Metro 把
 * app/src/main/assets/mermaid.min.js 等浏览器脚本打进 RN bundle。
 */
const androidNativeBlockList = [
  new RegExp(
    `${path.resolve(projectRoot, 'app/src/main/assets').replace(/[/\\]/g, '[/\\\\]')}[/\\\\].*`,
  ),
  new RegExp(
    `${path.resolve(projectRoot, 'app/build').replace(/[/\\]/g, '[/\\\\]')}[/\\\\].*`,
  ),
  /[/\\]\.gradle[/\\].*/,
  /[/\\]build[/\\]intermediates[/\\].*/,
];

/**
 * RN /index.bundle 不会走 Expo 的 /.expo/.virtual-metro-entry 重写，
 * 导致 babel-preset-expo 拿不到 transform.routerRoot，回退到根目录 app/
 *（那是 Android Gradle 模块，没有路由）。强制注入正确路由根。
 */
function withRouterRootTransform(url) {
  if (!url.includes('.bundle')) {
    return url;
  }
  if (url.includes('transform.routerRoot=')) {
    return url;
  }
  const sep = url.includes('?') ? '&' : '?';
  return `${url}${sep}transform.routerRoot=${encodeURIComponent(routerRoot)}`;
}

const expoConfig = getDefaultConfig(projectRoot);
const previousRewrite = expoConfig.server?.rewriteRequestUrl;
const defaultResolveRequest = expoConfig.resolver?.resolveRequest;

const packageAliases = {
  '@phonecode/remote-ui': remoteUiRoot,
  // protocol 仅有 `./*` → dist/*，把 alias 指到 dist 才能解析子路径
  '@getpaseo/protocol': path.resolve(remoteUiRoot, 'protocol/dist'),
  '@getpaseo/client': path.resolve(remoteUiRoot, 'client/dist'),
  '@getpaseo/relay': path.resolve(remoteUiRoot, 'relay/dist'),
  '@getpaseo/highlight': path.resolve(remoteUiRoot, 'highlight/dist'),
  '@getpaseo/app': path.resolve(remoteUiRoot, 'app'),
  '@getpaseo/expo-two-way-audio': path.resolve(
    remoteUiRoot,
    'shims/expo-two-way-audio',
  ),
};

/** client package.json 里 internal 子路径与 dist 文件名不一致 */
const clientInternalExports = {
  'internal/daemon-client': 'daemon-client',
  'internal/daemon-client-transport-types': 'daemon-client-transport-types',
};

/** sync-remote-ui 会删除的目录；映射到 shims/app-src 下的 mobile stub */
const droppedSrcPrefixes = [
  'desktop/',
  'schedules/',
  'browser/',
  'browser-automation/',
  'fdroid/',
];
const droppedSrcStubRoot = path.resolve(remoteUiRoot, 'shims/app-src');

/**
 * Metro 不会把 `@/foo` 当成 tsconfig paths。
 * `@/` → remote-ui app/src；已删除的 desktop/schedules 等 → shims/app-src。
 * `@getpaseo/client/internal/*` 按 package exports 映到 dist 文件名。
 */
function resolveRequest(context, moduleName, platform) {
  const resolve =
    defaultResolveRequest ?? context.resolveRequest.bind(context);

  if (moduleName.startsWith('@/')) {
    const rel = moduleName.slice(2);
    const useStub = droppedSrcPrefixes.some((prefix) => rel.startsWith(prefix));
    const base = path.join(useStub ? droppedSrcStubRoot : appSrc, rel);
    // 若存在同名目录与 .ts/.tsx 文件（如 assets/acp-provider-icons），优先文件
    for (const ext of ['.ts', '.tsx', '.js', '.jsx']) {
      const asFile = base + ext;
      if (fs.existsSync(asFile) && fs.statSync(asFile).isFile()) {
        return resolve(context, asFile, platform);
      }
    }
    return resolve(context, base, platform);
  }

  if (moduleName.startsWith('@getpaseo/client/')) {
    const rest = moduleName.slice('@getpaseo/client/'.length);
    const distName = clientInternalExports[rest] ?? rest;
    return resolve(
      context,
      path.join(remoteUiRoot, 'client/dist', distName),
      platform,
    );
  }

  // relay 的 package exports 在 import 条件下指向 src/*.ts（含 .js 后缀 import），Metro 会挂
  if (moduleName === '@getpaseo/relay') {
    return resolve(context, path.join(remoteUiRoot, 'relay/dist/index.js'), platform);
  }
  if (moduleName.startsWith('@getpaseo/relay/')) {
    const rest = moduleName.slice('@getpaseo/relay/'.length);
    const relayExportToDist = {
      e2ee: 'e2ee.js',
      cloudflare: 'cloudflare-adapter.js',
    };
    const file = relayExportToDist[rest] ?? `${rest}.js`;
    return resolve(context, path.join(remoteUiRoot, 'relay/dist', file), platform);
  }

  if (moduleName === 'react-native-svg' || moduleName.startsWith('react-native-svg/')) {
    return resolve(
      context,
      path.join(projectRoot, 'node_modules', moduleName),
      platform,
    );
  }

  return resolve(context, moduleName, platform);
}

const config = {
  watchFolders: [
    projectRoot,
    remoteUiRoot,
    path.resolve(remoteUiRoot, 'app'),
    path.resolve(remoteUiRoot, 'client'),
    path.resolve(remoteUiRoot, 'protocol'),
    path.resolve(remoteUiRoot, 'relay'),
    path.resolve(remoteUiRoot, 'highlight'),
    droppedSrcStubRoot,
  ],
  server: {
    ...expoConfig.server,
    rewriteRequestUrl: (url) => {
      const rewritten = previousRewrite ? previousRewrite(url) : url;
      return withRouterRootTransform(rewritten);
    },
  },
  resolver: {
    blockList: androidNativeBlockList,
    nodeModulesPaths: [path.resolve(projectRoot, 'node_modules')],
    extraNodeModules: packageAliases,
    resolveRequest,
  },
};

// 保留 env 以便其它工具读取；真正生效靠 transform.routerRoot
process.env.EXPO_ROUTER_APP_ROOT = appRootRoutes;

module.exports = mergeConfig(expoConfig, config);
