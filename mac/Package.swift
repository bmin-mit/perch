// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "perch",
    platforms: [.macOS(.v14)],
    targets: [
        .executableTarget(
            name: "perch",
            path: "Sources/perch",
            swiftSettings: [.swiftLanguageMode(.v5)]
        ),
    ]
)
