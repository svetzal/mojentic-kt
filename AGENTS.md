# Mojentic Kotlin — Agent Guidance

This file provides Kotlin-specific guidance for AI agents working in this
sub-project. The monorepo root `AGENTS.md` covers shared cross-port principles;
this file covers Kotlin-specific quality gates, tooling, and patterns.

## Project Overview

`mojentic-kt` is the Kotlin Multiplatform (KMP) port of Mojentic. The Python
implementation (`mojentic-py`) is the source of truth for API design and
feature behaviour. See `KOTLIN.md` in the `mojentic-unify` monorepo for the
full plan, roadmap, and Kotlin-idiomatic translation choices; see `PARITY.md`
for the cross-port feature matrix.

## Toolchain

- **Kotlin 2.0+** — required for K2 compiler stability, standard-library
  `kotlin.uuid.Uuid`, and the current KMP target shape.
- **JDK 17** — Kotlin compiler toolchain (`jvmToolchain(17)`).
- **Gradle 8.10+** — managed via the wrapper.
- **Android Gradle Plugin** — applied to the `mojentic-core` module so the
  `androidTarget()` declaration produces a consumable AAR. We do **not** use
  the `org.jetbrains.kotlin.android` plugin; JVM-only consumers must remain
  unaffected.
- **Targets**: `jvm()`, `androidTarget()` (minSdk 24), `iosX64()`,
  `iosArm64()`, `iosSimulatorArm64()`. macOS / Linux / JS / wasmJs are
  post-MVP and opportunistic.

## Mandatory Quality Gate

All gates must pass before any commit, matching the other ports. Run before
each commit:

```bash
./gradlew ktlintCheck detekt build allTests
```

Later phases add Kover coverage, OWASP dependency-check, binary-compatibility
validation, and Dokka. The Phase-0 skeleton wires only the gates that are
meaningful for an empty library.

| Concern              | Tool                                  | Command                                |
|----------------------|---------------------------------------|----------------------------------------|
| Lint (style)         | ktlint (`jlleitschuh.gradle.ktlint`)  | `./gradlew ktlintCheck`                |
| Lint (smells)        | Detekt (with `detekt.yml`)            | `./gradlew detekt`                     |
| Build                | Gradle / KMP                          | `./gradlew build`                      |
| Tests                | `kotlin.test` + `kotlinx-coroutines-test` + Turbine + Ktor MockEngine | `./gradlew allTests`  |
| Coverage *(Phase 1+)*| Kover                                 | `./gradlew koverHtmlReport koverVerify`|
| Security *(Phase 1+)*| OWASP Dependency-Check                | `./gradlew dependencyCheckAggregate`   |
| API surface *(Phase 7)* | Binary-compatibility-validator     | `./gradlew apiCheck`                   |
| Docs *(Phase 7)*     | Dokka                                 | `./gradlew dokkaHtmlMultiModule`       |

CI (GitHub Actions) runs on:
- `ubuntu-latest` for JVM + Android targets (Android SDK installed).
- `macos-latest` for iOS targets (Xcode + iOS simulators).

## Engineering Principles

Inherit from the monorepo `AGENTS.md`. Kotlin-specific applications:

### Functional core, imperative shell

- Pure value types — `@Serializable data class` (kotlinx.serialization) —
  for domain models. Immutable by default.
- Side effects (HTTP, file I/O, WebSockets) live behind `interface` gateways.
  Gateway implementations are thin Ktor-Client / `okio` wrappers — **no
  business logic in gateways**.
- Stateful coordinators (broker, tracer event store, working memory,
  dispatcher, router, realtime session) are plain classes that protect mutable
  state with `Mutex.withLock { … }` or `MutableStateFlow` / `MutableSharedFlow`
  where reactive semantics fit. We do **not** use Java `synchronized` (won't
  work on Native) or deprecated coroutine actor builders.

### Compose over inherit

- `interface` + default methods + sealed hierarchies for closed sums; no
  inheritance hierarchies in domain models.
- The `NullTracer` pattern uses a top-level `object NullTracer : Tracer`
  with no-op defaults on the interface.

### Errors

- All public APIs that can fail are documented (and where useful, explicit)
  about what they throw — single `MojenticException` hierarchy at boundaries.
- **No `!!` force-unwraps in library code.** Use `requireNotNull` /
  `checkNotNull` / explicit `throws` of `MojenticException`.
- `error(...)` / `IllegalStateException` only for genuinely-unreachable
  invariant violations, never for recoverable conditions.

### Concurrency

- Every public async API is a `suspend fun` or returns a `Flow<T>`. Nothing
  returns `CompletableFuture`, `Deferred`, or `Job` from the public surface.
- The library does **not** dictate a `CoroutineScope`. Callers supply one, or
  use `suspend` calls inline. Internally we use `coroutineScope { … }` /
  `supervisorScope { … }` for fan-out.
- **No `runBlocking`** anywhere in library code (including examples that
  aren't `main`).
- Cancellation is cooperative `Job` cancellation. Tools that perform I/O
  honour `ensureActive()` and clean up in `try { … } finally { … }` blocks.
- Parallel tool execution uses
  `coroutineScope { tools.map { async { it.execute(...) } }.awaitAll() }`.
  Serial-default-for-chat-broker semantics are preserved;
  `ParallelToolRunner` is opt-in.
- Concurrency-safe state via `Mutex` (multiplatform-safe) or `StateFlow` /
  `SharedFlow` where reactive semantics fit.
- All public types in `commonMain` that cross coroutine / thread boundaries
  are immutable `data class`es or are `@Suppress`-justified holders of
  `Mutex`-protected state.

### Testing

- **`kotlin.test`** (multiplatform) + **`kotlinx-coroutines-test`** for
  `runTest { … }` + **Turbine** for `Flow` assertions + **Ktor MockEngine**
  for HTTP gateways + **MockK** (JVM tests only).
- Test behaviour, not implementation. Only mock gateway / boundary types;
  never mock library internals.
- Do not test gateway classes unless they have custom logic — they're already
  thin wrappers.

### Naming conventions

Follow the official Kotlin style guide. Acronyms longer than two letters are
treated as a single word (per JetBrains' guidance):
- `LlmBroker`, `LlmGateway`, `LlmMessage`, `LlmTool` — "Llm" not "LLM"
  (parallels `Http`, `Url`, `Json`).
- `CompletionConfig`, `ReasoningEffort` (enum: `LOW`, `MEDIUM`, `HIGH`).
- `ChatSession`, `Tracer`, `Router`, `Dispatcher`, `SharedWorkingMemory`.

## Documentation

- **Dokka v2** is the documentation tool (`mojentic-kt/docs/`).
- Update KDoc comments in the same commit as the code change.
- Use-Cases section is handwritten Markdown under `docs/use-cases/`
  (Building Chatbots, Structured Output, Building Agents, Image Analysis).
- Provided tools are documented as **examples** ("reference implementation,
  not core library feature").
- Dokka is published to GitHub Pages on every `v*` tag.

## Version Synchronisation

Major and minor versions track the other ports (per
`mojentic-ru/AGENTS.md` Version Synchronization). Patch versions move
independently. Update `CHANGELOG.md` in the same commit as code changes.

## Trunk-Based Development

Per the user's global instructions: integrate directly to `main`. No
long-lived feature branches, no PRs as gates. Commit scoped, working changes;
push to `origin/main` after each commit. See `~/.claude/CLAUDE.md` for full
policy.
