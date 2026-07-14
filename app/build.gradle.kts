import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    id("com.facebook.react")
}

val releaseStoreFile = providers.gradleProperty("PHONECODE_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("PHONECODE_RELEASE_STORE_FILE"))
    .orNull
val releaseStorePassword = providers.gradleProperty("PHONECODE_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("PHONECODE_RELEASE_STORE_PASSWORD"))
    .orNull
val releaseKeyAlias = providers.gradleProperty("PHONECODE_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("PHONECODE_RELEASE_KEY_ALIAS"))
    .orNull
val releaseKeyPassword = providers.gradleProperty("PHONECODE_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("PHONECODE_RELEASE_KEY_PASSWORD"))
    .orNull

android {
    namespace = "dev.phonecode.app"
    // AGP 8.11 与 Expo 54 常见 compileSdk 对齐；暂不追 37
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.phonecode"
        minSdk = 26
        targetSdk = 35
        versionCode = 30
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Debug 默认打开远程 RN，便于 Spike / 联调；Release 仍由 -Pphonecode.remoteRn 控制
            buildConfigField("boolean", "REMOTE_RN_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
            buildConfigField(
                "boolean",
                "REMOTE_RN_ENABLED",
                providers.gradleProperty("phonecode.remoteRn").orElse("false").get(),
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Roborazzi (no Gradle plugin needed): captureRoboImage() writes PNGs whenever this
                // flag is on. Screenshots land in app/screenshots/ - the design feedback loop.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
        resValues = true
    }

    // PRoot/QEMU 的 ndkBuild 已迁到 :runtime-native，本模块留给 RN New Arch 的 CMake。

    androidResources {
        noCompress += "rootfs"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// React Native：工程根即仓库根（无 android/ 子目录）
react {
    root = file("..")
    reactNativeDir = file("../node_modules/react-native")
    codegenDir = file("../node_modules/@react-native/codegen")
    cliFile = file("../node_modules/react-native/cli.js")
    // settings.gradle.kts 已写入 systemProp；缺省再回退常见路径
    nodeExecutableAndArgs.set(
        listOf(
            System.getProperty("nodejs.executable")
                ?: System.getenv("NODE_BINARY")
                ?: "/usr/local/bin/node",
        ),
    )
    autolinkLibrariesWithApp()
}

dependencies {
    implementation(project(":agent"))
    implementation(project(":provider"))
    implementation(project(":tools"))
    // QEMU spawn 原生库（ndkBuild）；与 RN CMake 分模块，避免 CXX1400
    implementation(project(":runtime-native"))

    // React Native 运行时（版本由 RNGP 对齐 node_modules）
    implementation("com.facebook.react:react-android")
    implementation("com.facebook.react:hermes-android")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.haze)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jgit)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.ui.test.junit4)
    // debugImplementation (not testImplementation): the AAR's ComponentActivity manifest entry
    // must merge into the app manifest for Robolectric to resolve createComposeRule's activity.
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}

val prototypeRuntimeFiles = listOf(
    file("src/main/assets/alpine-aarch64.rootfs"),
    file("src/main/jniLibs/arm64-v8a/libproot.so"),
    file("src/main/jniLibs/arm64-v8a/libproot-loader.so"),
)

val legalVendoredFiles = files(
    fileTree("src/main/assets") { include("*.js", "*.rootfs") },
    fileTree("src/main/jniLibs") { include("**/*.so") },
    fileTree("src/main/res/font") { include("*.ttf") },
)

val verifyLegalInventory by tasks.registering {
    val checksumFile = rootProject.file("VENDORED_CHECKSUMS")
    val legalFiles = listOf(
        rootProject.file("LICENSE"),
        rootProject.file("legal/privacy.md"),
        rootProject.file("legal/terms.md"),
        rootProject.file("legal/RELEASE_COMPLIANCE.md"),
        rootProject.file("THIRD_PARTY.md"),
        file("src/main/assets/privacy.md"),
        file("src/main/assets/terms.md"),
        file("src/main/assets/licenses.md"),
        file("src/main/jniLibs/PROVENANCE.md"),
    )
    inputs.files(checksumFile, legalVendoredFiles, legalFiles)
    doLast {
        val shaPattern = Regex("[0-9a-f]{64}")
        val entries = checksumFile.readLines().filter(String::isNotBlank).associate { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            check(parts.size == 2 && parts[0].matches(shaPattern)) { "Invalid vendored checksum entry: $line" }
            parts[1] to parts[0]
        }
        val actual = legalVendoredFiles.files.mapTo(sortedSetOf()) {
            it.relativeTo(rootProject.projectDir).invariantSeparatorsPath
        }
        check(entries.keys == actual) {
            "VENDORED_CHECKSUMS does not match the vendored app inventory: registered=${entries.keys.sorted()}, actual=$actual"
        }
        entries.forEach { (path, expected) ->
            val digest = MessageDigest.getInstance("SHA-256")
            rootProject.file(path).inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualHash == expected) { "$path checksum mismatch: expected $expected, got $actualHash" }
        }
        check(rootProject.file("legal/privacy.md").readBytes().contentEquals(file("src/main/assets/privacy.md").readBytes())) {
            "The public and in-app privacy policies differ"
        }
        check(rootProject.file("legal/terms.md").readBytes().contentEquals(file("src/main/assets/terms.md").readBytes())) {
            "The public and in-app terms differ"
        }
        val notices = file("src/main/assets/licenses.md").readText()
        check(rootProject.file("LICENSE").readText().contains("Apache License\n                           Version 2.0, January 2004")) {
            "The root Apache-2.0 license is missing or incomplete"
        }
        check(notices.contains("Copyright 2026 dttdrv") && notices.contains("Apache License 2.0")) {
            "The in-app PhoneCode license notice is incomplete"
        }
        check(listOf("OpenCode", "Mermaid", "Inter", "JetBrains Mono", "Alpine", "PRoot", "talloc").all(notices::contains)) {
            "The in-app open-source notice inventory is incomplete"
        }
        val thirdParty = rootProject.file("THIRD_PARTY.md").readText()
        check(thirdParty.contains("licensed under Apache License 2.0")) {
            "The third-party inventory does not identify PhoneCode's root license"
        }
        val provenance = file("src/main/jniLibs/PROVENANCE.md").readText()
        listOf(
            "app/src/main/jniLibs/arm64-v8a/libproot.so",
            "app/src/main/jniLibs/arm64-v8a/libproot-loader.so",
        ).forEach { path ->
            check(provenance.contains(entries.getValue(path))) {
                "$path provenance hash does not match VENDORED_CHECKSUMS"
            }
        }
    }
}

val verifyPrototypeRuntimeBoundary by tasks.registering {
    val registry = file("src/main/kotlin/dev/phonecode/app/agent/ChatViewModel.kt")
    val bootstrap = file("src/main/kotlin/dev/phonecode/app/agent/EnvironmentBootstrap.kt")
    val hostSources = rootProject.fileTree(rootProject.projectDir) {
        include("app/src/main/**/*.kt", "agent/src/main/**/*.kt", "tools/src/main/**/*.kt")
    }
    inputs.files(prototypeRuntimeFiles + registry + bootstrap, hostSources)
    doLast {
        check(prototypeRuntimeFiles.all(File::exists)) {
            "The bundled local runtime is incomplete"
        }
        check(listOf("ShellTool(", "ProcessTool(").all(registry.readText()::contains)) {
            "The local runtime tools are not registered"
        }
        val bootstrapText = bootstrap.readText()
        check(listOf("\"-b\", \"/data\"", "\"-b\", \"/sys\"", "listOf(\"/system/bin/sh\"").none(bootstrapText::contains)) {
            "The local runtime exposes a host path or shell fallback"
        }
        val forbiddenApis = listOf("DexClassLoader(", "InMemoryDexClassLoader(")
        val offenders = hostSources.files.filter { source ->
            forbiddenApis.any(source.readText()::contains)
        }
        check(offenders.isEmpty()) {
            "Runtime DEX loading is not allowed: ${offenders.joinToString()}"
        }
    }
}

val verifyPlayReleaseBoundary by tasks.registering {
    doLast {
        error("Google Play release blocked: package a reproducible QEMU payload and pass the runtime, licensing, API 26/34/35, and AAB audits")
    }
}

val playReleaseArtifactTasks = setOf(
    "assembleRelease",
    "bundleRelease",
    "packageRelease",
    "packageReleaseBundle",
    "packageReleaseUniversalApk",
    "signReleaseBundle",
)

gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name in playReleaseArtifactTasks }) {
        error("Google Play release blocked: package a reproducible QEMU payload and pass the runtime, licensing, API 26/34/35, and AAB audits")
    }
}

tasks.named("check").configure {
    dependsOn(verifyPrototypeRuntimeBoundary, verifyLegalInventory)
}
