# 说明

整仓 paseo submodule 与根目录 `rn/` 已废弃。

跨平台远程 UI 共享包：

- [`packages/remote-ui`](../packages/remote-ui)（`@phonecode/remote-ui`）
- 同步脚本：[`scripts/sync-remote-ui.sh`](../scripts/sync-remote-ui.sh)

Android / 未来 iOS 都依赖该包，而不是互相依赖对方的应用工程。
