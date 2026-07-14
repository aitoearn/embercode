#!/usr/bin/env bash
# 历史：曾为 AGP 9 / Gradle 9.4 / Kotlin 2.3 修补 RNGP。
# 现已锁定 S2（AGP 8.11 / Gradle 8.14.3 / Kotlin 2.1.20），与 RN 0.81 默认一致，无需再补丁。
# 保留脚本以免 postinstall 断链；升级工具链时再决定是否恢复修补逻辑。
set -euo pipefail
exit 0
