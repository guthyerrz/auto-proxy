package com.guthy.autoproxy

import android.util.Log
import okhttp3.internal.platform.Platform

internal object OkHttpPatcher {

    private const val TAG = "OkHttpPatcher"

    fun patch() {
        try {
            val companionClass = Class.forName("okhttp3.internal.platform.Platform\$Companion")
            val platformClass = Class.forName("okhttp3.internal.platform.Platform")

            val companionField = platformClass.getDeclaredField("Companion").apply {
                isAccessible = true
            }
            val companion = companionField.get(null)

            val getMethod = companionClass.getDeclaredMethod("get")
            val currentPlatform = getMethod.invoke(companion) as Platform

            val debugPlatform = AutoProxyPlatform(currentPlatform)

            val resetMethod = companionClass.getDeclaredMethod("resetForTests", platformClass).apply {
                isAccessible = true
            }
            resetMethod.invoke(companion, debugPlatform)

            Log.i(TAG, "OkHttp Platform patched with AutoProxyPlatform")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to patch OkHttp Platform", e)
        }
    }
}
