# Local models with oMLX

[oMLX](https://github.com/jundot/omlx) is an LLM server for Apple Silicon. It
speaks the OpenAI chat-completions protocol. Use `OmlxGateway` to connect to
it. The gateway is in the `mojentic-openai` module, in the `com.mojentic.omlx`
package.

```kotlin
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmMessage
import com.mojentic.omlx.OmlxGateway

suspend fun main() {
    val gateway = OmlxGateway()
    val broker = LlmBroker(gateway)
    val response = broker.complete(
        model = "Qwen3.8-27B-MLX-8bit",
        messages = listOf(LlmMessage.user("Hello!")),
    )
    println(response.content)
    gateway.close()
}
```

The `examples/omlx-simple` project runs one turn against a local server:
`./gradlew :examples:omlx-simple:run`.

## Why not the OpenAI gateway

`OpenAIGateway` can connect to oMLX with a different host, but it changes requests
for model names that it does not know. It can remove `reasoning_effort`,
remove tools, and rename `max_tokens`. `OmlxGateway` uses the same message
adapter and stream parsers, but it sends every configured parameter unchanged,
for every model name. It also has its own environment variables, so it does not
collide with an OpenAI configuration.

## Configuration

Each setting comes from the constructor argument first, then from the
environment variable, then from the default.

| Setting | Argument | Environment | Default |
|---|---|---|---|
| Host | `host` | `OMLX_HOST` | `http://localhost:8000` (`DEFAULT_OMLX_HOST`) |
| API key | `apiKey` | `OMLX_API_KEY` | none |
| Timeout | `timeout` (`Duration`) | `OMLX_TIMEOUT`, in milliseconds | 10 minutes (`DEFAULT_OMLX_TIMEOUT`) |

- Do not put `/v1` in the host. The gateway adds `/v1` to every path.
- With an API key, each request has the header `Authorization: Bearer <key>`.
  Without a key, or with a blank key, requests have no authorization header.
- The gateway reads environment variables on the JVM only. On Android and
  iOS, give explicit values.
- One timeout applies to every request, including a model load. It limits the
  connection and each wait for data. Local models are slow: a 16384-token
  reply at 16 tokens a second takes 17 minutes. Thus the default is longer
  than the other gateways use.

## Chat requests

The gateway sends `model`, `messages`, `temperature` and `max_tokens` from the
`CompletionConfig`. It sends `reasoning_effort`, `response_format` and `tools`
when you set them. It never sends `max_completion_tokens`. It does not send
`numCtx` or `numPredict`, because oMLX sets the context length for each model.

## Thinking

`reasoningEffort` goes to oMLX as `low`, `medium` or `high`. oMLX gives it to
the chat template of the model, so the effect depends on the model. When
`reasoningEffort` is `null`, the model uses its default. Qwen 3 models think
by default.

The reasoning of the model is in `LlmGatewayResponse.thinking`. The legacy
`stream()` API emits it as `ThinkingChunk` events. `generateStreamEvents` emits
no event for it.

## Truncation

When `max_tokens` stops the model while it thinks, a non-streaming response
puts the partial reasoning in `content`. Then `thinking` is `null` and
`finishReason` is `length`. A streaming response keeps the partial reasoning
in the reasoning deltas. The gateway does not move text between the fields.

`content` is an answer only when `finishReason` is `stop`. Examine the finish
reason before you use the content.

## Structured output

`completeJson` sends `response_format` with the type `json_schema` and the
schema name `response`. The broker then decodes the object into your type.

oMLX can fail to compile a grammar for a JSON format. Then it tells the model
the format in the prompt and adds a `Warning` header to the response. For a
structured request (`completeJson`, or `CompletionConfig.responseFormat` set to
`ResponseFormat.Json`), the gateway puts the header value in
`LlmGatewayResponse.metadata` with the key `response_format_warning` and logs a
warning. When there are several `Warning` headers, it joins them with `, `.
The gateway does not retry and does not fail. The header is evidence that oMLX
did not enforce the format, so validate the content.

## Streaming

The two streaming APIs support oMLX:

- `LlmBroker.generateStreamEvents` follows the OpenAI completion rules in
  [Stream Events](stream-events.md). Success needs `finish_reason: "stop"` and
  the `[DONE]` marker. A native tool call is `UnexpectedToolCalls`.
- `LlmBroker.stream` and `OmlxGateway.stream` behave as the OpenAI gateway
  does. oMLX sends all of each tool call in one delta.

oMLX starts each stream with a keep-alive frame whose `model` is `keepalive`,
and sends more during a long prefill. The gateway drops these frames before it
parses the stream, so `keepalive` never shows as the provider model.

## Models

| Call | Request | Result |
|---|---|---|
| `availableModels()` | `GET /v1/models` | The model ids, sorted |
| `loadModel(id)` | `POST /v1/models/{id}/load` | Returns when the model is in memory |
| `unloadModel(id)` | `POST /v1/models/{id}/unload` | Returns when the model is out of memory |

A chat request loads its model automatically. Use `loadModel` to load a model
before the first request. Unloading a model that is not loaded is an error
(400). oMLX downloads models only through its admin dashboard, so there is no
pull call.

## Embeddings

`OmlxGateway` is also an `EmbeddingsGateway`. `embed(model, text)` sends one
request to `POST /v1/embeddings` with the whole text. The gateway does not
split the text. oMLX has no standard embedding model, so you must give a
model.

If the model is blank, the gateway throws `IllegalArgumentException` and
sends no request. A chat model causes a provider error (400). `embedBatch`
sends one request for each text.

## Errors

A response that is not 2xx causes `LlmGatewayException`. Its message contains
the HTTP status and the response body without change. Examples are a wrong
API key (401), an unknown model (404, the body lists the models) and a chat
model used for embeddings (400). In `generateStreamEvents`, the same response
is a `ProviderError` with the status and the body.
