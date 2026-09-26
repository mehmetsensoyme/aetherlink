// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AetherLinkMac",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "AetherLink", targets: ["AetherLink"]),
        .executable(name: "AetherLinkWidget", targets: ["AetherLinkWidget"])
    ],
    dependencies: [],
    targets: [
        .target(
            name: "AetherShared",
            dependencies: [],
            path: "Shared"
        ),
        .executableTarget(
            name: "AetherLink",
            dependencies: ["AetherShared"],
            path: "Sources"
        ),
        .executableTarget(
            name: "AetherLinkWidget",
            dependencies: ["AetherShared"],
            path: "WidgetExtension",
            exclude: ["Info.plist", "WidgetExtension.entitlements"]
        )
    ]
)
