[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

# Auto Proxy

Zero-code HTTP/HTTPS proxy injection for debugging and traffic inspection.

## Getting started (Android)

Add the library module to your project, then launch your app with proxy settings via `adb`:

```bash
adb shell am start -n com.your.app/.MainActivity \
  --es auto_proxy_host 192.168.1.100 \
  --ei auto_proxy_port 8080
```

Or configure it via `SharedPreferences` before launch:

```bash
adb shell am start -n com.your.app/.MainActivity
# The app reads from SharedPreferences key "auto_proxy_prefs"
```

No code changes required in your app — the library initializes automatically via a `ContentProvider`.

## How it works

The `:lib` module ships a `ContentProvider` (`AutoProxyInitializer`) that runs before your first `Activity`. On the first `onActivityCreated` callback it reads proxy configuration from:

1. **Intent extras** — `auto_proxy_host` (String), `auto_proxy_port` (Int), `auto_proxy_cert` (String, optional)
2. **SharedPreferences** — `auto_proxy_prefs` with keys `auto_proxy_host`, `auto_proxy_port`, `auto_proxy_cert`

When configuration is found, `AutoProxy.enable()` sets up:

- **System properties** (`http.proxyHost`, `https.proxyHost`, etc.)
- **Global `ProxySelector`** for libraries that honour it
- **SSL trust** — a composite `TrustManager` that trusts both the system CAs and the proxy CA, applied to the default `SSLContext` and `HttpsURLConnection`
- **OkHttp platform patch** — reflectively replaces OkHttp's `Platform` singleton so its internal SSL setup also uses the proxy CA

## Supported networking stacks

| Stack | How it's handled |
|---|---|
| `HttpsURLConnection` | Automatic — picks up system properties + default `SSLContext` |
| OkHttp (auto-patch) | Automatic — `Platform` is patched at init time |
| OkHttp (manual) | Use `AutoProxy.getProxy()`, `.sslSocketFactory`, and `.trustManager` on your `OkHttpClient.Builder` |
| System properties | Set globally — any library reading `http.proxyHost` / `https.proxyHost` will route through the proxy |

## Custom certificate

By default the SDK bundles a mitmproxy CA certificate. To use your own (e.g. Charles Proxy):

1. Place the `.pem` file in your app's `assets/` folder
2. Pass the asset path via intent extra or SharedPreferences:

```bash
adb shell am start -n com.your.app/.MainActivity \
  --es auto_proxy_host 192.168.1.100 \
  --ei auto_proxy_port 8888 \
  --es auto_proxy_cert charles.pem
```

## Demo app

The `:app` module is a Jetpack Compose demo with three screens:

- **Status** — shows whether the proxy is active and the current host:port
- **Test** — fires HTTP requests via `HttpsURLConnection`, OkHttp (auto-patched), and OkHttp (manual) to verify proxy interception
- **Settings** — configure proxy host, port, and custom cert path at runtime

## License

[MIT](LICENSE)
