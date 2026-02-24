import Foundation

enum UnpackStep {
    /// Unzips an IPA into a work directory, locates the .app bundle and main binary.
    /// Returns (appDir, mainBinaryURL).
    static func execute(ipa: URL, workDir: URL) throws -> (URL, URL) {
        Logger.step("Step 1/8: Unpacking IPA")

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/unzip")
        process.arguments = ["-o", "-q", ipa.path, "-d", workDir.path]
        try process.run()
        process.waitUntilExit()

        guard process.terminationStatus == 0 else {
            throw PatcherError("Failed to unzip IPA (exit code \(process.terminationStatus))")
        }

        let payloadDir = workDir.appendingPathComponent("Payload")
        guard FileManager.default.fileExists(atPath: payloadDir.path) else {
            throw PatcherError("IPA does not contain a Payload directory")
        }

        // Find *.app inside Payload/
        let contents = try FileManager.default.contentsOfDirectory(
            at: payloadDir, includingPropertiesForKeys: nil)
        guard let appDir = contents.first(where: { $0.pathExtension == "app" }) else {
            throw PatcherError("No .app bundle found in Payload/")
        }

        // Read Info.plist to get CFBundleExecutable
        let infoPlist = appDir.appendingPathComponent("Info.plist")
        guard let plistData = try? Data(contentsOf: infoPlist),
              let plist = try? PropertyListSerialization.propertyList(from: plistData, format: nil) as? [String: Any],
              let executableName = plist["CFBundleExecutable"] as? String else {
            throw PatcherError("Failed to read CFBundleExecutable from Info.plist")
        }

        let mainBinary = appDir.appendingPathComponent(executableName)
        guard FileManager.default.fileExists(atPath: mainBinary.path) else {
            throw PatcherError("Main binary not found: \(mainBinary.lastPathComponent)")
        }

        Logger.info("Found app: \(appDir.lastPathComponent) (binary: \(executableName))")
        return (appDir, mainBinary)
    }
}
