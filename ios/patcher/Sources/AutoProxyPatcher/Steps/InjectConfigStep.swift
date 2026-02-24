import Foundation

enum InjectConfigStep {
    static func execute(appDir: URL, host: String, port: Int, certURL: URL?) throws {
        Logger.step("Step 4/8: Writing proxy configuration")

        // The config goes inside AutoProxy.framework's resource bundle
        let bundleDir = appDir
            .appendingPathComponent("Frameworks")
            .appendingPathComponent("AutoProxy.framework")
            .appendingPathComponent("AutoProxy.bundle")

        try FileManager.default.createDirectory(at: bundleDir, withIntermediateDirectories: true)

        // Write proxy_config.plist
        let config: [String: Any] = [
            "ProxyHost": host,
            "ProxyPort": port,
        ]
        let plistData = try PropertyListSerialization.data(
            fromPropertyList: config,
            format: .xml,
            options: 0
        )
        let plistURL = bundleDir.appendingPathComponent("proxy_config.plist")
        try plistData.write(to: plistURL)
        Logger.info("Wrote proxy_config.plist (\(host):\(port))")

        // Copy CA certificate
        if let certURL {
            let certDest = bundleDir.appendingPathComponent("ca_cert.pem")
            if FileManager.default.fileExists(atPath: certDest.path) {
                try FileManager.default.removeItem(at: certDest)
            }
            try FileManager.default.copyItem(at: certURL, to: certDest)
            Logger.info("Injected CA certificate from \(certURL.lastPathComponent)")
        } else {
            Logger.warn("No CA certificate provided — SDK will use its embedded default")
        }
    }
}
