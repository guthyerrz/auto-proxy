import Foundation

enum Logger {
    static var quiet = false

    private static let reset  = "\u{001B}[0m"
    private static let green  = "\u{001B}[32m"
    private static let yellow = "\u{001B}[33m"
    private static let red    = "\u{001B}[31m"
    private static let cyan   = "\u{001B}[36m"

    static func info(_ message: String) {
        guard !quiet else { return }
        print("\(green)[✓]\(reset) \(message)")
    }

    static func warn(_ message: String) {
        guard !quiet else { return }
        print("\(yellow)[!]\(reset) \(message)")
    }

    static func error(_ message: String) {
        let stderr = FileHandle.standardError
        stderr.write(Data("\(red)[✗]\(reset) \(message)\n".utf8))
    }

    static func step(_ message: String) {
        guard !quiet else { return }
        print("\(cyan)[→]\(reset) \(message)")
    }
}

extension FileHandle: @retroactive TextOutputStream {
    public func write(_ string: String) {
        write(Data(string.utf8))
    }
}
