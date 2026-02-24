import Foundation

enum PatchBundleIdStep {
    static func execute(appDir: URL, bundleId: String?, keepBundleId: Bool) throws {
        Logger.step("Step 5/8: Patching bundle identifier")

        if keepBundleId {
            Logger.info("Keeping original bundle identifier (--keep-bundle-id)")
            return
        }

        let infoPlistURL = appDir.appendingPathComponent("Info.plist")
        guard var plist = readPlist(at: infoPlistURL) else {
            throw PatcherError("Failed to read Info.plist")
        }

        let originalId = plist["CFBundleIdentifier"] as? String ?? "unknown"

        let newId: String
        if let bundleId {
            newId = bundleId
        } else {
            // Auto-generate: com.patched.<original>
            newId = "com.patched.\(originalId)"
        }

        plist["CFBundleIdentifier"] = newId
        try writePlist(plist, to: infoPlistURL)

        Logger.info("Bundle ID: \(originalId) → \(newId)")
    }

    // MARK: - Plist helpers (also used by tests)

    static func readPlist(at url: URL) -> [String: Any]? {
        guard let data = try? Data(contentsOf: url),
              let plist = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any] else {
            return nil
        }
        return plist
    }

    static func writePlist(_ plist: [String: Any], to url: URL) throws {
        let data = try PropertyListSerialization.data(
            fromPropertyList: plist, format: .xml, options: 0)
        try data.write(to: url)
    }
}
