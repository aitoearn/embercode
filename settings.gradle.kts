pluginManagement {
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

extensions.configure<com.facebook.react.ReactSettingsExtension> {
    // 本仓库 Gradle 根即仓库根（无 android/ 子目录）。
    // 先用 community autolinking；Expo modules 另由 useExpoModules() 挂载。
    autolinkLibrariesFromCommand(
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
