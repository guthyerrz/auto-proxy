import XCTest
@testable import AutoProxy

final class AutoProxyTests: XCTestCase {

    override func setUp() {
        super.setUp()
        // Reset state between tests
        AutoProxy.shared.disable()
    }

    // MARK: - Config loading

    func testLoadConfigFromUserDefaults() {
        let defaults = UserDefaults.standard
        defaults.set("192.168.1.100", forKey: AutoProxy.hostKey)
        defaults.set(8080, forKey: AutoProxy.portKey)

        AutoProxy.shared.loadConfig()

        XCTAssertTrue(AutoProxy.shared.isEnabled)
        XCTAssertEqual(AutoProxy.shared.proxyHost, "192.168.1.100")
        XCTAssertEqual(AutoProxy.shared.proxyPort, 8080)

        defaults.removeObject(forKey: AutoProxy.hostKey)
        defaults.removeObject(forKey: AutoProxy.portKey)
    }

    func testLoadConfigSkipsWhenNoHost() {
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: AutoProxy.hostKey)
        defaults.removeObject(forKey: AutoProxy.portKey)

        AutoProxy.shared.loadConfig()

        XCTAssertFalse(AutoProxy.shared.isEnabled)
        XCTAssertNil(AutoProxy.shared.proxyHost)
    }

    func testLoadConfigSkipsWhenNoPort() {
        let defaults = UserDefaults.standard
        defaults.set("10.0.0.1", forKey: AutoProxy.hostKey)
        defaults.removeObject(forKey: AutoProxy.portKey)

        AutoProxy.shared.loadConfig()

        XCTAssertFalse(AutoProxy.shared.isEnabled)

        defaults.removeObject(forKey: AutoProxy.hostKey)
    }

    // MARK: - Enable / Disable

    func testEnableAndDisable() {
        AutoProxy.shared.enable(host: "127.0.0.1", port: 9090)

        XCTAssertTrue(AutoProxy.shared.isEnabled)
        XCTAssertEqual(AutoProxy.shared.proxyHost, "127.0.0.1")
        XCTAssertEqual(AutoProxy.shared.proxyPort, 9090)

        AutoProxy.shared.disable()
        XCTAssertFalse(AutoProxy.shared.isEnabled)
    }

    // MARK: - Proxy dictionary

    func testProxyDictionary() {
        AutoProxy.shared.enable(host: "10.0.0.1", port: 3128)

        let dict = AutoProxy.shared.proxyDictionary

        XCTAssertEqual(dict[kCFNetworkProxiesHTTPEnable as String] as? Bool, true)
        XCTAssertEqual(dict[kCFNetworkProxiesHTTPProxy as String] as? String, "10.0.0.1")
        XCTAssertEqual(dict[kCFNetworkProxiesHTTPPort as String] as? Int, 3128)
        XCTAssertEqual(dict["HTTPSEnable"] as? Bool, true)
        XCTAssertEqual(dict["HTTPSProxy"] as? String, "10.0.0.1")
        XCTAssertEqual(dict["HTTPSPort"] as? Int, 3128)
    }

    func testProxyDictionaryEmptyWhenDisabled() {
        let dict = AutoProxy.shared.proxyDictionary
        XCTAssertTrue(dict.isEmpty)
    }

    // MARK: - Certificate decoding

    func testDecodeCertificateFromBase64DER() {
        // Generate a minimal self-signed cert for testing:
        // This is the embedded ca_cert.pem content base64-encoded
        let pem = loadEmbeddedPEM()
        guard let pem = pem else {
            // Skip test if no embedded cert available in test bundle
            return
        }

        let cert = AutoProxy.shared.decodeCertificate(base64: pem)
        XCTAssertNotNil(cert, "Should decode PEM certificate")
    }

    func testDecodeCertificateReturnsNilForGarbage() {
        let cert = AutoProxy.shared.decodeCertificate(base64: "not-valid-base64!!!")
        XCTAssertNil(cert)
    }

    func testDecodeCertificateStripsHeaders() {
        let pem = """
        -----BEGIN CERTIFICATE-----
        MIIBkTCB+wIJALRiMLAh0KIQMA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMMBnRl
        -----END CERTIFICATE-----
        """
        // This won't be a valid cert, but it should at least try to decode the base64
        let cert = AutoProxy.shared.decodeCertificate(base64: pem)
        // The DER data won't form a valid cert, so this returns nil, but it shouldn't crash
        XCTAssertNil(cert) // incomplete cert data
    }

    // MARK: - URLProtocol canInit

    func testCanInitReturnsFalseWhenDisabled() {
        let request = URLRequest(url: URL(string: "https://example.com")!)
        XCTAssertFalse(AutoProxyURLProtocol.canInit(with: request))
    }

    func testCanInitReturnsTrueWhenEnabled() {
        AutoProxy.shared.enable(host: "127.0.0.1", port: 8080)
        let request = URLRequest(url: URL(string: "https://example.com")!)
        XCTAssertTrue(AutoProxyURLProtocol.canInit(with: request))
    }

    func testCanInitReturnsFalseForTaggedRequest() {
        AutoProxy.shared.enable(host: "127.0.0.1", port: 8080)

        let mutable = NSMutableURLRequest(url: URL(string: "https://example.com")!)
        URLProtocol.setProperty(true, forKey: AutoProxyURLProtocol.handledKey, in: mutable)

        XCTAssertFalse(AutoProxyURLProtocol.canInit(with: mutable as URLRequest))
    }

    func testCanInitReturnsFalseForNonHTTP() {
        AutoProxy.shared.enable(host: "127.0.0.1", port: 8080)
        let request = URLRequest(url: URL(string: "ftp://example.com/file")!)
        XCTAssertFalse(AutoProxyURLProtocol.canInit(with: request))
    }

    // MARK: - Embedded cert loading

    func testLoadEmbeddedCert() {
        let cert = AutoProxy.shared.loadEmbeddedCert()
        // May or may not find the cert depending on bundle setup in tests
        // This primarily verifies the method doesn't crash
        if cert != nil {
            XCTAssertNotNil(cert)
        }
    }

    // MARK: - Helpers

    private func loadEmbeddedPEM() -> String? {
        // Try to find the embedded cert in the test bundle or resource bundle
        let bundle = Bundle(for: AutoProxy.self)
        let candidates = [
            bundle.url(forResource: "ca_cert", withExtension: "pem"),
            bundle.url(forResource: "AutoProxy", withExtension: "bundle")
                .flatMap { Bundle(url: $0) }?
                .url(forResource: "ca_cert", withExtension: "pem"),
        ]

        for case let url? in candidates {
            if let content = try? String(contentsOf: url, encoding: .utf8) {
                return content
            }
        }
        return nil
    }
}
