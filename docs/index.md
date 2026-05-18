# Mojentic for Kotlin

Mojentic is a multi-layered framework that aims to provide a simple and flexible way to perform low-level operations with LLMs, apply simple agents to intermediate tasks, or assemble teams of agents to solve complex problems.

The Kotlin port is a Kotlin Multiplatform library targeting JVM, Android, and iOS. It is API-aligned with the Python reference implementation — see [`PARITY.md`](https://github.com/svetzal/mojentic-unify/blob/main/PARITY.md) for the live status across all supported languages.

## Design goals

- Simple to use to do simple things.
- One vendor-neutral surface across multiple LLM providers (Ollama, OpenAI, Anthropic).
- An async pubsub agent architecture rather than the popular directed-graph or delegation styles.
- Idiomatic Kotlin throughout: `suspend` functions, `Flow`, sealed-class unions, data classes with named arguments — no shoehorned-from-Python ergonomics.

## At a glance — simple completion

```kotlin
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.ollama.OllamaGateway

suspend fun main() {
    val broker = LlmBroker(model = "qwen3:32b", gateway = OllamaGateway())
    val response = broker.generate(
        messages = listOf(LlmMessage.user("Hello, how are you?")),
    )
    println(response.content)
}
```

## Reading guide

Three sections, mirroring the other Mojentic ports:

- **[Use Cases](use-cases/)** — task-oriented guides covering the four core capabilities: [Building Chatbots](use-cases/building-chatbots.md), [Structured Output](use-cases/structured-output.md), [Building Agents](use-cases/building-agents.md), [Image Analysis](use-cases/image-analysis.md). Start here if you're new.
- **Examples** — one runnable subproject under [`examples/`](https://github.com/svetzal/mojentic-kt/tree/main/examples) per feature. Each is independently executable via `./gradlew :examples:<name>:run`.
- **Core Concepts** — API reference auto-generated from KDoc on public symbols. Available alongside this site (see the module navigation in the Dokka header).

## Module map

| Module | Purpose |
|---|---|
| `mojentic-core` | Vendor-neutral types (`LlmMessage`, `LlmBroker`, `LlmGateway`, tool interfaces, tracer, task list, file tools, realtime broker, gateway interfaces). |
| `mojentic-ollama` | Ollama gateway. |
| `mojentic-openai` | OpenAI gateway (Chat Completions API). |
| `mojentic-anthropic` | Anthropic gateway (Messages API). |
| `mojentic-realtime-openai` | OpenAI Realtime gateway over WebSockets. |
| `mojentic-websearch-serpapi` | SerpApi-backed web search tool. |

Depend only on the gateway modules you need — `mojentic-core` is a transitive dependency of each.

## Quality gates

`./gradlew ktlintCheck detekt build allTests apiCheck` is the canonical "did I break anything" command. CI runs the same on every commit across JVM, Android-host, and iOS-simulator targets.

## Status

Phase 7 (documentation polish & 1.x stabilization). All four core Use Cases shipped — Ollama, OpenAI, Anthropic, Realtime Voice, plus the agent toolkit (parallel tool execution, iterative solvers, ReAct, async dispatcher, working memory). See [`KOTLIN.md`](https://github.com/svetzal/mojentic-unify/blob/main/KOTLIN.md) for the per-phase change log.
