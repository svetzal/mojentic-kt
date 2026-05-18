# Mojentic

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin 2.0+](https://img.shields.io/badge/Kotlin-2.0%2B-purple)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/platforms-JVM%20%7C%20Android%20%7C%20iOS-blue)](settings.gradle.kts)

A modern LLM integration framework for Kotlin Multiplatform (KMP) targeting
JVM, Android, and iOS — with the same library, same API, and same semantics
on every platform.

Mojentic provides a clean abstraction over multiple LLM providers with tool
support, structured output, streaming, an event-driven agent system, and
realtime voice — all built natively on Kotlin coroutines and `Flow`.

> **Status: Phase 0 — skeleton.** This repository currently contains only the
> Kotlin Multiplatform project scaffolding (Gradle build, target configuration,
> quality gates, smoke test). The actual LLM features land in Phase 1+ — see
> `KOTLIN.md` in the `mojentic-unify` monorepo for the full plan, roadmap, and
> parity-target rationale.

## Planned Features

- **🔌 Multi-Provider Support**: Ollama, OpenAI, and Anthropic gateways
- **⚡ Coroutine-First**: `suspend` + `Flow` end to end; structured concurrency
  throughout; cooperative cancellation
- **🛠️ Tool System**: Extensible tool calling with automatic recursive
  execution, serial (default) or parallel runners
- **📊 Structured Output**: Type-safe `@Serializable`-derived JSON Schema
- **🌊 Streaming**: Cold `Flow<StreamEvent>` with full recursive tool execution
- **🔍 Tracer System**: Observability via `MutableSharedFlow` of `TracerEvent`,
  correlation IDs threaded through nested broker / tool calls
- **🤖 Agent System**: Event-driven multi-agent coordination with the ReAct
  pattern and shared working memory
- **🎙️ Realtime Voice**: OpenAI Realtime API over Ktor `WebSockets` with
  server / manual VAD and barge-in
- **🧩 Per-Provider Modules**: `mojentic-core`, `mojentic-ollama`,
  `mojentic-openai`, `mojentic-anthropic`, `mojentic-realtime-openai` — apps
  pull only what they use

## Requirements

- **Kotlin 2.0+** (K2 compiler, multiplatform target shape, standard-library
  `Uuid`)
- **JDK 17+** for the build
- **Android**: minimum API 24 (Android 7.0)
- **iOS**: minimum deployment target iOS 14
- **Gradle**: 8.10+ (managed via the wrapper)

## Building Locally

The Gradle wrapper jar is intentionally not committed. On a fresh clone, run
once:

```bash
gradle wrapper --gradle-version 8.10.2 --distribution-type bin
```

After that, the wrapper is self-contained:

```bash
./gradlew build              # compile + test all targets the host can build
./gradlew :mojentic-core:jvmTest
./gradlew ktlintCheck detekt
```

CI runs the full quality-gate matrix on every push — see
`.github/workflows/build.yml`.

## Installation

> Not yet published. Targeted for the **Phase 0 → Phase 1** transition.

Once published to Maven Central:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.mojentic:mojentic-core:<version>")
    // Add gateways as they ship:
    // implementation("com.mojentic:mojentic-ollama:<version>")
    // implementation("com.mojentic:mojentic-openai:<version>")
}
```

For iOS consumption via Swift Package Manager, an XCFramework + `Package.swift`
manifest will be published alongside each tagged release.

## Documentation

Dokka HTML output will be published to GitHub Pages on every `v*` tag.
Handwritten use-case guides will live under `docs/use-cases/`.

## License

MIT — see [LICENSE](LICENSE).
