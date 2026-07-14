package dev.phonecode.app.remote

import android.content.Intent
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import dev.phonecode.app.BuildConfig
import expo.modules.ReactActivityDelegateWrapper

/**
 * 远程 RN / Expo 宿主 Activity。
 * 加载根目录 `index.js` 注册的 `PhoneCodeRemote`（@phonecode/remote-ui embedded）。
 */
class RemoteRnActivity : ReactActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (!RemoteFeature.isEnabled()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        // 保留 extras 供 JS 侧读取（pair/host/chat）
        super.onCreate(savedInstanceState)
    }

    override fun getMainComponentName(): String = "PhoneCodeRemote"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        ReactActivityDelegateWrapper(
            this,
            BuildConfig.IS_NEW_ARCHITECTURE_ENABLED,
            DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled),
        )

    /** JS 侧可通过 NativeModule 回传摘要。 */
    fun finishWithSummariesJson(json: String) {
        setResult(
            RESULT_OK,
            Intent().putExtra(RemoteRnContract.RESULT_SUMMARIES_JSON, json),
        )
        finish()
    }
}
