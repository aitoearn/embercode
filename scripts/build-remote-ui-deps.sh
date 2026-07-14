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
