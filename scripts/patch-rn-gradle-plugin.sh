#!/usr/bin/env bash
# 历史：曾为 AGP 9 / Gradle 9.4 / Kotlin 2.3 修补 RNGP。
# 现已锁定 S2（AGP 8.11 / Gradle 8.14.3 / Kotlin 2.1.20），与 RN 0.81 默认一致。
# 仍修补 Expo：IDE 精简 PATH 下需绝对 node 路径。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/patch-expo-node-binary.sh"
