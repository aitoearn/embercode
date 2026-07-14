# @phonecode/remote-ui 同步清单

- 时间: 2026-07-14 17:40:12 +0800
- 源仓库: `/Users/lisq/ai/coding/ori_paseo/paseo`
- 分支: `feat/paseo-remote-rn`
- commit: `b4ab0d9db6e5668218e5aaa34f15ef3dd133e3ec`
- 目标: `packages/remote-ui`（跨平台共享包）
- 目标体积: 8.7M

## 包含

- `protocol` / `client` / `relay` / `highlight` / 精简 `app`

## 不包含

- paseo server、desktop、cli、website
- 各端原生工程（android/、ios/）

## 消费方

- Android：PhoneCode `app` 模块（Compose 壳）
- iOS（后续）：独立壳工程依赖本包，不依赖 Android 应用仓

重新同步:

```bash
PASEO_ROOT=/Users/lisq/ai/coding/ori_paseo/paseo ./scripts/sync-remote-ui.sh
```
