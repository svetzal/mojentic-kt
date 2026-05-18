# ios-spm-smoke

Manual end-to-end recipe for verifying that `mojentic-core` can be consumed from an iOS Swift application via Swift Package Manager (SPM).

> **This is a manual recipe, not a CI-driven test.** Running it requires Xcode, an Apple Developer signing identity (for device builds), and an iOS Simulator. A future release will fold this into the GitHub Actions `macos-latest` matrix; for now it lives here as a documented procedure.

## What this verifies

Kotlin Multiplatform produces a `.klib` per iOS target during normal builds, but consumers depend on an **XCFramework** packaged from those klibs. This recipe:

1. Builds the XCFramework from `mojentic-core` (and optionally one or more gateway modules) for all three iOS targets (`iosArm64` device, `iosX64` Intel simulator, `iosSimulatorArm64` Apple-silicon simulator).
2. Wires it into a small Swift project via SPM.
3. Compiles + links + runs a one-line `print` exercising a Mojentic type to confirm linkage works end-to-end.

If any step fails, the iOS distribution is broken — even if `./gradlew allTests` is green.

## Step 1 — Build the XCFramework

From `mojentic-kt/`:

```bash
./gradlew :mojentic-core:assembleMojenticCoreXCFramework
```

Output lands at:

```
mojentic-core/build/XCFrameworks/release/MojenticCore.xcframework
```

For a debug build:

```bash
./gradlew :mojentic-core:assembleMojenticCoreDebugXCFramework
```

## Step 2 — Wire the XCFramework into an SPM Package

Create a throwaway Swift package alongside this README:

```bash
mkdir -p MojenticSmoke && cd MojenticSmoke
swift package init --type executable
```

Replace `Package.swift` with:

```swift
// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "MojenticSmoke",
    platforms: [.iOS(.v14), .macOS(.v12)],
    targets: [
        .binaryTarget(
            name: "MojenticCore",
            path: "../../mojentic-core/build/XCFrameworks/release/MojenticCore.xcframework"
        ),
        .executableTarget(
            name: "MojenticSmoke",
            dependencies: ["MojenticCore"]
        ),
    ]
)
```

## Step 3 — Write the smoke executable

Replace `Sources/MojenticSmoke/main.swift` with:

```swift
import Foundation
import MojenticCore

// Exercise a couple of types reachable through the K/N → Objective-C bridge.
let userMessage = LlmMessage.companion.user(text: "Hello from Swift!")
print("Role: \\(userMessage.role)")
print("Content: \\(userMessage.content ?? "(none)")")
```

Then build:

```bash
swift build
```

Expected output:

```
Compiling MojenticSmoke main.swift
Linking MojenticSmoke
Build complete!
```

Run:

```bash
swift run MojenticSmoke
```

Expected output:

```
Role: User
Content: Hello from Swift!
```

## Step 4 — Verify on the iOS Simulator

The above runs on macOS. To verify the actual iOS slice of the XCFramework:

```bash
xcodebuild -scheme MojenticSmoke -destination 'platform=iOS Simulator,name=iPhone 15' build
```

If this succeeds, the iOS-target binaries inside the XCFramework are correctly linked.

## Available XCFramework tasks

All six library modules declare an `XCFramework` binary alongside their iOS targets. The following tasks are available:

| Module | Task |
|---|---|
| `mojentic-core` | `assembleMojenticCoreXCFramework` |
| `mojentic-ollama` | `assembleMojenticOllamaXCFramework` |
| `mojentic-openai` | `assembleMojenticOpenAiXCFramework` |
| `mojentic-anthropic` | `assembleMojenticAnthropicXCFramework` |
| `mojentic-realtime-openai` | `assembleMojenticRealtimeOpenAiXCFramework` |
| `mojentic-websearch-serpapi` | `assembleMojenticWebSearchSerpApiXCFramework` |

Each builds release + debug variants for `iosX64`, `iosArm64`, and `iosSimulatorArm64` into `<module>/build/XCFrameworks/{release,debug}/`.

## Why this is a recipe, not a Gradle subproject

The Swift toolchain — `swift package`, `xcodebuild`, the iOS simulator — is **not** something `./gradlew` knows about. The integration is "build an XCFramework with Gradle, hand it to Xcode," and the Xcode side is best driven by the standard Swift tooling. CI replicates this in a `macos-latest` job that runs the recipe verbatim.

## Future automation

A planned `samples/ios-spm-smoke/Package.swift` + `Sources/MojenticSmoke/main.swift` checked into the repo (rather than this README's inline snippets) would let CI run `swift build` against the freshly-assembled XCFramework on every push. That's deferred — the current priority is getting all four library modules' XCFrameworks declared and verified locally first.
