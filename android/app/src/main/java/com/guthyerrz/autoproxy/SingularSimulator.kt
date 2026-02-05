package com.guthyerrz.autoproxy

import android.util.Log
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object SingularSimulator {

    private const val TAG = "SingularSimulator"

    private const val SINGULAR_URL =
        "https://sdk-api-v1.singular.net/api/v1/config" +
            "?a=wildlife_dev_127ec072&p=Android&v=13&i=com.fungames.blockcraft" +
            "&sdk=Singular%2Fv12.5.4&n=Block%20Craft%203D" +
            "&h=4c4d0fe6b0180db5e3ac44e311f4248c894a3143"

    fun fireConfigRequest() {
        Thread {
            try {
                Log.i(TAG, "Firing simulated Singular config request...")
                val url = URL(SINGULAR_URL)
                val conn = url.openConnection() as HttpsURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("User-Agent", "Singular/SDK-v12.5.4.PROD")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000

                conn.outputStream.use { os ->
                    os.write("{}".toByteArray())
                }

                val code = conn.responseCode
                Log.i(TAG, "Singular config response: HTTP $code")
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Singular config request failed", e)
            }
        }.start()
    }
}
