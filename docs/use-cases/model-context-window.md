# Model context windows

All completion gateways expose `suspend fun modelContextWindow(model: String)`.
It returns a `ModelContextWindow` with positive `maxContextTokens`, its source
`field`, and an optional Ollama `modelNumCtx`. Null means not advertised; it does
not mean a failed query. HTTP failures, transport failures and malformed metadata
throw `LlmGatewayException`. Cancellation propagates normally.

| Gateway | Discovery |
| --- | --- |
| oMLX | `GET /v1/models` on the configured host, with its configured Bearer key when present. Exact `id` selection; `max_model_len` supplies capacity. Missing model or field returns null. |
| Ollama | `POST /api/show` with only `{"model": model}`. Any architecture key ending in `.context_length` supplies capacity and its full `model_info` path supplies provenance. A `num_ctx` parameter line supplies `modelNumCtx`. |
| OpenAI | Exact existing registry entry with `field = "registry"`; no HTTP and no pattern fallback for unknown models. |
| Anthropic and existing custom gateways | Null, without HTTP. Custom gateways may override discovery when they have provider evidence. |

Discovery never loads a model, mutates configuration, or changes later ordinary,
structured or streaming requests. Ollama's missing `model_info` or unmatched
architecture returns null. Malformed object shapes and present nonpositive or
noninteger window fields fail, as do malformed `num_ctx` lines. Additional fields
are ignored. This metadata operation accepts any HTTP 2xx response and rejects redirects without following them.

```kotlin
import com.mojentic.llm.CompletionConfig
import com.mojentic.ollama.OllamaGateway
import com.mojentic.ollama.effectiveContextWindow

suspend fun inspectWindow(gateway: OllamaGateway, model: String) {
    val advertised = gateway.modelContextWindow(model)
    val config = CompletionConfig(numCtx = 8192)
    val requestUpperBound = effectiveContextWindow(advertised, config)
    println("Capacity: $advertised; request upper bound: $requestUpperBound")
}
```

The pure helper clamps advertised capacity by the caller's `numCtx` first, then
by the model's `modelNumCtx`, then uses advertised capacity alone. Unknown
capacity returns null even with a caller limit. To represent an omitted caller
limit explicitly, use `effectiveContextWindow(advertised, numCtx = null)`.
The overload accepting `CompletionConfig` uses its nullable `numCtx`. With an
unset config it uses the model parameter, then advertised capacity. It does not
invent a 32,768-token limit. See [request fidelity](request-fidelity.md).

Without a caller or model limit the result is an upper bound. The client cannot
see `OLLAMA_CONTEXT_LENGTH` or the server's built-in default. For the actual
loaded size, an application can query `GET /api/ps` after its first completion.
Budgeting and context-pressure policy stay in the application.
