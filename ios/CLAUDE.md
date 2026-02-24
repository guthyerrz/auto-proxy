# CLAUDE.md — iOS SDK

This file provides guidance to Claude Code (claude.ai/code) when working in the `ios/` directory.

## Build & Test Commands

All commands run from `ios/`. Uses a `Justfile` for convenience.

```bash
# Lint podspec
just lint

# Build example app (runs xcodegen + pod install + xcodebuild)
just build-example

# Run unit tests
just test

# Clean
just clean
```

### Manual workflow (without just)

```bash
cd Example
xcodegen generate
pod install
open AutoProxyExample.xcworkspace

# Build
xcodebuild -workspace AutoProxyExample.xcworkspace \
    -scheme AutoProxyExample -sdk iphonesimulator build

# Test
xcodebuild test -workspace AutoProxyExample.xcworkspace \
    -scheme AutoProxyExample -sdk iphonesimulator \
    -destination 'platform=iOS Simulator,name=iPhone 16'
```

## Architecture

### Core Library (`Sources/AutoProxy/`)

Four Swift files + one ObjC auto-loader:

- **AutoProxy** — Singleton that manages proxy config (host, port, CA certificate). Reads settings from `UserDefaults` (which automatically picks up launch arguments). Registers the custom `URLProtocol` and triggers swizzling when enabled.

- **AutoProxyURLProtocol** — Custom `URLProtocol` that intercepts HTTP/HTTPS requests and routes them through the proxy. Tags requests to prevent recursion. Internal sessions use `protocolClasses = []` to bypass interception.

- **ProxySessionDelegate** — `URLSessionDataDelegate` for the internal proxy session. Handles SSL trust evaluation by adding the proxy CA as a trusted anchor alongside system CAs. Forwards responses/data/errors back to the `URLProtocolClient`.

- **URLSessionConfigurationSwizzle** — Swizzles `URLSessionConfiguration.default` and `.ephemeral` to inject `AutoProxyURLProtocol` into `protocolClasses` of every new session.

- **APAutoLoader** (ObjC) — Uses `+load` to trigger `AutoProxy.shared.loadConfig()` on the main queue before any app code runs.

### Configuration

Proxy settings are read from `UserDefaults.standard`, which automatically maps launch arguments:

| Launch Arg | UserDefaults Key | Type | Description |
|---|---|---|---|
| `-auto_proxy_host` | `auto_proxy_host` | String | Proxy hostname/IP |
| `-auto_proxy_port` | `auto_proxy_port` | Int | Proxy port |
| `-auto_proxy_cert` | `auto_proxy_cert` | String | Base64-encoded DER certificate |

If no cert is provided via launch args, the SDK falls back to the embedded `ca_cert.pem` (mitmproxy CA).

### Example App (`Example/`)

UIKit app with status display and test request buttons. Uses XcodeGen (`project.yml`) + CocoaPods (`Podfile`) for project generation.

## Key Technical Details

- **CocoaPods distribution:** `pod 'AutoProxy'` — podspec at `ios/AutoProxy.podspec`
- Deployment target: iOS 15.0
- Swift 5.9
- The embedded CA cert is at `Sources/AutoProxy/Resources/ca_cert.pem`
- The proxy dictionary uses `kCFNetworkProxiesHTTPEnable/Proxy/Port` and `HTTPSEnable/Proxy/Port` keys
- `SecTrustSetAnchorCertificatesOnly(_, false)` is critical — it keeps system CAs trusted alongside the proxy CA
