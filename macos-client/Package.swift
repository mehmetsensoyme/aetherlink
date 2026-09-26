// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AetherLinkMac",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "AetherLink", targets: ["AetherLink"])
    ],
    dependencies: [],
    targets: [
        .executableTarget(
            name: "AetherLink",
            dependencies: [],
            path: "Sources"
        )
    ]
)
