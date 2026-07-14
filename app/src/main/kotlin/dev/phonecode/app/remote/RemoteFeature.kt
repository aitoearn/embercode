package dev.phonecode.app.remote

import dev.phonecode.app.BuildConfig

/** 远程 RN（paseo）能力开关；由 Gradle 属性 `phonecode.remoteRn` 注入 BuildConfig。 */
object RemoteFeature {
    fun isEnabled(): Boolean = BuildConfig.REMOTE_RN_ENABLED
}
