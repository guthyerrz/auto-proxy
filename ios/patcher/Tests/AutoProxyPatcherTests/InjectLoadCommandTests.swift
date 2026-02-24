import XCTest
import COptool

final class InjectLoadCommandTests: XCTestCase {

    private var tempDir: URL!

    override func setUp() {
        super.setUp()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("optool-tests-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: tempDir)
        super.tearDown()
    }

    /// Creates a minimal 64-bit Mach-O binary with enough header padding for injection.
    private func createTestBinary() -> URL {
        let url = tempDir.appendingPathComponent("test_binary")
        var data = Data()

        // Mach-O 64-bit header
        var header = mach_header_64()
        header.magic = MH_MAGIC_64
        header.cputype = CPU_TYPE_ARM64
        header.cpusubtype = CPU_SUBTYPE_ARM64_ALL
        header.filetype = UInt32(MH_EXECUTE)
        header.ncmds = 1
        header.flags = UInt32(MH_PIE)

        // We'll have one LC_SEGMENT_64 (__TEXT) command
        let segCmdSize = MemoryLayout<segment_command_64>.size
        header.sizeofcmds = UInt32(segCmdSize)
        data.append(Data(bytes: &header, count: MemoryLayout<mach_header_64>.size))

        // __TEXT segment command
        var seg = segment_command_64()
        seg.cmd = UInt32(LC_SEGMENT_64)
        seg.cmdsize = UInt32(segCmdSize)
        withUnsafeMutableBytes(of: &seg.segname) { buf in
            let name = "__TEXT"
            name.utf8CString.withUnsafeBufferPointer { src in
                for i in 0..<min(src.count, 16) {
                    buf[i] = UInt8(bitPattern: src[i])
                }
            }
        }
        seg.vmaddr = 0x100000000
        seg.vmsize = 0x4000
        seg.fileoff = 0
        seg.filesize = 0x4000
        seg.maxprot = 5  // r-x
        seg.initprot = 5
        seg.nsects = 0
        seg.flags = 0
        data.append(Data(bytes: &seg, count: segCmdSize))

        // Pad the rest of the header area with zeros (room for injecting load commands)
        let headerEnd = MemoryLayout<mach_header_64>.size + segCmdSize
        let padding = 0x1000 - headerEnd  // page-align
        data.append(Data(count: padding))

        // Fill remaining filesize with zeros
        let remaining = Int(seg.filesize) - data.count
        if remaining > 0 {
            data.append(Data(count: remaining))
        }

        try! data.write(to: url)
        return url
    }

    func testInsertLoadDylib() {
        let binary = createTestBinary()
        let dylibPath = "@executable_path/Frameworks/AutoProxy.framework/AutoProxy"

        let result = optool_insert_load_dylib(binary.path, dylibPath)
        XCTAssertTrue(result, "Should successfully insert load command")

        // Verify it was actually inserted
        let hasCommand = optool_has_load_dylib(binary.path, dylibPath)
        XCTAssertTrue(hasCommand, "Binary should now contain the load command")
    }

    func testInsertIsIdempotent() {
        let binary = createTestBinary()
        let dylibPath = "@executable_path/Frameworks/AutoProxy.framework/AutoProxy"

        // Insert twice
        let result1 = optool_insert_load_dylib(binary.path, dylibPath)
        XCTAssertTrue(result1)

        let result2 = optool_insert_load_dylib(binary.path, dylibPath)
        XCTAssertTrue(result2, "Inserting the same load command again should succeed (idempotent)")
    }

    func testHasLoadDylibReturnsFalseForCleanBinary() {
        let binary = createTestBinary()
        let dylibPath = "@executable_path/Frameworks/AutoProxy.framework/AutoProxy"

        let hasCommand = optool_has_load_dylib(binary.path, dylibPath)
        XCTAssertFalse(hasCommand, "Clean binary should not have the load command")
    }

    func testInsertFailsForNonexistentBinary() {
        let fakePath = tempDir.appendingPathComponent("nonexistent")
        let result = optool_insert_load_dylib(fakePath.path, "foo")
        XCTAssertFalse(result, "Should fail for non-existent binary")
    }
}
