#!/usr/bin/env bash
# 兼容旧名：转发到 sync-remote-ui.sh
exec "$(cd "$(dirname "$0")" && pwd)/sync-remote-ui.sh" "$@"
