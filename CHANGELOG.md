# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Major and minor versions are synchronised with the other Mojentic ports
(`mojentic-py`, `mojentic-ts`, `mojentic-ex`, `mojentic-ru`, `mojentic-sw`);
patch versions move independently.

## [Unreleased]

## [0.5.0] - Phase 5 ✅ Shipped (2026-05-18)

Realtime voice — first cross-port realtime stack. `mojentic-realtime-openai`
adds a duplex WebSocket gateway against OpenAI Realtime; `mojentic-core` adds
the vendor-neutral types (`RealtimeVoiceBroker`, `RealtimeSession`,
`RealtimeEvent` sealed union, `AudioFrame`, `VadConfig`, `RealtimeVoiceConfig`)
that the broker layer normalises raw provider events into. Parallel tool
calls during voice turns reuse the Phase 3 `ParallelToolRunner`. Barge-in
cancels the in-flight response coroutine and the pending tool batch.

### Added — slice C: audio frames, VAD modes, barge-in (2026-05-18)

- **`RealtimeSession.sendAudio(Flow<AudioFrame>)`** streams PCM16 frames
  into the session's input buffer as `input_audio_buffer.append` events.
- **`RealtimeSession.commit()`** explicitly closes the input audio buffer
  and requests a response — meaningful under `VadConfig.Manual`
  (push-to-talk); harmless redundant signal under server VAD.
- **`RealtimeSession.clearAudio()`** drops pending audio without requesting
  a response.
- **Barge-in**: when `UserSpeechStarted` lands mid-assistant-turn, the
  session cancels the active tool-dispatch job, sends
  `ClientRealtimeEvent.ResponseCancel`, and emits a
  `RealtimeEvent.Interrupted(reason = BargeIn)`. The cancellation flows
  through the same coroutine `Job.cancel()` machinery used everywhere
  else — no separate cancellation primitive.
- **Tool dispatch moved to its own `Job`** owned by the session so barge-in
  can abort it cleanly; the dispatch logic itself (parallel runner, submit
  outputs, request follow-up response) is unchanged from slice B.
- **`AssistantAudioDelta` end-to-end**: the normaliser decodes
  `response.audio.delta` base64 PCM16 payloads into `AudioFrame`s via the
  shared `Pcm16AudioCodec`, so consumers never touch base64.
- **Example**: `examples/realtime-text` — JVM-only smoke runner that
  connects to OpenAI Realtime in text-only modality and prints streaming
  deltas (requires `OPENAI_API_KEY`).
- **Tests**: six new `RealtimeAudioAndVadTest` cases covering audio
  streaming, manual commit, buffer clear, barge-in trigger, no-op
  speech-started before turn, and audio-delta decoding.
- Quality gate: ktlint + Detekt clean, `./gradlew build allTests` green on
  JVM + Android-host + iOS-simulator.

### Added — slice B: RealtimeVoiceBroker + RealtimeSession (text mode, parallel tools) (2026-05-18)

- **`RealtimeVoiceBroker`** — coordinator above the `RealtimeGateway`,
  composing a `ToolRunner` (defaults to `ParallelToolRunner` because voice
  turns can legitimately emit several tool calls concurrently) and a
  `Tracer`. Opens sessions on a caller-supplied `CoroutineScope`.
- **`RealtimeSession`** — stateful per-connection handle. Exposes
  `events: Flow<RealtimeEvent>` (vendor-neutral, replay-buffered), a power-user
  `rawEvents: Flow<JsonObject>` escape hatch, `sendText`, `interrupt`,
  `close`, and `awaitTurnCompleted`. Demultiplexes raw provider events
  through `RealtimeEventNormalizer` into the union and dispatches tool
  calls via the broker's runner after each `response.done`. Function
  outputs are submitted followed by `response.create` for the follow-up.
- **`RealtimeEventNormalizer`** (internal) — translates OpenAI wire events
  into the vendor-neutral union; reassembles streamed function-call
  arguments before dispatch.
- **Pcm16 codec moved to `mojentic-core/realtime/internal`** so the broker
  can decode audio deltas without depending on the OpenAI module.
- **Tests**: eight `RealtimeSessionTest` cases using a `FakeRealtimeGateway`
  that replays scripted server events through a Channel-backed flow,
  covering text deltas, transcript completion, parallel tool dispatch,
  error events, interruption, and session close.

### Added — slice A: core realtime types + OpenAI gateway skeleton (2026-05-18)

- **Core realtime types** in `mojentic-core/commonMain/realtime/`:
  - `RealtimeVoiceConfig` — cross-port subset (instructions, voice,
    modalities, audio format, VAD, tools, tool-choice, temperature, token
    cap, transcription model, provider-extras escape hatch).
  - `VadConfig` sealed interface with `Server(threshold, prefixPaddingMs,
    silenceDurationMs)` and `Manual` variants.
  - `AudioFrame(samples: ShortArray, sampleRateHz: Int)` — vendor-neutral
    PCM16 frame; default 24 kHz mono matches OpenAI Realtime's default.
  - `RealtimeEvent` sealed interface — full event union: session lifecycle,
    user turn (speech + transcript deltas + final), assistant turn (text
    delta + final, transcript delta + final, audio delta, completion with
    token usage), tool calls (parallel-aware: started, args delta,
    dispatched, completed, failed, batch submitted), control (interrupted,
    rate-limited, gateway error).
  - `ClientRealtimeEvent` sealed interface — typed surface for everything
    the client sends (session update, audio buffer append/commit/clear,
    user text, function-call output, response create/cancel).
  - `RealtimeGateway` + `RealtimeGatewaySession` interfaces — vendor-neutral
    transport contract.
  - `RealtimeGatewayException` — new sibling of `LlmGatewayException` in
    the `MojenticException` sealed hierarchy.
- **New `mojentic-realtime-openai` module**:
  - `OpenAiRealtimeGateway` over Ktor Client WebSockets (OkHttp engine on
    JVM/Android, Darwin engine on iOS). Sends initial `session.update` on
    open; raw server events flow through a Channel-backed `Flow<JsonObject>`
    so no events are dropped between connect and first subscriber.
  - `OpenAiEventCodec` (internal) — only place in the stack that speaks the
    OpenAI wire vocabulary; translates `ClientRealtimeEvent` ↔ OpenAI JSON.
- **Tests**: 11 `OpenAiEventCodecTest` cases covering user-text encoding,
  function-call-output items, response control events, session config with
  tools and manual VAD, named-tool-choice encoding, audio frame roundtrip
  through base64, payload length validation, and provider-extras merging.
- Quality gate: ktlint + Detekt clean across JVM + Android-host +
  iOS-simulator.

## [0.4.0] - Phase 4 ✅ Shipped (2026-05-18)

### Added — slice C: ReActAgent + remaining examples (2026-05-18)

- **`ReActAgent`** in `mojentic-core/commonMain/agents` — single-class
  reasoning-and-acting loop with a custom system prompt. Each iteration is
  one round-trip through the broker (which already does recursive tool
  dispatch), looking for a `FINAL ANSWER:` marker to stop. `steps()` exposes
  the per-iteration `ReActStep` trace (`iteration`, `response`, `toolCalls`,
  `final`) for observability. The Python reference's multi-agent ReAct
  example becomes a single Kotlin class because the broker's recursive tool
  execution is the idiomatic equivalent of the Python dispatcher fan-out.
- **Seven new JVM-only example subprojects** rounding out the Phase 4
  example surface:
  - `examples/react` — drives `ReActAgent` against the date toolkit.
  - `examples/async-llm` — `AsyncDispatcher` fan-out: two `BaseAsyncLlmAgent`s
    answer in parallel and an `AsyncAggregatorAgent` joins them via a shared
    `correlationId`.
  - `examples/recursive-agent` — concurrent `SimpleRecursiveAgent.solve`
    calls under `coroutineScope`, plus a printout of the per-iteration
    `SolverEvent` history.
  - `examples/solver-chat-session` — wraps an `IterativeProblemSolver` as an
    `LlmTool` and hands it to a top-level `ChatSession`.
  - `examples/working-memory` — `BaseAsyncLlmAgentWithMemory` backed by a
    seeded `SharedWorkingMemory`, demonstrating `mergeMemory` between turns.
  - `examples/coding-file-tool` — coordinator agent delegating to two
    `ToolWrapper`-bridged specialists: a temporal agent (date tools) and a
    sandboxed knowledge agent (eight file tools).
  - `examples/broker-as-tool` — composer agent delegating to summariser and
    translator sub-agents via `ToolWrapper`.
- Quality gate green: ktlint + Detekt clean; build + allTests pass on JVM,
  Android-host, and iOS-simulator.

### Added — slice A: agent foundations + ToolWrapper (2026-05-18)

- **`Event` / `TerminateEvent`** in `mojentic-core/commonMain/agents` —
  open base classes carrying an optional `source: KClass<*>` hint and a
  mutable `correlationId` so the dispatcher can fan a fresh id through a
  chain.
- **`Agent` interface** with a single `suspend fun receiveEvent(event):
  List<Event>` surface. The Kotlin port collapses the Python reference's
  sync/async pair (`BaseAgent` / `BaseAsyncAgent`) — bodies that don't
  need to suspend simply don't.
- **`Router`** maps `KClass<out Event>` → ordered list of agents.
- **`AsyncDispatcher`** — coroutine-driven event loop. `dispatch(event)`
  queues, `start(scope)` launches the loop on a caller-owned scope,
  `stop()` joins the loop, `waitForEmptyQueue(timeoutMs)` lets tests
  block until in-flight work drains, and any `TerminateEvent` produced
  by an agent stops the loop. Routes through `Tracer.recordAgentInteraction`.
- **`BaseAsyncLlmAgent`** — LLM-backed agent reusing `LlmBroker` +
  `LlmTool` from earlier phases. Exposes a mutable tool list (`addTool`)
  and a `generateResponse(content): LlmGatewayResponse` shortcut.
- **`ToolWrapper`** — wraps a `BaseAsyncLlmAgent` as an `LlmTool`.
  Mirrors the Python reference's agent-as-tool pattern; the inner agent
  receives the calling LLM's `input` argument as a user message and
  returns its text response.

### Added — slice B: shared memory + aggregator + iterative solvers (2026-05-18)

- **`SharedWorkingMemory`** in `mojentic-core/commonMain/context` —
  `Mutex`-protected `Map<String, JsonElement>` with snapshot-style reads
  and merge / replace mutators. Multiplatform-safe.
- **`BaseAsyncLlmAgentWithMemory`** — extends `BaseAsyncLlmAgent` to
  inject the current memory snapshot into the prompt before each turn.
- **`AsyncAggregatorAgent`** — buffers events by `correlationId` until
  every required `KClass<out Event>` has been observed, then delivers
  the collected list to `processEvents`. `waitForEvents(correlationId,
  timeoutMs)` suspends external callers using `CompletableDeferred`.
- **`IterativeProblemSolver`** — chat-session loop calling the LLM up to
  `maxIterations` times, stopping on `DONE` / `FAIL`, then asks for a
  final summary turn.
- **`SimpleRecursiveAgent`** — same loop with `withTimeoutOrNull` for an
  overall wall-clock deadline; emits per-iteration `SolverEvent`
  snapshots (`GoalSubmitted`, `IterationCompleted`, `GoalAchieved`,
  `GoalFailed`, `TimedOut`) accessible via `history()`.
- **Two examples**:
  - `examples/agent-dispatcher` — wires `Router` + `AsyncDispatcher` +
    two `BaseAsyncLlmAgent`s with `ToolWrapper` bridging them.
  - `examples/iterative-solver` — drives `IterativeProblemSolver` with
    the Phase 1 date tools to answer a multi-step planning question.
- Quality gate green: ktlint + Detekt clean; tests pass on JVM,
  Android-host, and iOS-simulator.

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
