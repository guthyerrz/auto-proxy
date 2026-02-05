package com.guthy.autoproxy

import okhttp3.internal.platform.Platform
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class AutoProxyPlatform(private val delegate: Platform) : Platform() {

    override fun newSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        return AutoProxy.sslSocketFactory ?: super.newSslSocketFactory(trustManager)
    }

    override fun platformTrustManager(): X509TrustManager {
        return AutoProxy.trustManager ?: super.platformTrustManager()
    }
}
