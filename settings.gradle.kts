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
    // expo-updates 由 @getpaseo/app 的 package.json 传递依赖带入 node_modules，
    // 但本项目未采用 EAS Update 原生更新流程。
    // ExpoUpdatesPlugin 计算 projectRoot 时假定标准 `<repo>/android` 目录结构（会取
    // rootProject.projectDir 的上一级），而本仓库无 android/ 子目录、Gradle 根即仓库根，
    // 导致其反算出的 projectRoot 指向仓库上一级目录，运行 node 时找不到 node_modules 里的
    // expo-updates（`Cannot find module 'expo-updates/package.json'`）。
    // 显式排除该包，避免其 Gradle 插件被错误应用。
    // 注意：ExpoAutolinkingSettingsExtension.exclude 底层用空字符串拼接多个包名再传给
    // expo-modules-autolinking CLI（一个已知的实现限制），导致同时排除多个包名会失效，
    // 因此这里只能排除一个包名。expo-dev-client 的 gradle 插件目前未观察到构建失败，暂不排除。
    exclude = listOf("expo-updates")
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
