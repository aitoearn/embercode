# PhoneCode remote-ui 全量挂载 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `RemoteRnActivity` 直接跑 `@getpaseo/app` 的 expo-router + host-runtime 全量树，实现配对 → 主机 → 远程聊天，并经 Bridge 回写 Compose 摘要。

**Architecture:** 根工程 npm workspaces 挂 `packages/remote-ui/{app,client,protocol,relay,highlight}`；Metro/Babel 解析 `@getpaseo/*` 与 `@/`；`index.js` 改走 app 的 `expo-router/entry`；boot 时读 `PhoneCodeBridge` extras 做 `router.replace`；Activity 返回时用 `session-summary-export` 导出摘要。语音等非关键原生可 stub。

**Tech Stack:** Expo 54 / RN 0.81.5 / Gradle S2；expo-router、unistyles、i18n、zustand、host-runtime、`@getpaseo/client|protocol|relay`；既有 `PhoneCodeBridge` / `RemoteSummaryCache`。

**Spec:** [`docs/superpowers/specs/2026-07-15-paseo-remote-ui-full-mount-design.md`](../specs/2026-07-15-paseo-remote-ui-full-mount-design.md)

---

## 文件结构（锁定）

| 路径 | 职责 |
|------|------|
| `package.json` | workspaces + 提升 app 依赖；scripts：`build:remote-ui-deps` |
| `metro.config.js` | watchFolders、`@getpaseo/*`、`@/`、可选 `EXPO_ROUTER_APP_ROOT` |
| `babel.config.js` | unistyles plugin（root 指向 remote-ui app src）+ reanimated |
| `index.js` | 转调 `packages/remote-ui/app/index.ts`（或等价 polyfill + expo-router/entry） |
| `app.json` | expo-router / scheme；标记 embedded |
| `App.tsx` | 不再作为默认根（可删或留注释指向全量入口） |
| `packages/remote-ui/app/src/embedded/phonecode-boot.ts` | 读 Bridge extras → href |
| `packages/remote-ui/app/src/embedded/phonecode-finish.ts` | 从 host-runtime 采摘要 → `finishWithSummaries` |
| `packages/remote-ui/app/src/app/_layout.tsx` | boot replace + Android 返回钩子（最小侵入） |
| `packages/remote-ui/shims/expo-two-way-audio/` | 语音 stub（若暂不同步真模块） |
| `scripts/sync-remote-ui.sh` | 可选同步 `expo-two-way-audio` |
| `scripts/build-remote-ui-deps.sh` | 构建 protocol/relay/client/highlight dist |
| `app/.../RemoteRnActivity.kt` | `getMainComponentName()` → `"main"`（expo-router 默认） |

---

### Task 1: 构建 `@getpaseo/*` dist + workspaces 接线

**Files:**
- Create: `scripts/build-remote-ui-deps.sh`
- Modify: `package.json`
- Modify: `packages/remote-ui/package.json`（如需）

- [ ] **Step 1: 添加构建脚本**

创建 `scripts/build-remote-ui-deps.sh`：

```bash
#!/usr/bin/env bash
# 构建 remote-ui 内 @getpaseo protocol/relay/client/highlight 的 dist（client 的 exports 指向 dist）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

build_one() {
  local dir="$1"
  echo "==> 构建 packages/remote-ui/$dir"
  npm run build --workspace="@getpaseo/$dir" 2>/dev/null \
    || (cd "packages/remote-ui/$dir" && npx tsc -p tsconfig.json --incremental false)
}

# protocol → relay → client → highlight（按依赖顺序）
build_one protocol
build_one relay
build_one client
build_one highlight
echo "remote-ui deps 构建完成"
```

`chmod +x scripts/build-remote-ui-deps.sh`

- [ ] **Step 2: 改根 `package.json`（workspaces + 核心依赖）**

在 `package.json` 增加（保留现有 scripts/deps，合并如下关键字段）：

```json
{
  "workspaces": [
    "packages/remote-ui/protocol",
    "packages/remote-ui/relay",
    "packages/remote-ui/client",
    "packages/remote-ui/highlight",
    "packages/remote-ui/app"
  ],
  "scripts": {
    "sync:remote-ui": "./scripts/sync-remote-ui.sh",
    "build:remote-ui-deps": "./scripts/build-remote-ui-deps.sh",
    "postinstall": "./scripts/patch-rn-gradle-plugin.sh && npm run build:remote-ui-deps",
    "start": "react-native start",
    "android": "react-native run-android"
  },
  "dependencies": {
    "@getpaseo/app": "*",
    "@getpaseo/client": "*",
    "@getpaseo/highlight": "*",
    "@getpaseo/protocol": "*",
    "@getpaseo/relay": "*",
    "@gorhom/bottom-sheet": "^5.2.14",
    "@gorhom/portal": "^1.0.14",
    "@react-native-async-storage/async-storage": "2.2.0",
    "@tanstack/react-query": "^5.90.11",
    "babel-preset-expo": "~54.0.10",
    "expo": "~54.0.18",
    "expo-camera": "~17.0.10",
    "expo-constants": "~18.0.13",
    "expo-linking": "~8.0.12",
    "expo-localization": "~17.0.9",
    "expo-router": "~6.0.13",
    "i18next": "^26.3.0",
    "react": "19.1.0",
    "react-i18next": "^17.0.8",
    "react-native": "0.81.5",
    "react-native-gesture-handler": "~2.28.0",
    "react-native-reanimated": "~4.1.1",
    "react-native-safe-area-context": "~5.6.0",
    "react-native-screens": "~4.16.0",
    "react-native-svg": "15.12.1",
    "react-native-unistyles": "^3.0.0",
    "react-native-worklets": "0.5.1",
    "zod": "^4.4.3",
    "zustand": "^5.0.9"
  }
}
```

说明：其余 `@getpaseo/app/package.json` 依赖在 Task 2 用「对照安装」补齐；本 Task 先让 workspaces 与 client dist 可用。  
**版本钉死：** `reanimated`/`worklets` 保持根上已验证的 `~4.1.1` / `0.5.1`，不要升到 app 包内的 `~4.3.1` / `~0.8.3`（会破坏 New Arch 已验证构建）。

- [ ] **Step 3: 安装并构建**

Run:

```bash
cd /Users/lisq/ai/android/phonecode
npm install
npm run build:remote-ui-deps
```

Expected: `packages/remote-ui/client/dist/daemon-client.js` 等存在；无 workspace 解析错误。

- [ ] **Step 4: Commit**

```bash
git add package.json package-lock.json scripts/build-remote-ui-deps.sh
git commit -m "$(cat <<'EOF'
build: 为 remote-ui 启用 workspaces 并构建 getpaseo dist

全量挂载前先让 @getpaseo/client 等 exports 可被 Metro 解析。
EOF
)"
```

---

### Task 2: 提升 app 运行时依赖 + 语音 stub

**Files:**
- Modify: `package.json`
- Create: `packages/remote-ui/shims/expo-two-way-audio/package.json`
- Create: `packages/remote-ui/shims/expo-two-way-audio/index.js`
- Create: `packages/remote-ui/shims/expo-two-way-audio/src/index.ts`（可选）
- Modify: `scripts/sync-remote-ui.sh`（仅注释说明 stub 优先；真模块后置）

- [ ] **Step 1: 对照 `packages/remote-ui/app/package.json` 把 Android 必需依赖写入根 `dependencies`**

至少包含（版本与 app 对齐，冲突时以根 S2 为准）：

- `@react-navigation/native`
- `expo-asset`、`expo-clipboard`、`expo-crypto`、`expo-document-picker`、`expo-file-system`、`expo-haptics`、`expo-image`、`expo-image-manipulator`、`expo-image-picker`、`expo-keep-awake`、`expo-notifications`、`expo-splash-screen`、`expo-system-ui`
- `react-native-keyboard-controller`、`react-native-webview`、`react-native-nitro-modules`
- `lucide-react-native`、`markdown-it`、`buffer`、`fast-deep-equal`、`mnemonic-id`、`tiny-invariant`、`use-sync-external-store`
- `@floating-ui/react-native`、`@react-native-masked-view/masked-view`

不要强装：`expo-dev-client`、`expo-updates`、web-only（`react-dom`/`react-native-web`）除非 Metro 强制要求。

Run: `npm install`

- [ ] **Step 2: 创建语音 stub 包**

`packages/remote-ui/shims/expo-two-way-audio/package.json`：

```json
{
  "name": "@getpaseo/expo-two-way-audio",
  "version": "0.0.0-phonecode-stub",
  "private": true,
  "main": "index.js",
  "react-native": "index.js"
}
```

`packages/remote-ui/shims/expo-two-way-audio/index.js`：

```js
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
```

根 `package.json` workspaces 增加：`"packages/remote-ui/shims/expo-two-way-audio"`，dependencies 增加 `"@getpaseo/expo-two-way-audio": "*"`。

Run: `npm install`

- [ ] **Step 3: Commit**

```bash
git add package.json package-lock.json packages/remote-ui/shims/expo-two-way-audio
git commit -m "$(cat <<'EOF'
build: 提升 remote-ui app 依赖并加入语音 stub

先打通全量挂载解析；语音原生模块后置，不挡文字聊天。
EOF
)"
```

---

### Task 3: Metro / Babel / app.json 解析全量树

**Files:**
- Modify: `metro.config.js`
- Modify: `babel.config.js`
- Modify: `app.json`

- [ ] **Step 1: 更新 `metro.config.js`**

```js
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
```

- [ ] **Step 2: 更新 `babel.config.js`**

```js
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
```

- [ ] **Step 3: 更新 `app.json`**

```json
{
  "name": "PhoneCode",
  "slug": "phonecode",
  "version": "0.3.0",
  "scheme": "phonecode",
  "android": {
    "package": "dev.phonecode"
  },
  "plugins": [
    "expo-router",
    [
      "expo-camera",
      {
        "cameraPermission": "允许 PhoneCode 使用相机扫描远程配对二维码。"
      }
    ]
  ],
  "extra": {
    "embeddedHost": "phonecode",
    "router": {
      "root": "./packages/remote-ui/app/src/app"
    }
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add metro.config.js babel.config.js app.json
git commit -m "$(cat <<'EOF'
chore: 配置 Metro/Babel 以解析全量 remote-ui 路由树

显式 EXPO_ROUTER_APP_ROOT 与 @getpaseo/@ 别名，挂载 unistyles 插件。
EOF
)"
```

---

### Task 4: JS 入口改为 expo-router + Activity 组件名 `main`

**Files:**
- Modify: `index.js`
- Modify: `app/src/main/kotlin/dev/phonecode/app/remote/RemoteRnActivity.kt`
- Modify or delete: `App.tsx`（停止作为根）

- [ ] **Step 1: 改写 `index.js`**

```js
/**
 * 入口须与 RemoteRnActivity.getMainComponentName() 一致。
 * expo-router/entry 注册组件名为 "main"。
 */
import 'packages/remote-ui/app/index.ts';
```

若 Metro 不接受该相对 import，改为：

```js
import './packages/remote-ui/app/index.ts';
```

确认 `packages/remote-ui/app/index.ts` 已包含 polyfill + unistyles + `expo-router/entry`（同步产物已有，勿改业务逻辑）。

- [ ] **Step 2: 修改 `RemoteRnActivity.kt`**

```kotlin
override fun getMainComponentName(): String = "main"
```

注释改为：加载 expo-router 注册的 `main`（全量 `@getpaseo/app`）。

- [ ] **Step 3: 处理 `App.tsx`**

将文件顶部改为废弃说明，或删除并由确认无引用：

```tsx
/**
 * @deprecated PhoneCode 远程根已改为 packages/remote-ui/app（expo-router）。
 * 保留本文件仅避免误引用；勿再 AppRegistry.registerComponent。
 */
export default function DeprecatedApp() {
  return null;
}
```

- [ ] **Step 4: 验证 Gradle 任务仍可配置**

Run:

```bash
cd /Users/lisq/ai/android/phonecode
./gradlew :app:tasks --group=build
```

Expected: 成功列出任务（证明 node/autolinking 仍可用）。此时尚不要求 `assembleDebug` 必过（依赖可能仍缺）。

- [ ] **Step 5: Commit**

```bash
git add index.js App.tsx app/src/main/kotlin/dev/phonecode/app/remote/RemoteRnActivity.kt
git commit -m "$(cat <<'EOF'
feat: RemoteRnActivity 改挂 expo-router main 入口

废弃平行 App.tsx 业务根，与 paseo app 入口对齐。
EOF
)"
```

---

### Task 5: Boot extras + 返回导出摘要（TDD）

**Files:**
- Create: `packages/remote-ui/app/src/embedded/phonecode-boot.ts`
- Create: `packages/remote-ui/app/src/embedded/phonecode-finish.ts`
- Modify: `packages/remote-ui/app/src/embedded/embedded-entry.test.ts`（或新建 `phonecode-boot.test.ts` / `phonecode-finish.test.ts`）
- Modify: `packages/remote-ui/app/src/app/_layout.tsx`（最小钩子）
- Modify: `packages/remote-ui/app/src/embedded/session-summary-export.ts`（若需适配字段）

- [ ] **Step 1: 写失败测试 — boot 从 Bridge 形状解析**

在 `packages/remote-ui/app/src/embedded/phonecode-boot.test.ts`：

```ts
import {resolvePhonecodeBootHref} from './phonecode-boot';

describe('resolvePhonecodeBootHref', () => {
  it('无 Bridge 时默认 pair', () => {
    expect(resolvePhonecodeBootHref(undefined)).toBe('/pair-scan?source=phonecode');
  });

  it('chat extras', () => {
    expect(
      resolvePhonecodeBootHref({
        embedded: true,
        route: 'chat',
        hostId: 'srv',
        agentId: 'ag',
      }),
    ).toBe('/h/srv/agent/ag');
  });
});
```

- [ ] **Step 2: 写失败测试 — finish 从主机快照生成 JSON**

在 `packages/remote-ui/app/src/embedded/phonecode-finish.test.ts`：

```ts
import {buildSummariesJsonFromHosts} from './phonecode-finish';

describe('buildSummariesJsonFromHosts', () => {
  it('导出 Connected 主机', () => {
    const json = buildSummariesJsonFromHosts({
      hosts: [
        {
          serverId: 'abc',
          label: 'desk',
          connected: true,
          agents: [{id: 'a1', title: 'Fix', updatedAt: 1, preview: 'hi'}],
        },
      ],
    });
    expect(JSON.parse(json)).toEqual({
      hosts: [
        {
          hostId: 'abc',
          hostLabel: 'desk',
          connectionState: 'Connected',
          sessions: [{id: 'a1', title: 'Fix', updatedAt: 1, preview: 'hi'}],
        },
      ],
    });
  });
});
```

- [ ] **Step 3: 跑测确认失败**

Run（若仓库尚无 vitest 根配置，在 `packages/remote-ui/app` 下用现有 vitest）：

```bash
cd /Users/lisq/ai/android/phonecode/packages/remote-ui/app
npx vitest run src/embedded/phonecode-boot.test.ts src/embedded/phonecode-finish.test.ts
```

Expected: FAIL（模块不存在）。

- [ ] **Step 4: 实现 `phonecode-boot.ts`**

```ts
import {NativeModules} from 'react-native';
import {hrefFromEmbeddedExtras, type EmbeddedExtras} from './embedded-entry';

export function readPhonecodeLaunchExtras(): EmbeddedExtras {
  const bridge = NativeModules.PhoneCodeBridge as
    | {getLaunchExtras?: () => EmbeddedExtras}
    | undefined;
  const fromBridge = bridge?.getLaunchExtras?.();
  if (fromBridge && typeof fromBridge === 'object') {
    return {...fromBridge, embedded: fromBridge.embedded ?? true};
  }
  return {embedded: true, route: 'pair'};
}

export function resolvePhonecodeBootHref(
  extras: EmbeddedExtras | undefined,
): string {
  const href = hrefFromEmbeddedExtras(extras ?? {embedded: true, route: 'pair'});
  return href ?? '/pair-scan?source=phonecode';
}
```

- [ ] **Step 5: 实现 `phonecode-finish.ts`**

```ts
import {NativeModules} from 'react-native';
import {exportSessionSummaries} from './session-summary-export';

export type HostSnapshotForSummary = {
  serverId: string;
  label: string;
  connected: boolean;
  agents: Array<{id: string; title?: string; updatedAt?: number; preview?: string}>;
};

export function buildSummariesJsonFromHosts(input: {
  hosts: HostSnapshotForSummary[];
}): string {
  const payload = exportSessionSummaries({
    hosts: input.hosts.map(h => ({
      id: h.serverId,
      label: h.label,
      connected: h.connected,
    })),
    agentsByHost: Object.fromEntries(
      input.hosts.map(h => [h.serverId, h.agents]),
    ),
  });
  return JSON.stringify(payload);
}

export function finishPhonecodeWithHostSnapshots(
  hosts: HostSnapshotForSummary[],
): void {
  const bridge = NativeModules.PhoneCodeBridge as
    | {finishWithSummaries?: (json: string) => void}
    | undefined;
  const json = buildSummariesJsonFromHosts({hosts});
  bridge?.finishWithSummaries?.(json);
}
```

另增运行时适配函数（可同文件）：从 `getHostRuntimeStore().getHosts()` + session store 采 `HostSnapshotForSummary[]`（字段以 `HostProfile.serverId` / `label` / 连接态为准；agent 列表若暂不可用则 `agents: []`）。

- [ ] **Step 6: 跑测通过**

Run: 同 Step 3。Expected: PASS。

- [ ] **Step 7: 在 `_layout.tsx` 挂最小 boot / 返回钩子**

在 `RootLayout`（或 `AppShell`）内增加仅 `source=phonecode` / `embedded` 时生效的 effect：

1. 首次 navigation ready 后：`router.replace(resolvePhonecodeBootHref(readPhonecodeLaunchExtras()) as Href)`
2. Android `BackHandler`：若栈只剩一层或用户离开 embedded，调用 `finishPhonecodeWithHostSnapshots(...)`（采当前 hosts）

保持改动局部，避免重写整棵 Provider 树。若 `_layout` 过大，可抽 `PhonecodeEmbeddedBridge.tsx` 组件挂在 `RootProviders` 内。

- [ ] **Step 8: Commit**

```bash
git add packages/remote-ui/app/src/embedded packages/remote-ui/app/src/app/_layout.tsx
# 若抽了 PhonecodeEmbeddedBridge.tsx 一并 add
git commit -m "$(cat <<'EOF'
feat: PhoneCode embedded boot 与返回摘要导出

Intent extras 跳转全量路由；返回时把 host-runtime 摘要回写 Compose。
EOF
)"
```

---

### Task 6: `assembleDebug` 门禁与缺依赖修通

**Files:**
- Modify: `package.json` / shims（按构建错误追加）
- Modify: `metro.config.js`（如需 stub 更多模块）
- Possibly: platform stubs under `packages/remote-ui/shims/`

- [ ] **Step 1: 停旧 daemon 后构建**

```bash
cd /Users/lisq/ai/android/phonecode
./gradlew --stop
./gradlew :app:assembleDebug
```

- [ ] **Step 2: 按错误迭代（记录在提交说明）**

常见处理：

| 错误 | 处理 |
|------|------|
| Unable to resolve module X | 根 `npm install X@版本` 或 shim |
| unistyles / reanimated babel | 确认 plugin 顺序（reanimated 最后） |
| duplicate class / AGP | 对齐已有 S2，勿升 Kotlin |
| expo-notifications 原生 | 保留 autolink；若失败可暂 exclude 并 stub JS |
| desktop-only import | 增加 `.native.ts` stub 或 Metro `resolveRequest` 拦截 |

每修通一类依赖可单独 commit：`fix: 补齐 xxx 以通过 assembleDebug`。

- [ ] **Step 3: 确认产物**

Expected: `BUILD SUCCESSFUL`；APK 位于 `app/build/outputs/apk/debug/`。

- [ ] **Step 4: Commit（若尚有未提交修复）**

```bash
git add -A
git status
git commit -m "$(cat <<'EOF'
fix: 修通全量 remote-ui 挂载后的 assembleDebug

按 Metro/Gradle 报错补齐依赖与 stub，保持 S2 工具链。
EOF
)"
```

---

### Task 7: 真机验收清单（手动）

**Files:** 无代码要求；结果可记入 `docs/` 本地笔记（docs 被 gitignore 时可只口头确认）。

- [ ] **Step 1: 安装 debug APK，确认 `phonecode.remoteRn=true`**

- [ ] **Step 2: 手测路径**

1. Compose 抽屉 →「连接远程」→ 出现 paseo `pair-scan`（非旧 EmbeddedPairScan）
2. 扫配对码 → `connectToDaemon` 成功 → 进入主机相关页
3. 打开 agent，发送一条文字消息
4. 系统返回 → Compose 远程分区有主机/会话摘要
5. 强杀进程 → 冷启动摘要仍在 → 再点远程可进 RN
6. （可选）`phonecode.remoteRn=false` 时无远程入口

- [ ] **Step 3: 验收通过后清理**

- 确认默认根已非 `EmbeddedPairScanScreen`
- 可将 `pair-scan-screen.tsx` 标 deprecated 或删除（单独 commit）

```bash
git commit -m "$(cat <<'EOF'
chore: 移除已废弃的 embedded 平行配对页

全量 remote-ui 挂载后不再维护双轨扫码 UI。
EOF
)"
```

---

## Spec 覆盖自检

| Spec 要求 | Task |
|-----------|------|
| 直接复用 expo-router / host-runtime / unistyles | 3–4 |
| workspaces + `@getpaseo/*` + Metro `@/` | 1–3 |
| 语音 stub / 私有依赖 | 2 |
| boot extras → replace | 5 |
| 返回导出摘要 / Bridge 契约 | 5 |
| assembleDebug | 6 |
| 配对→聊天→抽屉→冷启动 | 7 |
| flag 回滚 | 7（手测）；壳层已有 `RemoteFeature` |
| 废弃平行 embedded 默认根 | 4、7 |

---

## 执行交接

Plan 完成后请选择执行方式（见下一条消息）。
