# Single-turn stream events

## Single-turn streaming with terminal completion evidence

Use `LlmBroker.generateStreamEvents` when incomplete output must never count
as a result. It streams one turn as a `Flow<CompletionStreamEvent>`:

```kotlin
broker.generateStreamEvents(model, messages, config).collect { event ->
    when (event) {
        is CompletionStreamEvent.Content -> print(event.text)
        is CompletionStreamEvent.Completed -> println("\nfinish=${event.evidence.finishReason} usage=${event.evidence.usage}")
        is CompletionStreamEvent.Error -> println("\nfailed: ${event.reason}")
    }
}
```

The flow emits zero or more `Content` events in order, then exactly one
terminal event. Nothing follows the terminal event.

- `Completed(evidence)` is the only success. `evidence` is a
  `CompletionEvidence` with `finishReason`, `usage`, `providerModel`, and
  `metadata`. Each field is `null` when the provider did not report it.
- `Error(reason)` is a failed turn. Content that arrived before the error is
  evidence of what the provider sent. It is not a result, even when it parses
  as valid JSON.

### Completion rules

| Gateway | Success requires |
|---|---|
| OpenAI | `finish_reason: "stop"` **and** the `data: [DONE]` marker |
| Ollama | A final frame with `done: true` **and** `done_reason: "stop"` |

Ollama servers too old to send `done_reason` cannot use this API. Their final
frame is always an incomplete completion.

### Error reasons

| `StreamErrorReason` | Cause |
|---|---|
| `IncompleteCompletion(evidence)` | The terminal marker arrived with a finish reason other than `stop`, for example `length` |
| `IncompleteStream(evidence)` | The stream ended without a terminal marker. `evidence` holds what arrived first, or is `null` |
| `ProviderError(detail, status)` | The provider sent an error frame, or answered with a non-2xx status |
| `UnexpectedToolCalls` | The provider streamed a native tool call |
| `InvalidStreamEvent(payload)` | A frame could not be parsed or had an unexpected shape |
| `StreamEventsUnsupported` | The gateway does not implement `StreamEventsGateway` |
| `RequestFailed(cause)` | The connection or the body read failed |

### Behaviour

- The call takes messages and an optional `CompletionConfig`. It has no tools
  parameter, sends no tools, and forces `maxToolIterations` to zero. It never
  retries or recurses.
- Each collection sends one HTTP request. Stopping collection early (for
  example with `first()`, `take(n)`, or by cancelling the collecting
  coroutine) cancels the request.
- The OpenAI and Ollama gateways implement `StreamEventsGateway`. Other
  gateways, including Anthropic, yield one `StreamEventsUnsupported` error
  without sending a request.
- `CompletionConfig.responseFormat` is forwarded as in other requests. The
  OpenAI request also sets `stream_options: {include_usage: true}` so that
  usage is reported.

### Tracing

The broker records an `LlmCallEvent` when the request starts. When the
terminal event arrives, success or failure, it records an `LlmResponseEvent`
with the content received so far and the terminal evidence (`usage`,
`providerModel`, `finishReason`, `metadata`). A consumer that stops early
leaves a traced call and no traced response. That is not an error. An
unsupported gateway records nothing.

Existing `stream` behaviour is unchanged. It does not provide terminal proof.
