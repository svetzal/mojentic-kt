# Mojentic Kotlin — Project Charter

## Purpose

Mojentic-kt is the Kotlin Multiplatform (KMP) implementation of the Mojentic
LLM integration framework. It provides a Kotlin-idiomatic, coroutine-first
abstraction over multiple LLM providers (Ollama, OpenAI, Anthropic) with tool
calling, structured output, streaming, an event-driven agent system, and
realtime voice support. It exists to give Android app teams and JVM server-side
/ desktop teams a production-ready LLM library that serves the Kotlin ecosystem.

## Goals

- Provide a unified `suspend`-based API for interacting with multiple LLM
  providers through a single `LlmBroker` interface, mirroring the Python
  reference design.
- Be **distinctly Kotlin-idiomatic**: coroutines + `Flow` throughout, sealed
  hierarchies for closed sums, data classes for value types, builder DSLs via
  lambda-with-receiver where they pay for themselves — not a transliteration
  of any other port.
- Be **multiplatform-first**: one `commonMain` API surface, platform-specific
  code only where unavoidable (HTTP client engine, WebSocket transport,
  file I/O, secure random).
- Support an event-driven multi-agent architecture with shared working memory
  and ReAct-pattern reasoning.
- Maintain full feature parity with the Python, Elixir, Rust, TypeScript, and
  Swift implementations of Mojentic (see `PARITY.md` in the monorepo).
- Ship as a first-class Kotlin Multiplatform library:
  - **JVM / Android** → Maven Central (`com.vetzal.mojentic:mojentic-core:<v>`,
    plus per-gateway modules).
- Include comprehensive Dokka documentation, handwritten use-case guides, and
  runnable examples so the library is learnable without external guidance.

## Non-Goals

- Being a standalone AI application or end-user product — this is a library.
- Supporting platforms outside JVM and Android. Native Apple apps use
  `mojentic-sw`.
- Compose / Android UI helpers. Mojentic is a library; UI concerns stay in
  consumer apps.
- Bundling provider SDKs. We talk to provider HTTP APIs directly through
  Ktor; no `openai-java` or `aws-bedrock-sdk` pulled in transitively.
- A blocking / synchronous API surface — the library is `suspend` / coroutine
  first; no `runBlocking` in library code.

## Target Users

Kotlin developers building:
- Android apps wanting an idiomatic Kotlin LLM library.
- Kotlin server-side services (Ktor, Spring Boot, Micronaut).
- JVM desktop apps (Compose Desktop, JavaFX).

Especially developers who already use Mojentic on another stack and want
consistent semantics.
