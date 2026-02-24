// optool_wrapper.m — Thin wrapper exposing optool's core to Swift
//
// optool is Copyright (c) 2014, Alex Zielenski — BSD-2-Clause

#import "include/optool.h"
#import "headers.h"
#import "operations.h"

BOOL optool_insert_load_dylib(NSString *binaryPath, NSString *dylibPath) {
    NSMutableData *binary = [NSMutableData dataWithContentsOfFile:binaryPath];
    if (!binary) {
        NSLog(@"optool: failed to read binary at %@", binaryPath);
        return NO;
    }

    struct thin_header headers[4];
    uint32_t numHeaders = 0;
    headersFromBinary(headers, binary, &numHeaders);

    if (numHeaders == 0) {
        NSLog(@"optool: no compatible architectures found in %@", binaryPath);
        return NO;
    }

    BOOL allSucceeded = YES;
    for (uint32_t i = 0; i < numHeaders; i++) {
        if (!insertLoadEntryIntoBinary(dylibPath, binary, headers[i], LC_LOAD_DYLIB)) {
            NSLog(@"optool: failed to insert load command for architecture slice %u", i);
            allSucceeded = NO;
        }
    }

    if (allSucceeded) {
        if (![binary writeToFile:binaryPath atomically:YES]) {
            NSLog(@"optool: failed to write patched binary to %@", binaryPath);
            return NO;
        }
    }

    return allSucceeded;
}

BOOL optool_has_load_dylib(NSString *binaryPath, NSString *dylibPath) {
    NSData *binary = [NSData dataWithContentsOfFile:binaryPath];
    if (!binary) {
        return NO;
    }

    struct thin_header headers[4];
    uint32_t numHeaders = 0;
    headersFromBinary(headers, (NSData *)binary, &numHeaders);

    if (numHeaders == 0) {
        return NO;
    }

    // Check if any architecture slice has the load command
    NSMutableData *mutableBinary = [binary mutableCopy];
    for (uint32_t i = 0; i < numHeaders; i++) {
        uint32_t offset = 0;
        if (binaryHasLoadCommandForDylib(mutableBinary, dylibPath, &offset, headers[i])) {
            return YES;
        }
    }

    return NO;
}
