pluginManagement {
    // plugins {} 会在脚本其余代码之前应用；必须在此写入 node 绝对路径。
    val localProps = java.util.Properties()
    val localFile = File(settings.rootDir, "local.properties")
    if (localFile.isFile) {
        localFile.inputStream().use { localProps.load(it) }
    }
    val nodeCandidates = listOfNotNull(
        System.getenv("NODE_BINARY")?.trim()?.takeIf { it.isNotEmpty() },
        localProps.getProperty("nodejs.executable")?.trim()?.takeIf { it.isNotEmpty() },
        localProps.getProperty("nodejs.dir")?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it/node" },
        "/usr/local/bin/node",
        "/opt/homebrew/bin/node",
        "${System.getProperty("user.home")}/.volta/bin/node",
        "${System.getProperty("user.home")}/.local/bin/node",
        "${System.getProperty("user.home")}/.bun/bin/node",
    )
    val nodeExecutable = nodeCandidates.firstOrNull { File(it).canExecute() }
        ?: error(
            "找不到可执行的 node。请在 local.properties 设置 nodejs.executable=/绝对路径/node，" +
                "或导出 NODE_BINARY。",
        )
    System.setProperty("nodejs.executable", nodeExecutable)
    // 供本文件后续 extensions 配置使用
    settings.gradle.extensions.extraProperties.set("phonecode.nodeExecutable", nodeExecutable)

    includeBuild("node_modules/@react-native/gradle-plugin")
    includeBuild("node_modules/expo-modules-autolinking/android/expo-gradle-plugin")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.facebook.react.settings")
    id("expo-autolinking-settings")
}

val nodeExecutable =
    settings.gradle.extensions.extraProperties.get("phonecode.nodeExecutable") as String

extensions.configure<com.facebook.react.ReactSettingsExtension> {
    // 本仓库 Gradle 根即仓库根（无 android/ 子目录）。
    // 显式用绝对 node，避免 IDE 精简 PATH 下 `npx` 找不到。
    val cliJs = File(settings.rootDir, "node_modules/@react-native-community/cli/build/bin.js")
    autolinkLibrariesFromCommand(
        command = listOf(nodeExecutable, cliJs.absolutePath, "config"),
        workingDirectory = settings.rootDir,
        lockFiles = settings.layout.rootDirectory.files(
            "yarn.lock",
            "package-lock.json",
            "package.json",
            "react-native.config.js",
        ),
    )
}

extensions.configure<expo.modules.plugin.ExpoAutolinkingSettingsExtension> {
    // Gradle 根 = JS 工程根
    projectRoot = settings.rootDir
    useExpoModules()
}

dependencyResolutionManagement {
    // Expo 会把 local-maven-repo 挂到 project repositories；PREFER_SETTINGS 会忽略它们导致 AAR 找不到。
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("$rootDir/node_modules/react-native/android") }
        maven { url = uri("$rootDir/node_modules/@react-native/maven-repo") }
        maven { url = uri("https://www.jitpack.io") }
        mavenLocal()
    }
}

rootProject.name = "PhoneCode"

includeBuild("node_modules/@react-native/gradle-plugin")

include(":app", ":agent", ":provider", ":tools", ":runtime-native")
