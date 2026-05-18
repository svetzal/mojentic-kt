# Building Chatbots

## Why use a chat session?

A bare LLM call is stateless: every request is independent, and the model sees only what you put in the `messages` list. A chatbot, by contrast, is *conversational* — the assistant remembers what the user just said, and the user expects to be able to refer back to it ("yes, do that for me too").

`ChatSession` wraps `LlmBroker` with the bookkeeping required for a multi-turn conversation: it appends each user turn, each assistant reply, and (when tools are involved) each tool-call round, and forwards the full history to the broker on every turn.

## When to apply this approach

Use a `ChatSession` when:

- The interaction is a conversation, not a one-shot transformation.
- The assistant needs to recall prior turns to make sense of the current one.
- You want to plug in tools and let the LLM decide when to call them — `ChatSession` runs the tool/response loop until the model produces a terminal reply.

For one-shot prompts (transform this text, classify this string, extract these fields), call `LlmBroker.generate()` or `generateObject()` directly — `ChatSession` adds overhead you don't need.

## Getting started

```kotlin
import com.mojentic.chat.ChatSession
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.openai.OpenAiGateway

suspend fun main() {
    val gateway = OpenAiGateway(apiKey = System.getenv("OPENAI_API_KEY"))
    val broker = LlmBroker(model = "gpt-4o-mini", gateway = gateway)

    val chat = ChatSession(
        broker = broker,
        systemPrompt = "You are a concise assistant. Reply in one sentence.",
    )

    println(chat.send("What is the capital of Iceland?"))
    println(chat.send("And the population?"))
}
```

The second `send` invocation produces a sensible answer because the session forwarded the first exchange to the broker — the model knows what "the population" refers to.

## Step-by-step

### 1. Build a broker

```kotlin
val gateway = OpenAiGateway(apiKey = System.getenv("OPENAI_API_KEY"))
val broker = LlmBroker(model = "gpt-4o-mini", gateway = gateway)
```

The gateway is a thin wrapper over a specific provider. The broker normalises model behaviour across providers — same surface for OpenAI, Ollama, Anthropic.

### 2. Open a session

```kotlin
val chat = ChatSession(
    broker = broker,
    systemPrompt = "...",
    temperature = 0.2,
)
```

`systemPrompt` is added to the front of the history exactly once. `temperature` defaults to the broker default; override when you need it.

### 3. Send turns

```kotlin
val reply = chat.send("user text")
```

Each call appends a user message, runs `broker.generate(...)` over the full history, and appends the assistant's reply before returning the text. `chat.messages` exposes the current transcript if you need to render it.

## Adding tools

Tools let the model take action mid-conversation: look something up, call an API, ask the user a clarifying question, run a calculation. Implement the `LlmTool` interface and pass tools through the session constructor:

```kotlin
import com.mojentic.llm.tools.CurrentDateTimeTool

val chat = ChatSession(
    broker = broker,
    systemPrompt = "You are a date-aware assistant.",
    tools = listOf(CurrentDateTimeTool()),
)

println(chat.send("What's today's date?"))
```

`ChatSession` handles the tool/response loop transparently: the model emits a tool call, the session executes the tool, appends the result, and re-prompts the model — repeating until the model produces a terminal text reply.

Multiple parallel tool calls in a single assistant turn are dispatched concurrently via `ParallelToolRunner` — voice-mode and high-latency tools benefit from this without any extra wiring.

## Streaming

For UIs that want token-by-token output, switch from `send` to `sendStream`:

```kotlin
chat.sendStream("Tell me a longer story.").collect { chunk ->
    print(chunk)
}
```

Streaming is fully tool-aware — if the model calls a tool mid-stream, the session pauses output, executes the tool, and resumes.

## Resetting the conversation

```kotlin
chat.reset()
```

Clears all messages except the system prompt. Useful for "new chat" buttons in a chat UI.

## Related examples

- [`examples/chat-session`](https://github.com/svetzal/mojentic-kt/tree/main/examples/chat-session) — minimal chat loop reading stdin.
- [`examples/chat-session-with-tool`](https://github.com/svetzal/mojentic-kt/tree/main/examples/chat-session-with-tool) — chat plus a date-resolver tool.
- [`examples/streaming`](https://github.com/svetzal/mojentic-kt/tree/main/examples/streaming) — token-by-token streaming.
- [`examples/realtime-text`](https://github.com/svetzal/mojentic-kt/tree/main/examples/realtime-text) — chat over the OpenAI Realtime WebSocket endpoint.
