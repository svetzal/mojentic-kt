# Structured Output

## Why structured output?

LLMs are great at producing free-form text and terrible at producing parseable data on the first try. "Reply only with JSON" is reliable enough for demos and fragile enough that you'll regret it in production — the model will at some point wrap the JSON in `` ```json `` fences, add a trailing apology, or omit a required field.

Structured output sidesteps this: the model is constrained at the API level to emit a value that matches a schema you provided. The Kotlin port exposes a single `LlmBroker.generateObject<T>()` method that:

1. Generates a JSON Schema from your `@Serializable` data class.
2. Tells the gateway to enforce that schema (OpenAI's `response_format = json_schema`; Anthropic's forced-tool trick under the hood).
3. Decodes the model's reply directly into a typed Kotlin value.

The caller never sees raw JSON.

## When to apply this approach

Reach for `generateObject` whenever you would otherwise be parsing model output with regex or `JSONObject.optString`. Particularly:

- Extracting specific fields from a longer body of text (entity extraction, classification, summarisation with structured metadata).
- Producing tool inputs for a deterministic downstream system.
- Asking the model to make multi-valued choices ("rank these candidates", "label this transcript", "score on these axes").

If your shape is "free text plus an integer at the end", structured output is the right tool — don't try to coax that out with prompts alone.

## Getting started

```kotlin
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.openai.OpenAiGateway
import kotlinx.serialization.Serializable

@Serializable
data class Sentiment(
    val label: String,
    val confidence: Double,
)

suspend fun main() {
    val gateway = OpenAiGateway(apiKey = System.getenv("OPENAI_API_KEY"))
    val broker = LlmBroker(model = "gpt-4o-mini", gateway = gateway)

    val result: Sentiment = broker.generateObject(
        messages = listOf(LlmMessage.user("I love this so much! It's amazing.")),
    )

    println("label=${result.label} conf=${result.confidence}")
}
```

`generateObject<T>()` infers the target type from the call site via Kotlin's reified type parameters. There's no explicit "model" or "schema" argument — the `@Serializable` annotation on the data class is the schema.

## Step-by-step

### 1. Define the data class

```kotlin
@Serializable
data class Sentiment(
    val label: String,
    val confidence: Double,
)
```

`@Serializable` is from `kotlinx.serialization`. Field names become JSON keys; nullable types become optional schema fields; nested `@Serializable` types nest in the schema. Use `@SerialName` to map a Kotlin field name to a different JSON key.

For richer constraints (enum values, min/max), declare an `enum class` instead of `String`, or constrain via the prompt — the schema-generator emits structural constraints only.

### 2. Call generateObject

```kotlin
val result: Sentiment = broker.generateObject(
    messages = listOf(LlmMessage.user("...")),
)
```

The broker:

- Generates a JSON Schema from `Sentiment`'s `SerialDescriptor`.
- Passes it to the gateway's structured-output channel (OpenAI's `response_format = { type: "json_schema", ... }`; Anthropic's forced `respond_in_json` tool).
- Parses the resulting JSON back to a `Sentiment`.

If the model refuses or the response fails schema validation, the broker throws — there is no "maybe it's text" fallback. Treat that as a signal to inspect prompts.

### 3. Use the typed result

```kotlin
when (result.label) {
    "positive" -> ...
    "negative" -> ...
    else -> ...
}
```

You're back in normal Kotlin land with type safety.

## Provider notes

| Provider | Mechanism |
|---|---|
| OpenAI | `response_format = { type: "json_schema", json_schema = { ... } }` — first-class schema enforcement. |
| Anthropic | Synthetic forced tool `respond_in_json` with `tool_choice = { type: "tool", name: "respond_in_json" }`. The Anthropic API does not (yet) expose a first-class `response_format` knob; the broker hides this. |
| Ollama | Model-dependent. Some models support JSON-mode via Ollama's `format: "json"` setting. Generic models may need prompt-engineered enforcement; expect occasional malformed output. |

Behavior is unified at the broker level: the same `generateObject` call works against all three. The differences are below the seam.

## Nested and collection types

```kotlin
@Serializable
data class Person(val name: String, val age: Int)

@Serializable
data class Roster(val people: List<Person>)

val roster: Roster = broker.generateObject(
    messages = listOf(LlmMessage.user("Build a 3-person roster of fictional pirates.")),
)
```

Lists, maps with `String` keys, and nested data classes all serialise correctly. Sealed-class polymorphism is supported via `kotlinx.serialization`'s normal mechanism — declare a `classDiscriminator` if you need it.

## Related examples

- [`examples/simple-structured`](https://github.com/svetzal/mojentic-kt/tree/main/examples/simple-structured) — minimal sentiment extraction.
- [`examples/iterative-solver`](https://github.com/svetzal/mojentic-kt/tree/main/examples/iterative-solver) — uses structured output for the solver's per-step decisions.
- [`examples/react`](https://github.com/svetzal/mojentic-kt/tree/main/examples/react) — ReAct agent emits structured action selections.
