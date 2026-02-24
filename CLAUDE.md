# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Auto Proxy is a monorepo of mobile SDKs that provide zero-code HTTP/HTTPS proxy injection for mobile apps. The SDKs are designed for automation and traffic inspection — they intercept network traffic without requiring app code changes.

Each SDK lives in its own top-level directory with its own build system, tests, and CLAUDE.md:

| Platform | Directory | Status |
|----------|-----------|--------|
| Android  | [`android/`](android/CLAUDE.md) | Active |
| iOS      | [`ios/`](ios/CLAUDE.md) | Active |
| Flutter  | `flutter/` | Planned |

## How It Works (Cross-Platform Concept)

The core idea is the same across all platforms:

1. **Zero-code initialization** — The SDK hooks into the platform's app lifecycle to start before any app code runs (e.g., Android `ContentProvider`, iOS swizzling).
2. **Proxy configuration** — Injects proxy host/port into the platform's networking layer (system properties, URL session config, etc.).
3. **SSL trust injection** — Installs a composite trust manager that trusts both system CAs and the proxy's CA certificate, enabling MITM inspection of HTTPS traffic.
4. **Networking stack patching** — Patches popular networking libraries (OkHttp, URLSession, etc.) that may bypass system-level proxy settings.
5. **External configuration** — Proxy settings come from outside the app (launch arguments, preferences) so no app code changes are needed.

## CI/CD

GitHub Actions workflows live in `.github/workflows/`:

- **`e2e-proxy-test.yml`** — Runs E2E proxy interception tests on PRs and tag pushes using mitmproxy and Firebase Test Lab.
- **`release.yml`** — Triggers on `v*` tags. Builds, signs, publishes to package registries (Maven Central for Android), and creates a GitHub release.

## Repository Conventions

- Each SDK directory contains a `Justfile` for common development tasks (`just build`, `just test`, etc.).
- E2E tests use mitmproxy to verify traffic interception. Python scripts (managed with `uv`) handle request verification.
- Versions are managed per-SDK in their respective build config files.
