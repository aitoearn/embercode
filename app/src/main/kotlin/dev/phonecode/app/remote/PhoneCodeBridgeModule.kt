package dev.phonecode.app.remote

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule

/**
 * Compose ↔ RN 桥：读取启动 extras，并把会话摘要写回 Activity result。
 */
@ReactModule(name = PhoneCodeBridgeModule.NAME)
class PhoneCodeBridgeModule(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String = NAME

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun getLaunchExtras(): WritableMap {
        val map = Arguments.createMap()
        val intent = (reactApplicationContext.currentActivity as? RemoteRnActivity)?.intent
        map.putBoolean(
            "embedded",
            intent?.getBooleanExtra(RemoteRnContract.EXTRA_EMBEDDED, true) ?: true,
        )
        intent?.getStringExtra(RemoteRnContract.EXTRA_ROUTE)?.let { map.putString("route", it) }
        intent?.getStringExtra(RemoteRnContract.EXTRA_HOST_ID)?.let { map.putString("hostId", it) }
        intent?.getStringExtra(RemoteRnContract.EXTRA_AGENT_ID)?.let { map.putString("agentId", it) }
        return map
    }

    @ReactMethod
    fun finishWithSummaries(json: String) {
        val activity = reactApplicationContext.currentActivity as? RemoteRnActivity ?: return
        activity.runOnUiThread {
            activity.finishWithSummariesJson(json)
        }
    }

    companion object {
        const val NAME = "PhoneCodeBridge"
    }
}
