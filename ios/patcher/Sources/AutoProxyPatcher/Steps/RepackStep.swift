import Foundation

enum RepackStep {
    static func execute(workDir: URL, outputURL: URL) throws {
        Logger.step("Step 8/8: Repackaging IPA")

        // Remove existing output file
        if FileManager.default.fileExists(atPath: outputURL.path) {
            try FileManager.default.removeItem(at: outputURL)
        }

        // Create parent directory if needed
        let parentDir = outputURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: parentDir, withIntermediateDirectories: true)

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/zip")
        process.arguments = ["-r", "-q", outputURL.path, "Payload"]
        process.currentDirectoryURL = workDir
        try process.run()
        process.waitUntilExit()

        guard process.terminationStatus == 0 else {
            throw PatcherError("Failed to create IPA (zip exit code \(process.terminationStatus))")
        }

        // Report file size
        if let attrs = try? FileManager.default.attributesOfItem(atPath: outputURL.path),
           let size = attrs[.size] as? UInt64 {
            let mb = Double(size) / 1_048_576.0
            Logger.info("Created IPA: \(String(format: "%.1f", mb)) MB")
        }
    }
}
