package com.guthyerrz.autoproxy

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class AutoProxyInitializer : ContentProvider() {

    companion object {
        private const val TAG = "AutoProxyInitializer"
        private const val EXTRA_HOST = "auto_proxy_host"
        private const val EXTRA_PORT = "auto_proxy_port"
        private const val EXTRA_CERT = "auto_proxy_cert"
        const val PREFS_NAME = "auto_proxy_prefs"
        const val PREF_HOST = "auto_proxy_host"
        const val PREF_PORT = "auto_proxy_port"
        const val PREF_CERT = "auto_proxy_cert"
        private const val CONFIG_ASSET_PATH = "auto_proxy/config.properties"
    }

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var handled = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (handled) return
                handled = true

                // Try intent extras first
                val intent = activity.intent
                var host = intent?.getStringExtra(EXTRA_HOST)
                var port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
                var certPath = intent?.getStringExtra(EXTRA_CERT)

                // Fall back to SharedPreferences
                if (host == null) {
                    val prefs = activity.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    host = prefs.getString(PREF_HOST, null)
                    port = prefs.getInt(PREF_PORT, 0)
                    certPath = prefs.getString(PREF_CERT, null)
                }

                // Fall back to bundled asset config (used by patcher-injected APKs)
                if (host == null) {
                    try {
                        val props = java.util.Properties()
                        activity.applicationContext.assets.open(CONFIG_ASSET_PATH).use {
                            props.load(it)
                        }
                        host = props.getProperty("host")
                        port = props.getProperty("port", "0").toIntOrNull() ?: 0
                    } catch (_: Exception) {
                        // No asset config available
                    }
                }

                if (host == null) return
                if (port == 0) {
                    Log.w(TAG, "auto_proxy_port missing or zero, skipping proxy setup")
                    return
                }

                Log.i(TAG, "Auto-proxy config detected: $host:$port")
                AutoProxy.enable(activity.applicationContext, host, port, certPath)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
