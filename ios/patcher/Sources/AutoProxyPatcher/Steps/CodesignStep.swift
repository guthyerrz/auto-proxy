import Foundation

enum CodesignStep {
    static func execute(appDir: URL, identity: String, profileURL: URL, workDir: URL) throws {
        Logger.step("Step 6/8: Replacing provisioning profile")

        // Copy mobileprovision into the app
        let embeddedProfile = appDir.appendingPathComponent("embedded.mobileprovision")
        if FileManager.default.fileExists(atPath: embeddedProfile.path) {
            try FileManager.default.removeItem(at: embeddedProfile)
        }
        try FileManager.default.copyItem(at: profileURL, to: embeddedProfile)
        Logger.info("Replaced embedded.mobileprovision")

        // Extract entitlements from the provisioning profile
        let entitlementsURL = workDir.appendingPathComponent("entitlements.plist")
        try extractEntitlements(from: profileURL, to: entitlementsURL)

        Logger.step("Step 7/8: Re-signing")

        // Sign order: frameworks → extensions → main app
        let frameworksDir = appDir.appendingPathComponent("Frameworks")
        if FileManager.default.fileExists(atPath: frameworksDir.path) {
            let frameworks = try FileManager.default.contentsOfDirectory(at: frameworksDir, includingPropertiesForKeys: nil)
            for fw in frameworks where fw.pathExtension == "framework" || fw.pathExtension == "dylib" {
                try codesign(path: fw, identity: identity, entitlements: nil)
            }
        }

        // Sign extensions (PlugIns/)
        let plugInsDir = appDir.appendingPathComponent("PlugIns")
        if FileManager.default.fileExists(atPath: plugInsDir.path) {
            let extensions = try FileManager.default.contentsOfDirectory(at: plugInsDir, includingPropertiesForKeys: nil)
            for ext in extensions where ext.pathExtension == "appex" {
                try codesign(path: ext, identity: identity, entitlements: entitlementsURL)
            }
        }

        // Sign the main app bundle
        try codesign(path: appDir, identity: identity, entitlements: entitlementsURL)
        Logger.info("Re-signed \(appDir.lastPathComponent)")
    }

    /// Ad-hoc sign the app (no identity or profile needed). Sufficient for simulators.
    static func adHocSign(appDir: URL) throws {
        // Sign frameworks first
        let frameworksDir = appDir.appendingPathComponent("Frameworks")
        if FileManager.default.fileExists(atPath: frameworksDir.path) {
            let frameworks = try FileManager.default.contentsOfDirectory(at: frameworksDir, includingPropertiesForKeys: nil)
            for fw in frameworks where fw.pathExtension == "framework" || fw.pathExtension == "dylib" {
                try codesign(path: fw, identity: "-", entitlements: nil)
            }
        }

        // Sign the main app
        try codesign(path: appDir, identity: "-", entitlements: nil)
        Logger.info("Ad-hoc signed \(appDir.lastPathComponent)")
    }

    private static func extractEntitlements(from profile: URL, to output: URL) throws {
        // Decode the CMS envelope to get the plist
        let cmsProcess = Process()
        cmsProcess.executableURL = URL(fileURLWithPath: "/usr/bin/security")
        cmsProcess.arguments = ["cms", "-D", "-i", profile.path]
        let cmsPipe = Pipe()
        cmsProcess.standardOutput = cmsPipe
        cmsProcess.standardError = FileHandle.nullDevice
        try cmsProcess.run()
        cmsProcess.waitUntilExit()

        let cmsData = cmsPipe.fileHandleForReading.readDataToEndOfFile()
        guard cmsProcess.terminationStatus == 0,
              let profilePlist = try? PropertyListSerialization.propertyList(from: cmsData, format: nil) as? [String: Any],
              let entitlements = profilePlist["Entitlements"] as? [String: Any] else {
            throw PatcherError("Failed to extract entitlements from provisioning profile")
        }

        let entData = try PropertyListSerialization.data(
            fromPropertyList: entitlements, format: .xml, options: 0)
        try entData.write(to: output)
        Logger.info("Extracted entitlements (\(entitlements.count) keys)")
    }

    private static func codesign(path: URL, identity: String, entitlements: URL?) throws {
        var args = [
            "-f", "-s", identity,
            "--timestamp=none",
        ]

        if let entitlements {
            args += ["--entitlements", entitlements.path]
        }

        args.append(path.path)

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/codesign")
        process.arguments = args
        let errPipe = Pipe()
        process.standardError = errPipe
        try process.run()
        process.waitUntilExit()

        guard process.terminationStatus == 0 else {
            let errOutput = String(data: errPipe.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
            throw PatcherError("codesign failed for \(path.lastPathComponent): \(errOutput)")
        }
    }
}
