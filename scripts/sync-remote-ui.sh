#!/usr/bin/env bash
# 从外部 paseo 按白名单同步到 packages/remote-ui（跨平台共享包 @phonecode/remote-ui）。
# 不拷贝 server / desktop / website / cli，也不生成 android/ ios/ 原生工程。
#
# 用法:
#   PASEO_ROOT=/path/to/paseo ./scripts/sync-remote-ui.sh
# 默认 PASEO_ROOT=/Users/lisq/ai/coding/ori_paseo/paseo

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASEO_ROOT="${PASEO_ROOT:-/Users/lisq/ai/coding/ori_paseo/paseo}"
DEST="${ROOT}/packages/remote-ui"
MANIFEST="${DEST}/SYNC_MANIFEST.md"

if [[ ! -d "${PASEO_ROOT}/packages" ]]; then
  echo "错误: 找不到 paseo 源码目录: ${PASEO_ROOT}" >&2
  echo "请设置 PASEO_ROOT 指向含 packages/ 的 paseo 仓库。" >&2
  exit 1
fi

if ! command -v rsync >/dev/null 2>&1; then
  echo "错误: 需要 rsync" >&2
  exit 1
fi

mkdir -p "${DEST}"

echo "==> 源: ${PASEO_ROOT}"
echo "==> 目标: ${DEST}（@phonecode/remote-ui）"

# 只清同步产物，保留本包元数据
rm -rf \
  "${DEST}/protocol" \
  "${DEST}/client" \
  "${DEST}/relay" \
  "${DEST}/highlight" \
  "${DEST}/app"

rsync_pkg() {
  local name="$1"
  local src="${PASEO_ROOT}/packages/${name}/"
  local dst="${DEST}/${name}/"
  if [[ ! -d "${src}" ]]; then
    echo "错误: 缺少包 ${src}" >&2
    exit 1
  fi
  mkdir -p "${dst}"
  rsync -a \
    --exclude node_modules/ \
    --exclude dist/ \
    --exclude build/ \
    --exclude .expo/ \
    --exclude android/ \
    --exclude ios/ \
    --exclude coverage/ \
    --exclude '*.tsbuildinfo' \
    "${src}" "${dst}"
  echo "  已同步 packages/${name} -> packages/remote-ui/${name}"
}

rsync_pkg protocol
rsync_pkg client
rsync_pkg relay
rsync_pkg highlight

APP_SRC="${PASEO_ROOT}/packages/app"
APP_DST="${DEST}/app"
mkdir -p "${APP_DST}/src"

for f in package.json index.ts tsconfig.json app.config.js babel.config.js \
         metro.config.cjs global.d.ts react-native-web.d.ts; do
  if [[ -f "${APP_SRC}/${f}" ]]; then
    cp "${APP_SRC}/${f}" "${APP_DST}/${f}"
  fi
done

if [[ -d "${APP_SRC}/assets" ]]; then
  rsync -a "${APP_SRC}/assets/" "${APP_DST}/assets/"
fi

APP_PATHS=(
  "src/app/_layout.tsx"
  "src/app/index.tsx"
  "src/app/pair-scan.tsx"
  "src/app/welcome.tsx"
  "src/app/sessions.tsx"
  "src/app/new.tsx"
  "src/app/h"
  "src/embedded"
  "src/runtime"
  "src/hosts"
  "src/components/hosts"
  "src/components/welcome-screen.tsx"
  "src/composer"
  "src/stores"
  "src/contexts"
  "src/navigation"
  "src/hooks"
  "src/lib"
  "src/utils"
  "src/i18n"
  "src/styles"
  "src/constants"
  "src/data"
  "src/polyfills"
  "src/native"
  "src/agent-stream"
  "src/screens"
  "src/panels"
  "src/mobile-panels"
  "src/attachments"
  "src/git"
  "src/projects"
  "src/provider-selection"
  "src/provider-usage"
  "src/review"
  "src/subagents"
  "src/client-slash-commands"
  "src/create-agent-preferences"
  "src/assistant-file-links"
  "src/keyboard"
  "src/dictation"
  "src/diagnostics"
  "src/components"
)

for rel in "${APP_PATHS[@]}"; do
  src="${APP_SRC}/${rel}"
  dst="${APP_DST}/${rel}"
  if [[ -d "${src}" ]]; then
    mkdir -p "${dst}"
    rsync -a \
      --exclude '*.test.ts' \
      --exclude '*.test.tsx' \
      --exclude '*.spec.ts' \
      --exclude '*.spec.tsx' \
      --exclude '__tests__/' \
      "${src}/" "${dst}/"
    echo "  已同步 app/${rel}/"
  elif [[ -f "${src}" ]]; then
    mkdir -p "$(dirname "${dst}")"
    cp "${src}" "${dst}"
    echo "  已同步 app/${rel}"
  else
    echo "  跳过（源不存在）: app/${rel}"
  fi
done

for drop in \
  "${APP_DST}/src/desktop" \
  "${APP_DST}/src/browser" \
  "${APP_DST}/src/browser-automation" \
  "${APP_DST}/src/fdroid" \
  "${APP_DST}/src/schedules" \
  "${APP_DST}/e2e" \
  "${APP_DST}/maestro" \
  "${APP_DST}/fastlane" \
  "${APP_DST}/android" \
  "${APP_DST}/ios"
do
  rm -rf "${drop}"
done

COMMIT="unknown"
BRANCH="unknown"
if [[ -d "${PASEO_ROOT}/.git" ]] || [[ -f "${PASEO_ROOT}/.git" ]]; then
  COMMIT="$(git -C "${PASEO_ROOT}" rev-parse HEAD 2>/dev/null || echo unknown)"
  BRANCH="$(git -C "${PASEO_ROOT}" branch --show-current 2>/dev/null || echo unknown)"
fi

SIZE="$(du -sh "${DEST}" | awk '{print $1}')"
cat > "${MANIFEST}" <<EOF
# @phonecode/remote-ui 同步清单

- 时间: $(date '+%Y-%m-%d %H:%M:%S %z')
- 源仓库: \`${PASEO_ROOT}\`
- 分支: \`${BRANCH}\`
- commit: \`${COMMIT}\`
- 目标: \`packages/remote-ui\`（跨平台共享包）
- 目标体积: ${SIZE}

## 包含

- \`protocol\` / \`client\` / \`relay\` / \`highlight\` / 精简 \`app\`

## 不包含

- paseo server、desktop、cli、website
- 各端原生工程（android/、ios/）

## 消费方

- Android：PhoneCode \`app\` 模块（Compose 壳）
- iOS（后续）：独立壳工程依赖本包，不依赖 Android 应用仓

重新同步:

\`\`\`bash
PASEO_ROOT=${PASEO_ROOT} ./scripts/sync-remote-ui.sh
\`\`\`
EOF

# 确保包 README / package.json 存在（首次或被误删时恢复）
if [[ ! -f "${DEST}/package.json" ]]; then
  cat > "${DEST}/package.json" <<'EOF'
{
  "name": "@phonecode/remote-ui",
  "version": "0.1.0",
  "private": true,
  "description": "PhoneCode 远程会话 UI（跨平台共享）",
  "workspaces": ["protocol", "client", "relay", "highlight", "app"]
}
EOF
fi

echo "==> 完成。体积: ${SIZE}"
echo "==> 清单: ${MANIFEST}"
