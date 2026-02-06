# CLAUDE.md — Android SDK

This file provides guidance to Claude Code (claude.ai/code) when working in the `android/` directory.

## Build & Test Commands

All commands run from `android/`. The project uses Gradle (Kotlin DSL) with a `Justfile` for convenience.

```bash
# Build
just build              # Build lib module (debug)
just build-release      # Build lib module (release AAR)
just build-app          # Build demo app (debug APK)
just build-all          # Build everything

# Unit tests
just test               # Lib unit tests only
just test-app           # App unit tests only
just test-all           # All unit tests
# Or directly:
./gradlew :lib:test
./gradlew :app:test

# Instrumented tests (requires connected device/emulator)
just test-instrumented      # Lib instrumented tests
just test-instrumented-app  # App instrumented tests
# Or directly:
./gradlew :lib:connectedAndroidTest
./gradlew :app:connectedAndroidTest

# E2E proxy tests (requires mitmproxy, ngrok for FTL)
just e2e-proxy-test                # Full E2E with Firebase Test Lab
just e2e-proxy-test-local [ip]     # Local device E2E

# Lint
just lint

# Publish
just publish-local      # Publish to local Maven repo
just publish-release    # Publish to Maven Central

# Utilities
just devices            # List connected Android devices
just dependencies       # Show dependency tree
just tasks              # List all Gradle tasks
```

## Architecture

### Core Library (`lib/`)

Package: `com.guthyerrz.autoproxy` — four Kotlin files:

- **AutoProxyInitializer** — `ContentProvider` registered in manifest (`${applicationId}.autoproxy-init`) that runs at app startup. Registers `ActivityLifecycleCallbacks` to read proxy config from Intent extras (`auto_proxy_host`, `auto_proxy_port`, `auto_proxy_cert`) or SharedPreferences (`auto_proxy_prefs`) on first Activity creation, then calls `AutoProxy.enable()`.

- **AutoProxy** — Singleton that orchestrates proxy setup: sets system properties (`http.proxyHost`, etc.), installs a global `ProxySelector`, builds a composite `X509TrustManager` (system CAs + proxy CA), sets default `SSLContext`, and triggers OkHttp patching. Exposes `getProxy()`, `getHostnameVerifier()`, `sslSocketFactory`, and `trustManager` for manual OkHttp configuration.

- **OkHttpPatcher** — Uses reflection to replace OkHttp's `Platform` singleton with `AutoProxyPlatform`. Calls `Platform.resetForTests()` to swap the instance. Gracefully handles OkHttp not being on the classpath.

- **AutoProxyPlatform** — Extends `okhttp3.internal.platform.Platform`, overriding `newSslSocketFactory()` and `platformTrustManager()` to inject proxy CA trust.

### Demo App (`app/`)

Jetpack Compose app with three tabs (Status, Test, Settings) for validating proxy behavior. Includes `SingularSimulator` which fires a POST to `sdk-api-v1.singular.net` on launch — used by E2E tests to verify proxy interception.

### E2E Testing (`scripts/`)

Python-based (managed with `uv`, requires Python 3.10+) using mitmproxy for traffic capture. `verify_captured_requests.py` validates that expected Singular API calls were intercepted. Tests can run against Firebase Test Lab (via ngrok tunnel) or local devices.

## Key Technical Details

- **Maven Central coordinates:** `com.guthyerrz:autoproxy:<version>`. Version is set in `gradle.properties`.
- **OkHttp is `compileOnly`** in the lib module — it's an optional dependency. The patcher handles `ClassNotFoundException` gracefully.
- The embedded proxy CA cert is at `lib/src/main/res/raw/auto_proxy_ca_cert.pem` (mitmproxy CA). Custom certs can be loaded from app assets.
- The composite trust manager tries the system CA chain first, falling back to the proxy CA chain — allowing both legitimate HTTPS and MITM'd traffic to work.
- Min SDK 23 (lib), 24 (app). Target/Compile SDK 36. JVM target 11.
- Dependency versions managed via `gradle/libs.versions.toml` version catalog.
