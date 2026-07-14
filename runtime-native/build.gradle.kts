plugins {
    alias(libs.plugins.android.library)
}

/**
 * 本地 runtime 原生库（QEMU spawn：libphonecode_vm.so）。
 * 从 :app 拆出，避免与 RN New Architecture 的 CMake 在同一模块触发 CXX1400。
 */
android {
    namespace = "dev.phonecode.runtimenative"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            ndkBuild {
                arguments += "APP_ABI=arm64-v8a"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
