import Foundation
import Security

/// Zero-code HTTP/HTTPS proxy injection for iOS apps.
///
/// Reads proxy configuration from UserDefaults (which automatically picks up launch arguments):
///   -auto_proxy_host <host>  -auto_proxy_port <port>  -auto_proxy_cert <base64-DER>
@objc public final class AutoProxy: NSObject {
    @objc public static let shared = AutoProxy()

    public private(set) var isEnabled = false
    public private(set) var proxyHost: String?
    public private(set) var proxyPort: Int = 0
    public private(set) var proxyCertificate: SecCertificate?

    public static let configDidChangeNotification = Notification.Name("AutoProxyConfigDidChange")

    static let hostKey = "auto_proxy_host"
    static let portKey = "auto_proxy_port"
    static let certKey = "auto_proxy_cert"

    private override init() {
        super.init()
    }

    // MARK: - Proxy dictionary for URLSessionConfiguration

    var proxyDictionary: [String: Any] {
        guard let host = proxyHost, proxyPort > 0 else { return [:] }
        return [
            kCFNetworkProxiesHTTPEnable as String: true,
            kCFNetworkProxiesHTTPProxy as String: host,
            kCFNetworkProxiesHTTPPort as String: proxyPort,
            "HTTPSEnable": true,
            "HTTPSProxy": host,
            "HTTPSPort": proxyPort,
        ]
    }

    // MARK: - Public API

    @objc public func loadConfig() {
        // Priority 1: UserDefaults / launch arguments (can override at runtime)
        let defaults = UserDefaults.standard
        if let host = defaults.string(forKey: Self.hostKey), !host.isEmpty {
            let port = defaults.integer(forKey: Self.portKey)
            guard port > 0 else {
                NSLog("[AutoProxy] Invalid or missing proxy port, skipping initialization")
                return
            }
            let certBase64 = defaults.string(forKey: Self.certKey)
            enable(host: host, port: port, certBase64: certBase64)
            return
        }

        // Priority 2: Embedded proxy_config.plist (baked in by patcher)
        if let config = loadEmbeddedConfig() {
            let host = config["ProxyHost"] as? String ?? ""
            let port = config["ProxyPort"] as? Int ?? 0
            guard !host.isEmpty, port > 0 else {
                NSLog("[AutoProxy] Embedded proxy_config.plist has invalid host/port")
                return
            }
            NSLog("[AutoProxy] Using embedded proxy config: %@:%d", host, port)
            enable(host: host, port: port, certBase64: nil)
            return
        }

        NSLog("[AutoProxy] No proxy host configured, skipping initialization")
    }

    public func enable(host: String, port: Int, certBase64: String? = nil) {
        var cert: SecCertificate?

        if let certBase64 = certBase64, !certBase64.isEmpty {
            cert = decodeCertificate(base64: certBase64)
        }

        if cert == nil {
            cert = loadEmbeddedCert()
        }

        enable(host: host, port: port, certificate: cert)
    }

    public func enable(host: String, port: Int, certData: Data) {
        let cert = SecCertificateCreateWithData(nil, certData as CFData)
        enable(host: host, port: port, certificate: cert)
    }

    func enable(host: String, port: Int, certificate: SecCertificate?) {
        proxyHost = host
        proxyPort = port
        proxyCertificate = certificate
        isEnabled = true

        URLProtocol.registerClass(AutoProxyURLProtocol.self)
        URLSessionConfigurationSwizzle.install()

        NSLog("[AutoProxy] Enabled — %@:%d (cert: %@)",
              host, port, certificate != nil ? "loaded" : "none")
        NotificationCenter.default.post(name: Self.configDidChangeNotification, object: self)
    }

    public func disable() {
        isEnabled = false
        proxyHost = nil
        proxyPort = 0
        proxyCertificate = nil
        URLProtocol.unregisterClass(AutoProxyURLProtocol.self)
        NSLog("[AutoProxy] Disabled")
        NotificationCenter.default.post(name: Self.configDidChangeNotification, object: self)
    }

    // MARK: - Certificate handling

    func decodeCertificate(base64: String) -> SecCertificate? {
        // Strip PEM headers if present, then decode
        let cleaned = base64
            .replacingOccurrences(of: "-----BEGIN CERTIFICATE-----", with: "")
            .replacingOccurrences(of: "-----END CERTIFICATE-----", with: "")
            .replacingOccurrences(of: "\n", with: "")
            .replacingOccurrences(of: "\r", with: "")
            .trimmingCharacters(in: .whitespaces)

        guard let data = Data(base64Encoded: cleaned) else {
            NSLog("[AutoProxy] Failed to base64-decode certificate")
            return nil
        }

        guard let cert = SecCertificateCreateWithData(nil, data as CFData) else {
            NSLog("[AutoProxy] Failed to parse DER certificate data")
            return nil
        }

        return cert
    }

    func loadEmbeddedCert() -> SecCertificate? {
        // Look in the resource bundle shipped with the pod
        let candidates = [
            Bundle(for: AutoProxy.self).url(forResource: "ca_cert", withExtension: "pem", subdirectory: nil),
            Bundle(for: AutoProxy.self).url(forResource: "ca_cert", withExtension: "pem"),
            resourceBundle?.url(forResource: "ca_cert", withExtension: "pem"),
        ]

        for case let url? in candidates {
            if let pem = try? String(contentsOf: url, encoding: .utf8) {
                if let cert = decodeCertificate(base64: pem) {
                    NSLog("[AutoProxy] Loaded embedded CA certificate from %@", url.lastPathComponent)
                    return cert
                }
            }
        }

        NSLog("[AutoProxy] No embedded CA certificate found")
        return nil
    }

    private func loadEmbeddedConfig() -> [String: Any]? {
        // Look for proxy_config.plist in the resource bundle (injected by the patcher)
        let candidates = [
            resourceBundle?.url(forResource: "proxy_config", withExtension: "plist"),
            Bundle(for: AutoProxy.self).url(forResource: "proxy_config", withExtension: "plist"),
        ]

        for case let url? in candidates {
            if let data = try? Data(contentsOf: url),
               let config = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any] {
                return config
            }
        }
        return nil
    }

    private var resourceBundle: Bundle? {
        // CocoaPods resource bundles are named after the pod
        guard let url = Bundle(for: AutoProxy.self)
            .url(forResource: "AutoProxy", withExtension: "bundle") else { return nil }
        return Bundle(url: url)
    }
}
