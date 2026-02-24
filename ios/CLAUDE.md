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

# Build AutoProxy.framework for patcher embedding
just build-framework

# Build the patcher CLI
just build-patcher

# Run patcher tests
just test-patcher

# Patch an IPA
just patch-ipa MyApp.ipa 10.0.20.205 777 ~/cert.pem "Apple Development: ..." ~/profile.mobileprovision

# Install patcher to ~/.local/bin
just install-patcher

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

- **AutoProxy** — Singleton that manages proxy config (host, port, CA certificate). Reads settings from `UserDefaults` (which automatically picks up launch arguments), with fallback to embedded `proxy_config.plist` (baked in by the patcher). Registers the custom `URLProtocol` and triggers swizzling when enabled.

- **AutoProxyURLProtocol** — Custom `URLProtocol` that intercepts HTTP/HTTPS requests and routes them through the proxy. Tags requests to prevent recursion. Internal sessions use `protocolClasses = []` to bypass interception.

- **ProxySessionDelegate** — `URLSessionDataDelegate` for the internal proxy session. Handles SSL trust evaluation by adding the proxy CA as a trusted anchor alongside system CAs. Forwards responses/data/errors back to the `URLProtocolClient`.

- **URLSessionConfigurationSwizzle** — Swizzles `URLSessionConfiguration.default` and `.ephemeral` to inject `AutoProxyURLProtocol` into `protocolClasses` of every new session.

- **APAutoLoader** (ObjC) — Uses `+load` to trigger `AutoProxy.shared.loadConfig()` on the main queue before any app code runs.

### Configuration

**Priority order** (first match wins):
1. `UserDefaults.standard` / launch arguments (can override at runtime)
2. Embedded `proxy_config.plist` (baked into the framework bundle by the patcher)

| Launch Arg | UserDefaults Key | Type | Description |
|---|---|---|---|
| `-auto_proxy_host` | `auto_proxy_host` | String | Proxy hostname/IP |
| `-auto_proxy_port` | `auto_proxy_port` | Int | Proxy port |
| `-auto_proxy_cert` | `auto_proxy_cert` | String | Base64-encoded DER certificate |

If no cert is provided via launch args, the SDK falls back to the embedded `ca_cert.pem` (mitmproxy CA).

### IPA Patcher (`patcher/`)

Swift Package (CLI executable) that injects AutoProxy into compiled IPAs for non-jailbroken devices. Uses **optool** (BSD-2-Clause) for Mach-O `LC_LOAD_DYLIB` injection.

**8-step pipeline:**
1. **Unpack** — Unzip IPA, locate `.app` + main binary via `Info.plist`
2. **Inject framework** — Copy `AutoProxy.framework` into `Frameworks/`
3. **Inject load command** — Insert `LC_LOAD_DYLIB` via optool pointing to `@executable_path/Frameworks/AutoProxy.framework/AutoProxy`
4. **Inject config** — Write `proxy_config.plist` + CA cert into framework's resource bundle
5. **Patch bundle ID** — Change `CFBundleIdentifier` (default: `com.patched.<original>`)
6. **Replace profile** — Copy `.mobileprovision`, extract entitlements
7. **Re-sign** — `codesign` frameworks → extensions → app with extracted entitlements
8. **Repack** — Zip `Payload/` back into IPA

**Key files:**
- `patcher/Package.swift` — SPM manifest with `COptool` (ObjC) + `auto-proxy-patcher` (Swift) targets
- `patcher/Sources/COptool/` — Embedded optool core (BSD-2-Clause) for Mach-O patching
- `patcher/Sources/AutoProxyPatcher/` — Swift CLI with ArgumentParser
- `patcher/Sources/AutoProxyPatcher/Resources/AutoProxy.framework/` — Pre-built framework (built via `just build-framework`)

### Example App (`Example/`)

UIKit app with status display and test request buttons. Uses XcodeGen (`project.yml`) + CocoaPods (`Podfile`) for project generation.

## Key Technical Details

- **CocoaPods distribution:** `pod 'AutoProxy'` — podspec at `ios/AutoProxy.podspec`
- Deployment target: iOS 15.0
- Swift 5.9
- The embedded CA cert is at `Sources/AutoProxy/Resources/ca_cert.pem`
- The proxy dictionary uses `kCFNetworkProxiesHTTPEnable/Proxy/Port` and `HTTPSEnable/Proxy/Port` keys
- `SecTrustSetAnchorCertificatesOnly(_, false)` is critical — it keeps system CAs trusted alongside the proxy CA
