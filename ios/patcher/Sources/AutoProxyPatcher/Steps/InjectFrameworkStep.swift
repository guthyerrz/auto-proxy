import Foundation

enum InjectFrameworkStep {
    static func execute(appDir: URL, frameworkURL: URL?) throws {
        Logger.step("Step 2/8: Injecting AutoProxy.framework")

        guard let frameworkURL else {
            throw PatcherError(
                "AutoProxy.framework not found. Build it with `just build-framework` " +
                "or pass --framework <path>")
        }

        guard FileManager.default.fileExists(atPath: frameworkURL.path) else {
            throw PatcherError("Framework not found at \(frameworkURL.path)")
        }

        let frameworksDir = appDir.appendingPathComponent("Frameworks")
        try FileManager.default.createDirectory(at: frameworksDir, withIntermediateDirectories: true)

        let dest = frameworksDir.appendingPathComponent("AutoProxy.framework")

        // Remove existing if present (re-patching)
        if FileManager.default.fileExists(atPath: dest.path) {
            try FileManager.default.removeItem(at: dest)
        }

        try FileManager.default.copyItem(at: frameworkURL, to: dest)
        Logger.info("Copied AutoProxy.framework into Frameworks/")
    }
}
