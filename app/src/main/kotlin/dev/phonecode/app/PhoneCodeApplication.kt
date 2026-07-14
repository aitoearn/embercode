package dev.phonecode.app

import android.app.Application
import android.content.res.Configuration
import android.os.Process
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultReactNativeHost
import dev.phonecode.app.agent.ChatViewModel
import dev.phonecode.app.agent.TurnService
import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ReactNativeHostWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * PhoneCode Application：本地 agent 能力 + React Native / Expo 宿主。
 * RN 初始化在进程启动时完成；是否进入远程页由 [dev.phonecode.app.remote.RemoteFeature] 控制。
 */
class PhoneCodeApplication : Application(), ReactApplication {
    val foregroundLeases by lazy {
        requireMainUid()
        ForegroundLeaseManager(
            start = { TurnService.start(this) },
            stop = { TurnService.stop(this) },
        )
    }
    val chatViewModel by lazy {
        requireMainUid()
        ChatViewModel(this)
    }

    /**
     * Scope for in-flight agent TURNS. Deliberately application-level: a turn launched in
     * viewModelScope died with the activity (closing the app or locking the phone killed the
     * response mid-stream - device feedback). Here the turn keeps streaming as long as the
     * process lives (the dataSync TurnService holds that lease), persists its session on
     * completion, and the reopened UI restores the finished reply. ChatViewModel.cancel()
     * cancels the individual job; the scope itself lives as long as the process.
     */
    val turnScope by lazy {
        requireMainUid()
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override val reactNativeHost: ReactNativeHost =
        ReactNativeHostWrapper(
            this,
            object : DefaultReactNativeHost(this) {
                override fun getPackages(): List<ReactPackage> =
                    PackageList(this).packages.apply {
                        // 无法 autolink 的包可在此手动 add
                    }

                override fun getJSMainModuleName(): String = "index"

                override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

                override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
                override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
            },
        )

    override val reactHost: ReactHost
        get() = ReactNativeHostWrapper.createReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        loadReactNative(this)
        ApplicationLifecycleDispatcher.onApplicationCreate(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
    }

    private fun requireMainUid() {
        check(Process.myUid() == applicationInfo.uid)
    }
}
