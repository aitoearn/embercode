import path from "node:path";
import { defineConfig } from "vitest/config";

/**
 * 精简版 vitest 配置：本次同步（SYNC_MANIFEST.md）未携带源仓库
 * `packages/app/vitest.config.ts`，导致 `describe`/`it`/`expect` 全局
 * 未注入，且 `import ... from 'react-native'` 在 Node 环境下因 Flow
 * 语法直接崩溃。这里只保留跑通单元测试所需的最小项：
 * - `globals: true`：与源仓库测试文件保持一致，无需逐文件 import vitest。
 * - `react-native` 别名指向 `react-native-web`：避免解析原生 Flow 源码。
 * - `@` 别名指向 `src`：与 `tsconfig.json` 的 `paths` 保持一致。
 */
export default defineConfig({
  test: {
    environment: "node",
    globals: true,
  },
  resolve: {
    alias: [
      {
        find: "react-native",
        replacement: path.resolve(
          __dirname,
          "../../../node_modules/react-native-web/dist/index.js",
        ),
      },
      {find: "@", replacement: path.resolve(__dirname, "src")},
    ],
  },
});
