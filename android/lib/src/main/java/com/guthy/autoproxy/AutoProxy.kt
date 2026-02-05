package com.guthy.autoproxy

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object AutoProxy {

    private const val TAG = "AutoProxy"

    var isEnabled: Boolean = false
        private set

    var trustManager: X509TrustManager? = null
        private set

    var sslSocketFactory: SSLSocketFactory? = null
        private set

    var proxyHost: String? = null
        private set
    var proxyPort: Int = 0
        private set

    /**
     * Enable the proxy with the given host and port.
     *
     * @param context Application context
     * @param host Proxy host address
     * @param port Proxy port number
     * @param certAssetPath Optional path to a CA cert in the app's assets folder.
     *                      If null, the embedded mitmproxy CA cert is used.
     */
    fun enable(context: Context, host: String, port: Int, certAssetPath: String? = null) {
        proxyHost = host
        proxyPort = port

        setupSystemProperties(host, port)
        setupProxySelector(host, port)
        setupSSLTrust(context, certAssetPath)
        patchOkHttpPlatform()

        isEnabled = true
        Log.i(TAG, "Proxy enabled: $host:$port")
    }

    fun getProxy(): Proxy {
        if (!isEnabled) return Proxy.NO_PROXY
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
    }

    fun getHostnameVerifier(): HostnameVerifier = HostnameVerifier { _, _ -> true }

    // -- Internal setup -------------------------------------------------------

    private fun setupSystemProperties(host: String, port: Int) {
        val portStr = port.toString()
        System.setProperty("http.proxyHost", host)
        System.setProperty("http.proxyPort", portStr)
        System.setProperty("https.proxyHost", host)
        System.setProperty("https.proxyPort", portStr)
        System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1")
        Log.d(TAG, "System properties configured for proxy: $host:$port")
    }

    private fun setupProxySelector(host: String, port: Int) {
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                Log.d(TAG, "ProxySelector.select: $uri")
                return listOf(proxy)
            }

            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: java.io.IOException) {
                Log.w(TAG, "ProxySelector connection failed: $uri", ioe)
            }
        })
        Log.d(TAG, "ProxySelector configured")
    }

    private fun setupSSLTrust(context: Context, certAssetPath: String?) {
        try {
            val cf = CertificateFactory.getInstance("X.509")

            val certInput: InputStream = if (certAssetPath != null) {
                context.assets.open(certAssetPath)
            } else {
                context.resources.openRawResource(R.raw.auto_proxy_ca_cert)
            }

            val caCert = certInput.use { cf.generateCertificate(it) }

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("auto-proxy-ca", caCert)
            }

            val defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(null as KeyStore?)
            }
            val customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }

            val defaultTm = defaultTmf.trustManagers[0] as X509TrustManager
            val customTm = customTmf.trustManagers[0] as X509TrustManager

            val compositeTm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    defaultTm.checkClientTrusted(chain, authType)
                }

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    try {
                        defaultTm.checkServerTrusted(chain, authType)
                    } catch (e: java.security.cert.CertificateException) {
                        customTm.checkServerTrusted(chain, authType)
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    defaultTm.acceptedIssuers + customTm.acceptedIssuers
            }

            trustManager = compositeTm

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(compositeTm), SecureRandom())
            }
            sslSocketFactory = sslContext.socketFactory

            SSLContext.setDefault(sslContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

            Log.d(TAG, "SSL trust configured with proxy CA")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup SSL trust", e)
            throw RuntimeException("Failed to setup SSL trust for auto proxy", e)
        }
    }

    private fun patchOkHttpPlatform() {
        try {
            Class.forName("okhttp3.internal.platform.Platform")
            OkHttpPatcher.patch()
        } catch (_: ClassNotFoundException) {
            Log.d(TAG, "OkHttp not on classpath, skipping platform patch")
        }
    }
}
