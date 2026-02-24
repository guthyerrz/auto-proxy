import Foundation
import COptool

enum InjectLoadCommandStep {
    static let dylibPath = "@executable_path/Frameworks/AutoProxy.framework/AutoProxy"

    static func execute(binary: URL) throws {
        Logger.step("Step 3/8: Injecting LC_LOAD_DYLIB load command")

        let result = optool_insert_load_dylib(binary.path, dylibPath)
        guard result else {
            throw PatcherError(
                "Failed to inject load command into \(binary.lastPathComponent). " +
                "There may not be enough space in the Mach-O header padding.")
        }

        Logger.info("Injected load command: \(dylibPath)")
    }
}
