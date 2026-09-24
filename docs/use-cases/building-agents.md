# Building Agents

## Why an agent layer?

A chatbot replies to one prompt at a time. An *agent* picks a sub-goal, takes an action, observes the result, and decides what to do next — and keeps going until the original goal is satisfied or it gives up. The agent layer is what bridges "single LLM call" and "system that gets things done."

The Kotlin port ships three agent patterns out of the box, each suited to a different shape of problem:

| Pattern | Class | Shape of problem |
|---|---|---|
| **Iterative solver** | `IterativeProblemSolver` | Goal is a final answer; the model proposes steps and re-evaluates. Linear progress, no branching. |
| **Recursive solver** | `SimpleRecursiveAgent` | Goal can be split into sub-goals; each sub-goal is solved by another instance of the same agent. |
| **ReAct** | `ReActAgent` | Goal requires interleaved reasoning and tool use ("think", "act", "observe", repeat). |

All three run on a `ChatSession` over an `LlmBroker`. The separate `Agent` / `Event` / `Router` / `AsyncDispatcher` core handles multi-agent coordination over an async event bus.

## When to apply each pattern

- **Single-shot transformation?** Don't reach for an agent — use `LlmBroker.complete` or `completeJson` directly.
- **The model needs to use tools and chat?** A `ChatSession` with tools is enough — see [Building Chatbots](building-chatbots.md).
- **The goal needs decomposition + reflection?** `IterativeProblemSolver` is the simplest agent that does this.
- **The goal is recursive (research a topic → research each subtopic)?** `SimpleRecursiveAgent`.
- **The goal is "think before each action"?** `ReActAgent`. Slowest, most general.

Start with the simplest pattern that fits and only escalate when you've actually proven you need more.

## Getting started — iterative solver

```kotlin
import com.mojentic.agents.IterativeProblemSolver
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.tools.CurrentDateTimeTool
import com.mojentic.openai.OpenAIGateway

suspend fun main() {
    val broker = LlmBroker(OpenAIGateway(apiKey = System.getenv("OPENAI_API_KEY")))

    val solver = IterativeProblemSolver(
        broker = broker,
        model = "gpt-4o-mini",
        availableTools = listOf(CurrentDateTimeTool()),
        maxIterations = 8,
    )

    val answer: String = solver.solve(
        "What day of the week was 31 January 2026? Reply with just the day name.",
    )

    println(answer)
}
```

The solver loops: each turn asks the model to make progress with the available tools and reply `DONE` or `FAIL`. It stops on either signal or when `maxIterations` is hit, then asks for a final summary, which `solve` returns.

## Tool authoring

Every agent pattern accepts a `List<LlmTool>`. Implement the interface directly — no annotations, no reflection:

```kotlin
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class WeatherTool(private val weather: WeatherGateway) : LlmTool {
    override val descriptor = ToolDescriptor(
        name = "current_weather",
        description = "Returns the current temperature in Celsius for a city.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("city") { put("type", "string") }
            }
            putJsonArray("required") { add("city") }
        },
    )

    override suspend fun execute(arguments: JsonObject): String {
        val city = arguments["city"]?.jsonPrimitive?.content
            ?: return """{"error":"city is required"}"""
        return """{"celsius":${weather.currentCelsius(city)}}"""
    }
}
```

`LlmTool` derives `name` and `description` from the descriptor. `WeatherGateway` stands for your own interface to the weather service. Pass the tool to any agent or chat session: `IterativeProblemSolver(broker, model, availableTools = listOf(WeatherTool(weather)))`.

Tool calls run one at a time by default. Build the broker with a `ParallelToolRunner` to run the calls of one turn concurrently: `LlmBroker(gateway, toolRunner = ParallelToolRunner())`. Then `WeatherTool` for Berlin and `WeatherTool` for Tokyo run in parallel, not sequentially.

## Shared working memory across agents

For multi-agent setups, `SharedWorkingMemory` gives all participating agents a common scratch space:

```kotlin
import com.mojentic.agents.BaseAsyncLlmAgentWithMemory
import com.mojentic.context.SharedWorkingMemory

val memory = SharedWorkingMemory()
val researcher = BaseAsyncLlmAgentWithMemory(
    broker = broker,
    model = "gpt-4o-mini",
    memory = memory,
    behaviour = "You are a researcher.",
    instructions = "Record the facts you find.",
)
val writer = BaseAsyncLlmAgentWithMemory(
    broker = broker,
    model = "gpt-4o-mini",
    memory = memory,
    behaviour = "You are a writer.",
    instructions = "Write from the recorded facts.",
)
```

Each agent sees the current memory contents before every turn. An agent adds to it explicitly with `mergeMemory(...)`. This is useful for "first agent researches, second agent writes" pipelines without bolting on an external store.

## Async dispatcher (pubsub)

```kotlin
import com.mojentic.agents.AsyncDispatcher
import com.mojentic.agents.Event
import com.mojentic.agents.Router
import kotlinx.coroutines.coroutineScope

class UserQuery(val text: String) : Event()
class ResearchComplete(val notes: String) : Event()

val router = Router()
router.addRoute(UserQuery::class, researchAgent)
router.addRoute(ResearchComplete::class, writingAgent)

coroutineScope {
    val dispatcher = AsyncDispatcher(router)
    dispatcher.start(this)
    dispatcher.dispatch(UserQuery("explain quantum tunneling"))
    dispatcher.waitForEmptyQueue(timeoutMs = 60_000)
    dispatcher.stop()
}
```

`researchAgent` and `writingAgent` implement `Agent`: `receiveEvent(event)` returns the events to dispatch next. A `TerminateEvent` stops the dispatcher. `AsyncDispatcher` is the spine of multi-agent setups: there is no orchestrator object that "knows everyone" — the routing falls out of the `Router`'s event-type routes.

## Tracing

Every agent run can be inspected via the `Tracer`:

```kotlin
import com.mojentic.tracer.TracerSystem

val tracer = TracerSystem()
val broker = LlmBroker(gateway, tracer = tracer)
val solver = IterativeProblemSolver(broker = broker, model = "gpt-4o-mini")
solver.solve("...")
tracer.eventStore.getEvents().forEach { println(it.printableSummary()) }
```

You'll see every LLM call, LLM response, and tool invocation with timestamps. `AsyncDispatcher` also takes a tracer and records agent interactions. The `tracer-demo` example prints every recorded event after a run.

## Related examples

- [`examples/iterative-solver`](https://github.com/svetzal/mojentic-kt/tree/main/examples/iterative-solver)
- [`examples/recursive-agent`](https://github.com/svetzal/mojentic-kt/tree/main/examples/recursive-agent)
- [`examples/react`](https://github.com/svetzal/mojentic-kt/tree/main/examples/react)
- [`examples/agent-dispatcher`](https://github.com/svetzal/mojentic-kt/tree/main/examples/agent-dispatcher)
- [`examples/working-memory`](https://github.com/svetzal/mojentic-kt/tree/main/examples/working-memory)
- [`examples/solver-chat-session`](https://github.com/svetzal/mojentic-kt/tree/main/examples/solver-chat-session) — embed an iterative solver inside a chat UI.
- [`examples/tracer-demo`](https://github.com/svetzal/mojentic-kt/tree/main/examples/tracer-demo)
