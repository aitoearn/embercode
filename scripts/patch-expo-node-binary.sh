#!/usr/bin/env bash
# 让 Expo / RN 相关 Gradle 脚本使用绝对 node 路径（systemProp.nodejs.executable /
# 环境变量 NODE_BINARY），避免 Cursor/Android Studio 精简 PATH 下找不到 `node`。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

python3 - "$ROOT" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
NODE_EXPR = '(System.getProperty("nodejs.executable") ?: System.getenv("NODE_BINARY") ?: "node")'
NODE_GROOVY = '(System.getProperty("nodejs.executable") ?: System.getenv("NODE_BINARY") ?: "node")'
MARKER = "nodejs.executable"

targets = [
    root / "node_modules/expo-modules-autolinking/android/expo-gradle-plugin/expo-autolinking-plugin-shared/src/main/kotlin/expo/modules/plugin/AutolinkigCommandBuilder.kt",
    root / "node_modules/expo-modules-autolinking/android/expo-gradle-plugin/expo-autolinking-settings-plugin/src/main/kotlin/expo/modules/plugin/ExpoAutolinkingSettingsPlugin.kt",
    root / "node_modules/expo-modules-autolinking/android/expo-gradle-plugin/expo-autolinking-settings-plugin/src/main/kotlin/expo/modules/plugin/ExpoAutolinkingSettingsExtension.kt",
    root / "node_modules/expo-modules-core/expo-module-gradle-plugin/src/main/kotlin/expo/modules/plugin/gradle/ExpoGradleHelperExtension.kt",
    root / "node_modules/expo/scripts/autolinking.gradle",
    root / "node_modules/expo-constants/scripts/get-app-config-android.gradle",
    root / "node_modules/expo-modules-autolinking/scripts/android/autolinking_implementation.gradle",
    root / "node_modules/react-native-reanimated/android/build.gradle",
    root / "node_modules/react-native-worklets/android/build.gradle",
    root / "node_modules/react-native-svg/android/build.gradle",
    root / "node_modules/react-native-gesture-handler/android/build.gradle",
    root / "node_modules/react-native-screens/android/build.gradle",
]

def patch_text(path: Path, text: str) -> str:
    original = text
    # Kotlin/Groovy: commandLine("node" / commandLine('node'
    text = re.sub(
        r'commandLine\(\s*"node"',
        f"commandLine({NODE_EXPR}",
        text,
    )
    text = re.sub(
        r"commandLine\(\s*'node'",
        f"commandLine({NODE_EXPR}",
        text,
    )
    # Groovy list head: ["node",  or ['node',
    text = re.sub(
        r'\["node"',
        f"[{NODE_GROOVY}",
        text,
    )
    text = re.sub(
        r"\['node'",
        f"[{NODE_GROOVY}",
        text,
    )
    # Kotlin listOf( / listOf(\n    "node"
    text = re.sub(
        r'(listOf\(\s*)\n(\s*)"node"',
        rf'\1\n\2{NODE_EXPR}',
        text,
    )
    text = re.sub(
        r'listOf\(\s*"node"',
        f"listOf({NODE_EXPR}",
        text,
    )
    # Groovy String[] args = [ 'node',
    text = re.sub(
        r"(\[\s*)\n(\s*)'node',",
        rf"\1\n\2{NODE_GROOVY},",
        text,
    )
    text = re.sub(
        r"(\[\s*)'node',",
        rf"\1{NODE_GROOVY},",
        text,
    )
    # expo-constants: ?: ["node"]
    text = re.sub(
        r'\?: \["node"\]',
        f'?: [{NODE_GROOVY}]',
        text,
    )
    # Expo exclude 多包名：joinToString(" ") 会变成单个 argv，CLI 只认重复 --exclude
    if "optionsMap[key] = value.joinToString(\" \")" in text and "repeatedOptions" not in text:
        text = text.replace(
            """  /**
   * Add a list of values as an option to the command.
   */
  fun option(key: String, value: List<String>) = apply {
    optionsMap[key] = value.joinToString(" ")
  }""",
            """  /**
   * Add a list of values as an option to the command.
   * 每个值单独生成 `--key value`，避免 join 成单个字符串后 CLI 无法识别多包 exclude。
   */
  private val repeatedOptions = mutableListOf<Pair<String, String>>()

  fun option(key: String, value: List<String>) = apply {
    value.forEach { repeatedOptions.add(key to it) }
  }""",
        )
        text = text.replace(
            """      optionsMap.map { (key, value) -> listOf("--$key", value) }.flatMap { it } +
      searchPaths""",
            """      optionsMap.map { (key, value) -> listOf("--$key", value) }.flatMap { it } +
      repeatedOptions.flatMap { (key, value) -> listOf("--$key", value) } +
      searchPaths""",
        )
    if text == original and MARKER not in text:
        # 可能已是其它形式；仅告警
        if '"node"' in text or "'node'" in text:
            print(f"警告: 仍含 node 字面量，请人工检查: {path}")
    return text

for path in targets:
    if not path.is_file():
        print(f"跳过（不存在）: {path}")
        continue
    text = path.read_text(encoding="utf-8")
    if MARKER in text and "commandLine(\"node\"" not in text and "commandLine('node'" not in text:
        # 粗略认为已处理过 commandLine；仍可能有遗漏，继续跑一遍无害替换
        pass
    new_text = patch_text(path, text)
    if new_text != text:
        path.write_text(new_text, encoding="utf-8")
        print(f"已修补: {path}")
    else:
        print(f"无变更: {path}")
PY
