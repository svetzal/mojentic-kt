# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Major and minor versions are synchronised with the other Mojentic ports
(`mojentic-py`, `mojentic-ts`, `mojentic-ex`, `mojentic-ru`, `mojentic-sw`);
patch versions move independently.

## [Unreleased]

## [0.3.0] - Phase 3 (in progress — slices A + B + C)

### Added — slice C: file tools + web search (2026-05-18)

- **`FilesystemGateway`** interface in
  `mojentic-core/commonMain/llm/tools/files` — sandboxed multiplatform
  file-system abstraction. Backed by **`OkioFilesystemGateway`**, a thin
  wrapper around `okio.FileSystem` that resolves every path against a
  base directory and raises `SandboxEscapeException` on `..` / absolute
  escapes. Works across JVM, Android, and iOS targets thanks to okio's
  multiplatform `FileSystem.SYSTEM`.
- **Eight file tools** in the same package, all wired to the gateway:
  `ListFilesTool`, `ReadFileTool`, `WriteFileTool`, `ListAllFilesTool`,
  `FindFilesByGlobTool`, `FindFilesContainingTool`,
  `FindLinesMatchingTool`, `CreateDirectoryTool`. The `fileToolsFor(fs)`
  factory returns all eight ready to hand to the broker. Glob patterns
  support `*`, `**`, `?`, and `[abc]` via the internal `globToRegex`
  helper.
- **`WebSearchGateway`** interface in
  `mojentic-core/commonMain/llm/tools/websearch` — vendor-agnostic
  search abstraction returning `WebSearchResult(title, link, snippet)`.
- **`OrganicWebSearchTool`** in the same package — `organic_web_search`
  LLM tool that delegates to the gateway and emits a JSON array of
  results.
- **`mojentic-websearch-serpapi`** module — Ktor-Client backed
  `SerpApiWebSearchGateway` implementation. Reads `api_key`, hits
  `serpapi.com/search.json`, parses the `organic_results` payload, and
  surfaces failures via the new `WebSearchGatewayException` (sealed
  sibling of `LlmGatewayException`).
- **Two examples**:
  - `examples/file-tool` — seeds a temp sandbox and lets the LLM
    explore it through the eight file tools.
  - `examples/web-search` — wires `OrganicWebSearchTool` into a broker
    so the LLM can answer with fresh web results.
- **okio bumped to 3.16.4** to pick up the `kotlin.time.Clock`
  migration (`okio-fakefilesystem` 3.11.0 was binary-incompatible with
  kotlinx-datetime 0.7.1's removed `kotlinx.datetime.Clock`).

### Added — slice B: user / task tools (2026-05-18)

- **`UserInteractionGateway`** interface in `mojentic-core/commonMain` —
  thin abstraction over the user-facing I/O channel; gateway
  implementations carry no logic. JVM-only
  `ConsoleUserInteractionGateway` in `mojentic-core/jvmMain` backed by
  stdin / stdout. Native / iOS consumers inject their own.
- **`AskUserTool`** in `mojentic-core/commonMain` — `ask_user` LLM tool;
  delegates I/O to the injected `UserInteractionGateway` and returns
  the user's textual answer wrapped as `{ "user_response": "..." }`.
- **`TellUserTool`** in `mojentic-core/commonMain` — `tell_user` LLM
  tool; emits a one-way message via the gateway and returns
  `{ "status": "delivered" }`.
- **`EphemeralTaskList`** in
  `mojentic-core/commonMain/llm/tools/tasks` — in-memory task list
  with a small state machine (`Pending` → `InProgress` → `Completed`).
  Mutex-protected for safe coroutine sharing.
- **Seven task tools** in the same package: `AppendTaskTool`,
  `PrependTaskTool`, `InsertTaskAfterTool`, `StartTaskTool`,
  `CompleteTaskTool`, `ListTasksTool`, `ClearTasksTool`. The
  `taskToolsFor(list)` factory returns all seven wired to one list.
- **Three examples**:
  - `examples/ask-user` — LLM uses `ask_user` to ask the user a
    clarifying question during planning.
  - `examples/tell-user` — LLM emits intermediate updates while
    answering.
  - `examples/ephemeral-task-manager` — LLM plans a multi-step task
    using the seven task tools, prints the final task list.
- Quality gate green: ktlint + Detekt clean; **261 tests** on JVM,
  Android-host, and iOS-simulator (up from 198).

### Added — slice A: Tracer + ParallelToolRunner (2026-05-18)

- **Full `TracerSystem`** in `mojentic-core/commonMain`:
  - `TracerEvent` sealed interface with `LlmCallEvent`, `LlmResponseEvent`,
    `ToolCallEvent`, `ToolBatchEvent`, `AgentInteractionEvent` variants,
    each carrying a `kotlin.time.Instant` timestamp and a correlation ID
    plus a `printableSummary()` formatter for examples and demos.
  - `EventStore` — `Mutex`-protected append-only buffer plus a hot
    `SharedFlow<TracerEvent>` for live consumers, with type / time-window /
    custom-predicate filters and a `getLastN` helper.
  - `TracerSystem : Tracer` forwarding every recorder call to the
    underlying store; `enable()` / `disable()` toggle recording without
    touching wiring.
- **`ParallelToolRunner`** in `mojentic-core/commonMain/llm/tools`:
  - Opt-in alternative to `SerialToolRunner` — fans tool calls out onto
    child coroutines via `coroutineScope { ... awaitAll() }` and waits.
  - Emits a single `ToolBatchEvent` per batch (batchId, tool names,
    success / failure counts, wall-clock duration) so observers can
    quantify parallelism gains. Per-call `ToolCallEvent`s still land
    individually.
  - Cancellation propagates cooperatively through `coroutineScope`.
- **`Tracer` interface extensions**:
  - `recordToolBatch(...)` — emitted by `ParallelToolRunner`; serial
    runners do not emit batch events.
  - `recordAgentInteraction(...)` — placeholder for Phase 4 dispatcher
    integration. Declared now so consumers can register interest.
  - All `Tracer` methods are now `suspend` so the underlying `EventStore`
    can take its `Mutex` cleanly; the broker already calls every recorder
    from a suspend context.
- **`ToolRunner.runBatch` correlationId parameter** — threads through to
  `ParallelToolRunner`'s batch event so it correlates with the
  originating LLM call. Backward compatible via a default-null argument.
- **`tracer-demo` example** — wires `TracerSystem` + `ParallelToolRunner`
  + `CurrentDateTimeTool` into `LlmBroker` and prints every recorded
  event after the run.

### Changed

- Bumped version coordinate from `0.2.0-SNAPSHOT` to `0.3.0-SNAPSHOT`.

## [0.2.0] - Phase 2

### Added

- **`ChatSession`** in `mojentic-core/commonMain`:
  - Owns the message history behind a `Mutex` so the session is safe to
    share across coroutines.
  - `suspend send(message): LlmGatewayResponse` and
    `fun stream(message): Flow<StreamEvent>` mirror the broker surfaces;
    history is updated atomically on success and rolled back on failure.
  - Optional system prompt and default tool list; `messages()` snapshot
    and `reset()` preserve-system helpers.
- **`mojentic-openai` module** with `OpenAIGateway`:
  - Ktor-Client-based gateway against the OpenAI chat-completions API
    (OkHttp engine on JVM/Android, Darwin engine on iOS).
  - `complete`, `completeJson` (uses `response_format: json_schema`),
    streaming via SSE with parallel-tool-call accumulation through a
    dedicated `StreamingToolCallAccumulator`, `availableModels`.
  - Multimodal `LlmMessage` content serialised as the OpenAI `content`
    array shape (`{type: text}` + `{type: image_url, image_url: {url: ...}}`).
  - `reasoning_effort` plumbed through for the `o*` model family; the
    gateway automatically swaps `max_tokens` for `max_completion_tokens`
    and omits `temperature` on reasoning models.
- **`OpenAIModelRegistry`** — static metadata about chat models (context
  window, supports-tools, supports-vision, supports-reasoning-effort).
- **`OpenAIEmbeddingsGateway`** — thin wrapper around `POST /v1/embeddings`
  implementing the new `EmbeddingsGateway` interface.
- **`TokenizerGateway`** interface in `mojentic-core/commonMain`. JVM-only
  `JtokkitTokenizerGateway` in `mojentic-openai/jvmMain` backed by
  jtokkit; Kotlin/Native consumers can plug in their own implementation.
- **`EmbeddingsGateway`** interface in `mojentic-core/commonMain`.
- **Phase 2 examples** under `examples/`: `broker-examples`,
  `chat-session`, `chat-session-with-tool`, `image-analysis`,
  `embeddings`. JVM-only Gradle subprojects runnable via
  `./gradlew :examples:<name>:run`.
- **Detekt across all KMP source sets** — the umbrella `detekt` task now
  depends on the per-target `detektJvmMain` / `detektIosArm64Main` / …
  tasks the plugin generates, so `./gradlew detekt` actually scans every
  source set instead of reporting `NO-SOURCE`. Closes a Phase 1 deferred
  item.

### Changed

- Bumped version coordinate from `0.1.0-SNAPSHOT` to `0.2.0-SNAPSHOT`.
- `detekt.yml`: lifted `MaxLineLength` to 140; disabled
  `TooGenericExceptionCaught` and `InstanceOfCheckForException` (the
  broker / tool runner intentionally catches `Throwable` to honour
  cooperative cancellation by re-throwing `CancellationException`);
  raised `LoopWithTooManyJumpStatements.maxJumpCount` to 3.

### Tooling

- Added jtokkit (`com.knuddels:jtokkit:1.1.0`) as a JVM-only dependency of
  `mojentic-openai`.

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
