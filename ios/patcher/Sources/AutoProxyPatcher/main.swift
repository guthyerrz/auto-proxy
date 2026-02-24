import ArgumentParser
import Foundation

struct AutoProxyPatcherCLI: ParsableCommand {
    static let configuration = CommandConfiguration(
        commandName: "auto-proxy-patcher",
        abstract: "Auto Proxy IPA Patcher — inject proxy config into compiled iOS apps",
        subcommands: [PatchCommand.self]
    )
}

AutoProxyPatcherCLI.main()
