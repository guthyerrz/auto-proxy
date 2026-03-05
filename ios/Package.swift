// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AutoProxy",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "AutoProxy", targets: ["AutoProxy"]),
    ],
    targets: [
        .target(
            name: "AutoProxy",
            path: "Sources/AutoProxy",
            resources: [.copy("Resources/ca_cert.pem")],
            publicHeadersPath: "include"
        ),
        .testTarget(
            name: "AutoProxyTests",
            dependencies: ["AutoProxy"],
            path: "Tests/AutoProxyTests"
        ),
    ]
)
