import ArgumentParser
import Foundation

struct PatchCommand: ParsableCommand {
    static let configuration = CommandConfiguration(
        commandName: "patch",
        abstract: "Patch an IPA to route traffic through a proxy"
    )

    @Argument(help: "Path to the input IPA file")
    var input: String

    @Option(name: .long, help: "Proxy host address")
    var host: String

    @Option(name: .long, help: "Proxy port number")
    var port: Int

    @Option(name: .long, help: "Path to CA certificate (.pem)")
    var cert: String?

    @Option(name: .long, help: "Code signing identity (from `security find-identity`)")
    var identity: String?

    @Option(name: .long, help: "Path to .mobileprovision file")
    var profile: String?

    @Option(name: .long, help: "Override bundle identifier")
    var bundleId: String?

    @Flag(name: .long, help: "Don't change the bundle identifier")
    var keepBundleId = false

    @Flag(name: .long, help: "Skip code signing (for simulator or jailbroken devices)")
    var skipSigning = false

    @Option(name: .shortAndLong, help: "Output IPA path (default: <input>-patched.ipa)")
    var output: String?

    @Flag(name: .shortAndLong, help: "Suppress all output except errors")
    var quiet = false

    @Option(name: .long, help: "Path to pre-built AutoProxy.framework (default: bundled)")
    var framework: String?

    func validate() throws {
        if !skipSigning {
            guard identity != nil else {
                throw ValidationError("--identity is required (or use --skip-signing for simulator)")
            }
            guard profile != nil else {
                throw ValidationError("--profile is required (or use --skip-signing for simulator)")
            }
        }
    }

    func run() throws {
        if quiet { Logger.quiet = true }

        let inputURL = URL(fileURLWithPath: input)
        guard FileManager.default.fileExists(atPath: inputURL.path) else {
            throw PatcherError("Input IPA not found: \(inputURL.path)")
        }

        let outputURL: URL
        if let output {
            outputURL = URL(fileURLWithPath: output)
        } else {
            let name = inputURL.deletingPathExtension().lastPathComponent
            outputURL = inputURL.deletingLastPathComponent()
                .appendingPathComponent("\(name)-patched.ipa")
        }

        let profileURL: URL?
        if let profile {
            let url = URL(fileURLWithPath: profile)
            guard FileManager.default.fileExists(atPath: url.path) else {
                throw PatcherError("Provisioning profile not found: \(url.path)")
            }
            profileURL = url
        } else {
            profileURL = nil
        }

        let certURL: URL?
        if let cert {
            let url = URL(fileURLWithPath: cert)
            guard FileManager.default.fileExists(atPath: url.path) else {
                throw PatcherError("Certificate file not found: \(url.path)")
            }
            certURL = url
        } else {
            certURL = nil
        }

        let frameworkURL: URL?
        if let framework {
            let url = URL(fileURLWithPath: framework)
            guard FileManager.default.fileExists(atPath: url.path) else {
                throw PatcherError("Framework not found: \(url.path)")
            }
            frameworkURL = url
        } else {
            frameworkURL = locateBundledFramework()
        }

        Logger.info("Auto Proxy IPA Patcher")
        Logger.info("Input:    \(inputURL.path)")
        Logger.info("Output:   \(outputURL.path)")
        Logger.info("Proxy:    \(host):\(port)")
        if let identity {
            Logger.info("Identity: \(identity)")
        }
        if skipSigning {
            Logger.info("Signing:  SKIPPED")
        }

        let workDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("auto-proxy-patcher-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: workDir, withIntermediateDirectories: true)

        defer {
            try? FileManager.default.removeItem(at: workDir)
        }

        // Step 1: Unpack
        let (appDir, mainBinary) = try UnpackStep.execute(ipa: inputURL, workDir: workDir)

        // Step 2: Inject framework
        try InjectFrameworkStep.execute(appDir: appDir, frameworkURL: frameworkURL)

        // Step 3: Inject load command
        try InjectLoadCommandStep.execute(binary: mainBinary)

        // Step 4: Inject config
        try InjectConfigStep.execute(appDir: appDir, host: host, port: port, certURL: certURL)

        // Step 5: Patch bundle ID
        let newBundleId: String?
        if keepBundleId {
            newBundleId = nil
        } else {
            newBundleId = bundleId
        }
        try PatchBundleIdStep.execute(appDir: appDir, bundleId: newBundleId, keepBundleId: keepBundleId)

        // Step 6 & 7: Replace profile, extract entitlements, codesign
        if skipSigning {
            Logger.step("Step 6/8: Skipping provisioning profile (--skip-signing)")
            Logger.step("Step 7/8: Ad-hoc signing (--skip-signing)")
            try CodesignStep.adHocSign(appDir: appDir)
        } else {
            try CodesignStep.execute(
                appDir: appDir,
                identity: identity!,
                profileURL: profileURL!,
                workDir: workDir
            )
        }

        // Step 8: Repack
        try RepackStep.execute(workDir: workDir, outputURL: outputURL)

        Logger.info("Patched IPA ready: \(outputURL.path)")
    }

    private func locateBundledFramework() -> URL? {
        let execURL = URL(fileURLWithPath: CommandLine.arguments[0]).deletingLastPathComponent()
        let candidates = [
            execURL.appendingPathComponent("AutoProxy.framework"),
            execURL.appendingPathComponent("../Resources/AutoProxy.framework"),
            execURL.appendingPathComponent("../../Sources/AutoProxyPatcher/Resources/AutoProxy.framework"),
        ]
        return candidates.first { FileManager.default.fileExists(atPath: $0.path) }
    }
}

struct PatcherError: Error, CustomStringConvertible {
    let description: String
    init(_ message: String) {
        self.description = message
    }
}
