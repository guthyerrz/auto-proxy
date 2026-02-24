import XCTest
@testable import auto_proxy_patcher

final class InjectConfigTests: XCTestCase {

    private var tempDir: URL!

    override func setUp() {
        super.setUp()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("config-tests-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: tempDir)
        super.tearDown()
    }

    private func createMockAppDir() throws -> URL {
        let appDir = tempDir.appendingPathComponent("Test.app")
        let fwDir = appDir
            .appendingPathComponent("Frameworks")
            .appendingPathComponent("AutoProxy.framework")
        try FileManager.default.createDirectory(at: fwDir, withIntermediateDirectories: true)
        return appDir
    }

    func testWritesProxyConfig() throws {
        let appDir = try createMockAppDir()

        try InjectConfigStep.execute(appDir: appDir, host: "10.0.20.205", port: 777, certURL: nil)

        let configURL = appDir
            .appendingPathComponent("Frameworks")
            .appendingPathComponent("AutoProxy.framework")
            .appendingPathComponent("AutoProxy.bundle")
            .appendingPathComponent("proxy_config.plist")

        XCTAssertTrue(FileManager.default.fileExists(atPath: configURL.path))

        let data = try Data(contentsOf: configURL)
        let plist = try PropertyListSerialization.propertyList(from: data, format: nil) as! [String: Any]

        XCTAssertEqual(plist["ProxyHost"] as? String, "10.0.20.205")
        XCTAssertEqual(plist["ProxyPort"] as? Int, 777)
    }

    func testCopiesCertificate() throws {
        let appDir = try createMockAppDir()

        // Create a fake cert file
        let certURL = tempDir.appendingPathComponent("test.pem")
        try "-----BEGIN CERTIFICATE-----\nMIIBfake\n-----END CERTIFICATE-----".write(to: certURL, atomically: true, encoding: .utf8)

        try InjectConfigStep.execute(appDir: appDir, host: "localhost", port: 8080, certURL: certURL)

        let certDest = appDir
            .appendingPathComponent("Frameworks")
            .appendingPathComponent("AutoProxy.framework")
            .appendingPathComponent("AutoProxy.bundle")
            .appendingPathComponent("ca_cert.pem")

        XCTAssertTrue(FileManager.default.fileExists(atPath: certDest.path))

        let contents = try String(contentsOf: certDest, encoding: .utf8)
        XCTAssertTrue(contents.contains("MIIBfake"))
    }

    func testCreatesBundleDirectory() throws {
        let appDir = try createMockAppDir()

        let bundleDir = appDir
            .appendingPathComponent("Frameworks")
            .appendingPathComponent("AutoProxy.framework")
            .appendingPathComponent("AutoProxy.bundle")

        XCTAssertFalse(FileManager.default.fileExists(atPath: bundleDir.path))

        try InjectConfigStep.execute(appDir: appDir, host: "example.com", port: 9090, certURL: nil)

        XCTAssertTrue(FileManager.default.fileExists(atPath: bundleDir.path))
    }
}
