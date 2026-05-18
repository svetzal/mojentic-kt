# Building Agents

## Why an agent layer?

A chatbot replies to one prompt at a time. An *agent* picks a sub-goal, takes an action, observes the result, and decides what to do next — and keeps going until the original goal is satisfied or it gives up. The agent layer is what bridges "single LLM call" and "system that gets things done."

The Kotlin port ships three agent patterns out of the box, each suited to a different shape of problem:

| Pattern | Class | Shape of problem |
|---|---|---|
| **Iterative solver** | `IterativeProblemSolver` | Goal is a final answer; the model proposes steps and re-evaluates. Linear progress, no branching. |
| **Recursive solver** | `SimpleRecursiveAgent` | Goal can be split into sub-goals; each sub-goal is solved by another instance of the same agent. |
| **ReAct** | `ReActAgent` | Goal requires interleaved reasoning and tool use ("think", "act", "observe", repeat). |

All three sit on top of the `Agent` / `Event` / `Router` / `AsyncDispatcher` core, so they compose with the async pubsub bus when you need multi-agent coordination.

## When to apply each pattern

- **Single-shot transformation?** Don't reach for an agent — use `LlmBroker.generate` or `generateObject` directly.
- **The model needs to use tools and chat?** A `ChatSession` with tools is enough — see [Building Chatbots](building-chatbots.md).
- **The goal needs decomposition + reflection?** `IterativeProblemSolver` is the simplest agent that does this.
- **The goal is recursive (research a topic → research each subtopic)?** `SimpleRecursiveAgent`.
- **The goal is "think before each action"?** `ReActAgent`. Slowest, most general.

Start with the simplest pattern that fits and only escalate when you've actually proven you need more.

## Getting started — iterative solver

```kotlin
import com.mojentic.agent.IterativeProblemSolver
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.CurrentDateTimeTool
import com.mojentic.openai.OpenAiGateway

suspend fun main() {
    val broker = LlmBroker(
        model = "gpt-4o-mini",
        gateway = OpenAiGateway(apiKey = System.getenv("OPENAI_API_KEY")),
    )

    val solver = IterativeProblemSolver(
        broker = broker,
        tools = listOf(CurrentDateTimeTool()),
        maxIterations = 8,
    )

    val result = solver.solve(
        goal = "What day of the week was 31 January 2026? Reply with just the day name.",
    )

    println(result.finalAnswer)
}
```

The solver loops: propose a step → execute (which may include tool calls) → evaluate progress → repeat. It terminates when the model declares the goal achieved or when `maxIterations` is hit.

## Tool authoring

Every agent pattern accepts a `List<LlmTool>`. Implement the interface directly — no annotations, no reflection:

```kotlin
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.LlmToolParameter
import kotlinx.serialization.json.JsonObject

class WeatherTool : LlmTool {
    override val name = "current_weather"
    override val description = "Returns the current temperature in Celsius for a city."
    override val parameters = listOf(
        LlmToolParameter(name = "city", type = "string", required = true),
    )

    override suspend fun call(arguments: JsonObject): String {
        val city = arguments["city"]!!.toString().trim('"')
        return WeatherApi.fetch(city).let { "$it°C" }
    }
}
```

Pass it to any agent or chat session: `IterativeProblemSolver(broker, tools = listOf(WeatherTool()))`.

When the model wants to call multiple tools in a single turn, the agent dispatches them concurrently via `ParallelToolRunner` — `WeatherTool` for Berlin and `WeatherTool` for Tokyo run in parallel, not sequentially.

## Shared working memory across agents

For multi-agent setups, `SharedWorkingMemory` gives all participating agents a common scratch space:

```kotlin
import com.mojentic.agent.SharedWorkingMemory
import com.mojentic.agent.BaseAsyncLlmAgentWithMemory

val memory = SharedWorkingMemory()
val researcher = BaseAsyncLlmAgentWithMemory(broker = broker, memory = memory, role = "researcher")
val writer = BaseAsyncLlmAgentWithMemory(broker = broker, memory = memory, role = "writer")
```

Each agent's notes are visible to the others — useful for "first agent researches, second agent writes" pipelines without bolting on an external store.

## Async dispatcher (pubsub)

```kotlin
import com.mojentic.agent.AsyncDispatcher
import com.mojentic.agent.Event

val dispatcher = AsyncDispatcher()
dispatcher.register<UserQuery>(researcher)
dispatcher.register<ResearchComplete>(writer)
dispatcher.publish(UserQuery("explain quantum tunneling"))
```

`AsyncDispatcher` is the spine of multi-agent setups: agents subscribe to event types and emit new events when they finish. There is no orchestrator object that "knows everyone" — the routing falls out of the event-type subscriptions.

## Tracing

Every agent run can be inspected via the `Tracer`:

```kotlin
import com.mojentic.tracer.Tracer

val tracer = Tracer()
val solver = IterativeProblemSolver(broker = broker, tracer = tracer)
solver.solve(goal = "...")
tracer.events.forEach { println(it) }
```

You'll see every LLM call, every tool invocation, and every agent transition with timestamps. The realtime examples (`tracer-demo`) include a live console renderer.

## Related examples

- [`examples/iterative-solver`](https://github.com/svetzal/mojentic-kt/tree/main/examples/iterative-solver)
- [`examples/recursive-agent`](https://github.com/svetzal/mojentic-kt/tree/main/examples/recursive-agent)
- [`examples/react`](https://github.com/svetzal/mojentic-kt/tree/main/examples/react)
- [`examples/agent-dispatcher`](https://github.com/svetzal/mojentic-kt/tree/main/examples/agent-dispatcher)
- [`examples/working-memory`](https://github.com/svetzal/mojentic-kt/tree/main/examples/working-memory)
- [`examples/solver-chat-session`](https://github.com/svetzal/mojentic-kt/tree/main/examples/solver-chat-session) — embed an iterative solver inside a chat UI.
- [`examples/tracer-demo`](https://github.com/svetzal/mojentic-kt/tree/main/examples/tracer-demo)
