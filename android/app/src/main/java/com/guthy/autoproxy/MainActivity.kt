package com.guthy.autoproxy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guthy.autoproxy.ui.theme.AutoProxyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoProxyTheme {
                AutoProxyApp()
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    STATUS("Status", Icons.Default.Home),
    TEST("Test", Icons.Default.PlayArrow),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun AutoProxyApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.STATUS) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.STATUS -> StatusScreen(Modifier.padding(innerPadding))
                AppDestinations.TEST -> TestScreen(Modifier.padding(innerPadding))
                AppDestinations.SETTINGS -> SettingsScreen(Modifier.padding(innerPadding))
            }
        }
    }
}

// -- Status -------------------------------------------------------------------

@Composable
fun StatusScreen(modifier: Modifier = Modifier) {
    val enabled = AutoProxy.isEnabled
    val host = AutoProxy.proxyHost
    val port = AutoProxy.proxyPort

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Proxy Status", style = MaterialTheme.typography.headlineMedium)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (enabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enabled) "ON" else "OFF",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    if (enabled) {
                        Text(
                            text = "$host:$port",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "All traffic is being proxied",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "No proxy configured",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use Settings tab or launch with intent extras",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// -- Test ---------------------------------------------------------------------

private const val TEST_URL = "https://httpbin.org/get"

private data class RequestResult(
    val label: String,
    val loading: Boolean = false,
    val status: Int? = null,
    val body: String? = null,
    val error: String? = null,
)

@Composable
fun TestScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<Map<String, RequestResult>>(emptyMap()) }

    fun run(key: String, label: String, block: suspend () -> RequestResult) {
        results = results + (key to RequestResult(label, loading = true))
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    block()
                } catch (e: Exception) {
                    RequestResult(label, error = "${e.javaClass.simpleName}: ${e.message}")
                }
            }
            results = results + (key to result)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Request Tests", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Target: $TEST_URL",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        // -- HttpsURLConnection -----------------------------------------------
        RequestTestCard(
            title = "HttpsURLConnection",
            description = "Uses default SSLContext + ProxySelector set by AutoProxy",
            result = results["urlconn"],
            onRun = {
                run("urlconn", "HttpsURLConnection") {
                    val url = URL(TEST_URL)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    try {
                        val code = conn.responseCode
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        RequestResult("HttpsURLConnection", status = code, body = body.take(500))
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        )

        // -- OkHttp (auto-patched) --------------------------------------------
        RequestTestCard(
            title = "OkHttp (auto-patched)",
            description = "Default OkHttpClient — relies on Platform patch for SSL",
            result = results["okhttp_auto"],
            onRun = {
                run("okhttp_auto", "OkHttp (auto-patched)") {
                    val client = OkHttpClient()
                    val request = Request.Builder().url(TEST_URL).build()
                    client.newCall(request).execute().use { response ->
                        RequestResult(
                            "OkHttp (auto-patched)",
                            status = response.code,
                            body = response.body?.string()?.take(500)
                        )
                    }
                }
            }
        )

        // -- OkHttp (manual config) -------------------------------------------
        RequestTestCard(
            title = "OkHttp (manual config)",
            description = "Explicit proxy, sslSocketFactory, hostnameVerifier from AutoProxy",
            result = results["okhttp_manual"],
            onRun = {
                run("okhttp_manual", "OkHttp (manual config)") {
                    val builder = OkHttpClient.Builder()
                        .proxy(AutoProxy.getProxy())
                        .hostnameVerifier(AutoProxy.getHostnameVerifier())

                    val tm = AutoProxy.trustManager
                    val sf = AutoProxy.sslSocketFactory
                    if (tm != null && sf != null) {
                        builder.sslSocketFactory(sf, tm)
                    }

                    val client = builder.build()
                    val request = Request.Builder().url(TEST_URL).build()
                    client.newCall(request).execute().use { response ->
                        RequestResult(
                            "OkHttp (manual config)",
                            status = response.code,
                            body = response.body?.string()?.take(500)
                        )
                    }
                }
            }
        )

        // -- System properties check ------------------------------------------
        RequestTestCard(
            title = "System Properties",
            description = "Reads http.proxyHost / https.proxyHost system properties",
            result = results["sysprops"],
            onRun = {
                val httpHost = System.getProperty("http.proxyHost") ?: "(not set)"
                val httpPort = System.getProperty("http.proxyPort") ?: "(not set)"
                val httpsHost = System.getProperty("https.proxyHost") ?: "(not set)"
                val httpsPort = System.getProperty("https.proxyPort") ?: "(not set)"
                results = results + (
                    "sysprops" to RequestResult(
                        "System Properties",
                        body = "http.proxyHost=$httpHost\nhttp.proxyPort=$httpPort\nhttps.proxyHost=$httpsHost\nhttps.proxyPort=$httpsPort"
                    )
                )
            }
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FilledTonalButton(onClick = { results = emptyMap() }) {
                Text("Clear results")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RequestTestCard(
    title: String,
    description: String,
    result: RequestResult?,
    onRun: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onRun,
                    enabled = result?.loading != true
                ) {
                    Text("Run")
                }
            }

            if (result != null) {
                HorizontalDivider()
                when {
                    result.loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Requesting...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    result.error != null -> {
                        Text(
                            text = result.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        if (result.status != null) {
                            Text(
                                text = "HTTP ${result.status}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (result.status in 200..299)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                        if (result.body != null) {
                            Text(
                                text = result.body,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 12,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

// -- Settings -----------------------------------------------------------------

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            AutoProxyInitializer.PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    var host by rememberSaveable { mutableStateOf(prefs.getString(AutoProxyInitializer.PREF_HOST, "") ?: "") }
    var port by rememberSaveable { mutableStateOf(prefs.getInt(AutoProxyInitializer.PREF_PORT, 0).let { if (it == 0) "" else it.toString() }) }
    var cert by rememberSaveable { mutableStateOf(prefs.getString(AutoProxyInitializer.PREF_CERT, "") ?: "") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Proxy Settings", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            placeholder = { Text("192.168.1.100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            placeholder = { Text("8888") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = cert,
            onValueChange = { cert = it },
            label = { Text("Certificate (assets path)") },
            placeholder = { Text("charles.pem") },
            supportingText = { Text("Leave empty to use embedded mitmproxy CA") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                prefs.edit()
                    .putString(AutoProxyInitializer.PREF_HOST, host)
                    .putInt(AutoProxyInitializer.PREF_PORT, port.toIntOrNull() ?: 0)
                    .putString(AutoProxyInitializer.PREF_CERT, cert.ifBlank { null })
                    .apply()

                restartApp(context)
            },
            enabled = host.isNotBlank() && (port.toIntOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Restart")
        }

        Button(
            onClick = {
                prefs.edit().clear().apply()
                restartApp(context)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear & Restart")
        }
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    Process.killProcess(Process.myPid())
}
