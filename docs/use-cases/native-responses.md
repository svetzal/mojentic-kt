# Native responses

## Caller-owned context and native responses

Use `broker.generateResponse(model, messages, tools, config)` to receive one
native gateway response without
executing tools, extending history or making a follow-up request. Assemble the
complete message array before each call. The broker traces the supplied request
and returned response; it does not read repository guidance or apply a context
policy.

The existing convenience completion method still executes tools and follows up.
Choose a serial or parallel runner according to the tools' effects. Parallel
execution does not make dependent edits safe.

Set `CompletionConfig(maxToolIterations = null)` to disable the tool-round
limit.
Existing finite defaults remain unchanged. Concurrency controls simultaneous
work; it is not a task budget or a loop detector.

Unknown tools produce ordered error outcomes. `ParallelToolRunner(maxConcurrency
= 4)` bounds active execution while retaining request order. Coroutine
cancellation propagates to child calls.

## Provider evidence in response traces

`LlmGatewayResponse` carries what the provider reported about a response:

| Field | Source | When absent |
|---|---|---|
| `usage` | Provider usage as reported, as a `JsonObject` | `null` |
| `providerModel` | Model name the provider reported | `null` |
| `finishReason` | Provider finish reason | `null` |
| `metadata` | Other provider metadata, for example Ollama durations | `null` |

The broker copies these fields unchanged into the `LlmResponseEvent` it
records for `complete`, `generateResponse`, and `completeJson`. The event's
`model` field stays the configured request model. The legacy `stream` API
records no evidence.

Usage is never estimated. When the provider reports none, `usage` is `null`.
Configured model names and text length are not substitutes for reported
metadata.

| Gateway | `usage` | `metadata` |
|---|---|---|
| OpenAI | The response `usage` object | `null` |
| Ollama | `prompt_eval_count` and `eval_count`, when present | `total_duration`, `load_duration`, `prompt_eval_duration`, `eval_duration`, when present |
| Anthropic | The response `usage` object | `null` |

Gateways fill structured-output evidence through
`LlmGateway.completeJsonResponse`. A custom gateway that does not override it
reports no evidence for structured output.
