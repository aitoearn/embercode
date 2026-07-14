# @phonecode/remote-ui

跨平台的 **远程编码 UI** 共享包（方案 A）。

- **内容**：从上游 paseo 白名单同步的 `protocol` / `client` / `relay` / `highlight` / 精简 `app` 页面
- **不含**：paseo server / desktop / website，也不含各端原生工程
- **消费者**：
  - Android：本仓库 `app` 模块（Compose 壳 + `RemoteRnActivity`）
  - iOS（后续）：独立壳工程通过 npm/path 依赖本包，嵌入 RN 根视图

## 同步上游

```bash
# 默认源：/Users/lisq/ai/coding/ori_paseo/paseo
./scripts/sync-remote-ui.sh

# 或
PASEO_ROOT=/path/to/paseo ./scripts/sync-remote-ui.sh
```

清单见 [`SYNC_MANIFEST.md`](./SYNC_MANIFEST.md)。

## 以后拆成独立仓库

本包可整体迁出为独立 git 仓；Android / iOS 改为依赖发布版或 submodule **仅本包**，无需依赖完整 paseo，也无需依赖 Android 应用仓。
