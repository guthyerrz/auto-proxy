// optool.h — Public C header for Swift interop
// Thin wrapper around optool's insertLoadEntryIntoBinary()
//
// optool is Copyright (c) 2014, Alex Zielenski — BSD-2-Clause
// https://github.com/alexzielenski/optool

#import <Foundation/Foundation.h>

/// Insert an LC_LOAD_DYLIB load command into a Mach-O binary.
/// Handles both thin and FAT (universal) binaries.
///
/// @param binaryPath  Path to the Mach-O executable on disk.
/// @param dylibPath   The dylib install name to inject (e.g. @executable_path/Frameworks/Foo.framework/Foo).
/// @return YES if the load command was inserted (or already existed) for every architecture slice.
BOOL optool_insert_load_dylib(NSString *_Nonnull binaryPath, NSString *_Nonnull dylibPath);

/// Check if a Mach-O binary already contains a load command for the given dylib.
///
/// @param binaryPath  Path to the Mach-O executable on disk.
/// @param dylibPath   The dylib install name to check for.
/// @return YES if at least one architecture slice already has the load command.
BOOL optool_has_load_dylib(NSString *_Nonnull binaryPath, NSString *_Nonnull dylibPath);
