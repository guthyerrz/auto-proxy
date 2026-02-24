// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "auto-proxy-patcher",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/apple/swift-argument-parser.git", from: "1.3.0"),
    ],
    targets: [
        .target(
            name: "COptool",
            path: "Sources/COptool",
            publicHeadersPath: "include",
            cSettings: [
                .headerSearchPath("."),
            ]
        ),
        .executableTarget(
            name: "auto-proxy-patcher",
            dependencies: [
                "COptool",
                .product(name: "ArgumentParser", package: "swift-argument-parser"),
            ],
            path: "Sources/AutoProxyPatcher",
            exclude: ["Resources"],
            swiftSettings: [
                .enableUpcomingFeature("BareSlashRegexLiterals"),
            ]
        ),
        .testTarget(
            name: "AutoProxyPatcherTests",
            dependencies: ["auto-proxy-patcher", "COptool"],
            path: "Tests/AutoProxyPatcherTests"
        ),
    ]
)
