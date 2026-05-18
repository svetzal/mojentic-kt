# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Major and minor versions are synchronised with the other Mojentic ports
(`mojentic-py`, `mojentic-ts`, `mojentic-ex`, `mojentic-ru`, `mojentic-sw`);
patch versions move independently.

## [Unreleased]

## [0.1.0] - Phase 1

### Added

- **Core LLM types** in `mojentic-core/commonMain`:
  - `LlmMessage` with `system`/`user`/`assistant`/`tool` factory constructors,
    plus multimodal `MessageContent` parts (`TextContent`, `ImageContent`).
  - `LlmToolCall`, `LlmGatewayResponse`, `MessageRole`.
  - `CompletionConfig` (temperature, num_ctx, max_tokens, num_predict,
    reasoning_effort, max_tool_iterations) mirroring the Python reference.
  - `ReasoningEffort` enum (LOW/MEDIUM/HIGH).
  - `MojenticException` sealed hierarchy with `LlmGatewayException`,
    `MaxToolIterationsExceededException`, `ToolExecutionException`.
  - Public correlation IDs are opaque strings so the API surface does not
    depend on still-experimental `kotlin.uuid.Uuid`.
- **`LlmGateway` interface** with `suspend complete`, `suspend completeJson`,
  `stream(): Flow<GatewayStreamEvent>`, and `availableModels()`.
  - `GatewayStreamEvent` sealed interface: `Content`, `Thinking`, `ToolCalls`,
    `Raw`.
  - `StreamEvent` sealed interface for broker callers: `TextChunk`,
    `ThinkingChunk`, `ToolCall`, `ToolResult`.
- **`LlmBroker` coordinator**:
  - `suspend complete(...)` with recursive tool execution.
  - `suspend inline <reified T> completeJson(...)` deriving the JSON schema
    from `T`'s `SerialDescriptor` (no KSP/codegen pipeline required).
  - `stream(...): Flow<StreamEvent>` with recursive tool execution between
    streaming completions.
  - Honours `CompletionConfig.maxToolIterations` as a hard recursion ceiling.
- **Tool system**:
  - `LlmTool` interface (`suspend execute`) + `ToolDescriptor`.
  - `ToolRunner` interface with `SerialToolRunner` (broker default).
  - `ToolOutcome` carries result, error, and wall-clock duration.
  - `CurrentDateTimeTool` (kotlinx-datetime based).
  - `DateResolverTool` (minimal multiplatform parser covering today / tomorrow /
    yesterday / in N (days|weeks|months|years) / N ... ago / next-or-last weekday /
    ISO-8601 literal passthrough). Documented as a Phase-1 minimal alternative
    to the Python `parsedatetime` parser.
- **`Tracer` interface + `NullTracer` object** — broker integration points; the
  full EventStore-backed tracer ships in Phase 3.
- **`JsonSchemaGenerator`** in `internal/` — walks a `SerialDescriptor` and
  produces a JSON-Schema-shaped `JsonObject` for primitives, objects, lists,
  maps, and enums.
- **`mojentic-ollama` module** with `OllamaGateway`:
  - Ktor-Client-based HTTP transport (OkHttp engine on JVM/Android, Darwin
    engine on iOS).
  - `complete`, `completeJson`, `stream`, `availableModels`.
  - Streaming uses Ollama's NDJSON `stream: true` mode line-by-line.
  - Tool calls and reasoning traces (`think: true`) surfaced through the
    neutral types.
  - Message and tool adapters live in `OllamaMessageAdapter.kt`.
- **Phase 1 examples** under `examples/`: `simple-llm`, `list-models`,
  `simple-structured`, `simple-tool`, `streaming`. Each is a JVM-only Gradle
  subproject runnable via `./gradlew :examples:<name>:run`.
- **Tests**: 35+ tests across `kotlin.test` + `kotlinx-coroutines-test` + Turbine +
  Ktor `MockEngine`. Cover message factories, completion config, tool runner,
  date resolver, JSON schema generator, broker (with stub gateway), and Ollama
  gateway (with mock HTTP).

### Changed

- Bumped version coordinate from `0.0.1-SNAPSHOT` to `0.1.0-SNAPSHOT`.
- Removed the Phase 0 `Mojentic.greet(...)` smoke surface; the `Mojentic`
  object now only exposes `VERSION`.
- Ktlint config disables `class-signature`, `function-signature`, and
  `function-expression-body` rules to preserve the project's preferred
  multi-line trailing-comma style.

### Tooling

- Added `kotlin-jvm` plugin to the version catalog so the `examples/*` JVM
  subprojects can use the standard `application` plugin pattern.
- Added `slf4j-simple` test dependency (jvmTest + androidHostTest source sets)
  so `kotlin-logging` resolves at test runtime.

## [0.0.1] - Phase 0

### Added

- Phase 0 skeleton: Kotlin Multiplatform project with JVM, Android, and iOS
  (x64, arm64, simulatorArm64) targets configured via the `kotlin-multiplatform`
  plugin in `mojentic-core`. Android target uses AGP 9.x's KMP-native
  `com.android.kotlin.multiplatform.library` plugin (the legacy
  `com.android.library` plugin is incompatible with KMP from AGP 9.0).
- Version catalog (`gradle/libs.versions.toml`) pinning the May-2026 stable
  toolchain: Kotlin 2.3.21, Gradle 9.5.1, AGP 9.2.0, Ktor 3.4.2,
  kotlinx-coroutines 1.10.2, kotlinx-serialization 1.8.1, kotlinx-datetime
  0.7.1, okio 3.11.0, kotlin-logging 7.0.7, Dokka 2.0.0,
  binary-compatibility-validator 0.18.1, Kover 0.9.1, ktlint plugin 13.0.0,
  Detekt 1.23.8.
- Quality-gate plugin wiring: ktlint + Detekt (with `detekt.yml`).
- Smoke-test target (`MojenticTest`) using `kotlin.test` to verify the
  Hello-level surface compiles and runs on all configured platforms.
- Project documentation files: `README.md`, `CHARTER.md`, `AGENTS.md`,
  `CLAUDE.md`, `CHANGELOG.md`, `LICENSE`, `.editorconfig`, `.gitignore`,
  `detekt.yml`.
- Committed Gradle wrapper (`gradlew`, `gradlew.bat`,
  `gradle/wrapper/gradle-wrapper.jar` + `.properties` at 9.5.1) so fresh
  clones build immediately without a system Gradle install.
- CI workflow (GitHub Actions) running ktlint, Detekt, build, and tests on
  Linux (JVM/Android targets) and macOS (iOS targets).
