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

- **Kotlin 2.4.20+** — required for K2 compiler stability, standard-library
  `kotlin.uuid.Uuid`, the current KMP target shape, and the fix for
  CVE-2026-53914 (unsafe deserialization in build cache metadata).
- **JDK 17** — Kotlin compiler toolchain (`jvmToolchain(17)`). JDK 17 stays
  the bytecode target so library consumers on JDK 17 LTS keep working.
- **Gradle 9.8+** — managed via the wrapper. The wrapper pins
  `distributionSha256Sum`; when you upgrade, pass
  `--gradle-distribution-sha256-sum` with the value from
  `services.gradle.org`, and check `gradle-wrapper.jar` against the published
  wrapper checksum.
- **Android Gradle Plugin 9.4+** — applied via the AGP-9-only
  `com.android.kotlin.multiplatform.library` plugin (the legacy
  `com.android.library` plugin is incompatible with the Kotlin Multiplatform
  plugin from AGP 9.0 onwards). Android config lives inside the `kotlin {
  android { ... } }` block — there is no separate `android { ... }`
  extension block at module scope.
- **Targets**: `jvm()`, `android { ... }` inside the kotlin extension
  (minSdk 24, compileSdk 37), `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`.
  macOS / Linux / JS / wasmJs are post-MVP and opportunistic.

## Mandatory Quality Gate

All gates must pass before any commit, matching the other ports. Run before
each commit:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"   # macOS; CI sets its own
./gradlew -Dorg.gradle.jvmargs=-Xmx8g \
  ktlintCheck detekt build allTests apiCheck dokkaGenerate
```

The iOS framework link needs the larger heap. On Apple Silicon, Gradle skips
`iosX64Test` and prints a warning; that is expected.

| Concern              | Tool                                  | Command                                |
|----------------------|---------------------------------------|----------------------------------------|
| Lint (style)         | ktlint 1.8 (`jlleitschuh.gradle.ktlint`, engine pinned in the catalog) | `./gradlew ktlintCheck` |
| Lint (smells)        | Detekt 1.23 (with `detekt.yml`)       | `./gradlew detekt`                     |
| Build                | Gradle / KMP                          | `./gradlew build`                      |
| Tests                | `kotlin.test` + `kotlinx-coroutines-test` + Turbine + Ktor MockEngine | `./gradlew allTests`  |
| API surface          | Binary-compatibility-validator        | `./gradlew apiCheck`                   |
| Docs                 | Dokka 2                               | `./gradlew dokkaGenerate`              |
| Security             | OWASP Dependency-Check                | see "Dependency audit" below           |
| Coverage *(not wired)* | Kover (plugin declared, not applied) | —                                     |

## Dependency audit

Run the audit after any dependency or plugin change, and before a release:

```bash
./gradlew -Dorg.gradle.jvmargs=-Xmx8g dependencyCheckAggregate --no-parallel
```

It must pass with zero findings at CVSS 7.0 or higher. `--no-parallel` is
required: Gradle 9 refuses the aggregate task's cross-project resolution under
parallel execution. The report is in `build/reports/dependency-check/`. A run
takes 7 to 15 minutes.

Without an NVD API key (`NVD_API_KEY`), Dependency-Check can read NVD's bulk
feed instead of the API. Check the feed's `modified.meta` timestamp first,
because feeds can lag the API. This changes the data transport only, not the
audit scope or the severity threshold. The API stays the default. See the
[Dependency-Check feed documentation](https://dependency-check.github.io/DependencyCheck/data/mirrornvd.html).

```bash
./gradlew -Dorg.gradle.jvmargs=-Xmx8g dependencyCheckAggregate --no-parallel \
  '-PdependencyCheckNvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz'
```

The audit scans build-tool classpaths (linters, Dokka, Android lint, Kotlin
tooling) as well as published configurations. Do not narrow it to published
configurations to make it pass.

When the audit fails, fix in this order:

1. **Upgrade** the dependency, or the tool that brings it.
2. **Floor** it. `buildToolSecurityFloors` in `build.gradle.kts` lifts a
   transitive dependency on named build-tool configurations. Add a floor only
   when the tool still works on the newer version, and prove it with the full
   gate. For a published configuration, use a dependency constraint instead.
3. **Replace** a tool whose bundled dependencies cannot be fixed.
4. **Suppress** in `dependency-check-suppressions.xml`, only when one of these
   is true:
   - (i) the finding is a verified false positive: you checked the advisory,
     the fix commit or the jar contents, and wrote down the evidence; or
   - (ii) there is no upstream fix, and the dependency is build-time only and
     cannot reach a published artifact.

Every suppression needs both:

- `<notes>` that name the case, (i) or (ii), and the evidence.
- An `until="YYYY-MM-DDZ"` expiry **no more than 90 days out**. When it
  expires, the audit fails again. Re-examine the finding: upgrade if a fix
  now exists, or re-verify and set a new date. Do not re-date without
  checking.

Match exact artifact versions in `packageUrl` (or the shaded copy's path in
`filePath`), so a tool upgrade brings its jars back for review.

Also check what consumers receive: run `./gradlew publishToMavenLocal` and
read the generated POMs and Gradle module files in
`~/.m2/repository/com/mojentic/`. Nothing in them may be vulnerable.

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
