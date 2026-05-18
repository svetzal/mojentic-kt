# Image Analysis

## Why a separate guide?

Sending an image to an LLM looks like sending text — same broker, same `generate` — but the message construction is different enough to be worth its own page. The Kotlin port models a user message as either a single text body or a list of `LlmContentPart` blocks, and image input is just a `Image` content part alongside a `Text` part.

The same surface works for OpenAI's `gpt-4o`-family vision models and Anthropic's Claude vision models. The gateway translates to each provider's wire format.

## When to apply this approach

Use multimodal messages for any task where the input is "this picture, plus a question":

- Image captioning, alt-text generation.
- Visual classification (does this image contain X?).
- OCR-adjacent tasks (read this whiteboard, transcribe this receipt).
- Diagram / chart interpretation.

For pure OCR with predictable output, dedicated OCR services will outperform an LLM on cost and latency. The LLM wins when you need *interpretation*, not just transcription.

## Getting started

```kotlin
import com.mojentic.llm.LlmBroker
import com.mojentic.llm.LlmContentPart
import com.mojentic.llm.LlmMessage
import com.mojentic.openai.OpenAiGateway
import okio.FileSystem
import okio.Path.Companion.toPath

suspend fun main() {
    val gateway = OpenAiGateway(apiKey = System.getenv("OPENAI_API_KEY"))
    val broker = LlmBroker(model = "gpt-4o", gateway = gateway)

    val imageBytes = FileSystem.SYSTEM.read("./sample.jpg".toPath()) { readByteArray() }

    val response = broker.generate(
        messages = listOf(
            LlmMessage.user(
                parts = listOf(
                    LlmContentPart.Text("What's in this picture? Reply in one sentence."),
                    LlmContentPart.Image(bytes = imageBytes, mimeType = "image/jpeg"),
                ),
            ),
        ),
    )

    println(response.content)
}
```

Two-part user message: text prompt + image bytes. The broker normalises the on-the-wire encoding (base64 + provider-specific envelope), so you don't need to do that yourself.

## Step-by-step

### 1. Read the image as bytes

```kotlin
val imageBytes = FileSystem.SYSTEM.read("./sample.jpg".toPath()) { readByteArray() }
```

`okio` is Mojentic's preferred I/O abstraction — works on JVM, Android, and iOS without a special case. Anywhere you can produce a `ByteArray`, you can produce an `LlmContentPart.Image`.

For images coming from HTTP, decode the response body into a `ByteArray` directly. For Android camera input, route the bitmap through `ByteArrayOutputStream`.

### 2. Build a multipart user message

```kotlin
LlmMessage.user(
    parts = listOf(
        LlmContentPart.Text("..."),
        LlmContentPart.Image(bytes = imageBytes, mimeType = "image/jpeg"),
    ),
)
```

`LlmContentPart` is a sealed class — `Text` and `Image` are its concrete cases. Order matters: most providers attend more reliably to the prompt when text comes first, image second. The Kotlin port preserves the order you supply.

`mimeType` is required and used directly by the gateway to set the right `data:` URL prefix. `image/jpeg`, `image/png`, `image/webp`, and `image/gif` are supported.

### 3. Call the broker

```kotlin
val response = broker.generate(messages = listOf(userMessage))
```

Same `generate` you'd use for a text-only message. Pick a vision-capable model (`gpt-4o`, `gpt-4o-mini`, `claude-3-5-sonnet`-class) — calling a non-vision model with an image part will fail at the provider with an unambiguous error message.

## Multiple images

Just add more parts:

```kotlin
LlmMessage.user(
    parts = listOf(
        LlmContentPart.Text("Compare these two pictures."),
        LlmContentPart.Image(bytes = pictureA, mimeType = "image/jpeg"),
        LlmContentPart.Image(bytes = pictureB, mimeType = "image/jpeg"),
    ),
)
```

Provider limits apply — most cap around 4–20 images per turn depending on size. Check the provider's docs before designing a workflow that floods many images at once.

## Structured output + images

`generateObject<T>()` works with multimodal input. Combine the image analysis above with a `@Serializable` result type to extract typed metadata from images:

```kotlin
@Serializable
data class Receipt(val merchant: String, val totalCents: Int, val currency: String)

val receipt: Receipt = broker.generateObject(
    messages = listOf(
        LlmMessage.user(
            parts = listOf(
                LlmContentPart.Text("Extract the merchant, total amount, and currency."),
                LlmContentPart.Image(bytes = imageBytes, mimeType = "image/jpeg"),
            ),
        ),
    ),
)
```

The schema enforcement applies to the *output*; the *input* is whatever mix of text and images you give the broker.

## Provider notes

| Provider | Notes |
|---|---|
| OpenAI | Sends as `image_url` content part with `data:image/...;base64,...` body. Supports JPEG, PNG, WebP, GIF. |
| Anthropic | Sends as `image` content part with `source = { type: "base64", media_type, data }`. Same set of formats. |
| Ollama | Model-dependent — `llava`-family models accept images. Mojentic forwards the bytes; the underlying Ollama HTTP API decides what it can handle. |

## Related examples

- [`examples/image-analysis`](https://github.com/svetzal/mojentic-kt/tree/main/examples/image-analysis) — minimal vision call against a local file.
- [`examples/chat-session-with-tool`](https://github.com/svetzal/mojentic-kt/tree/main/examples/chat-session-with-tool) — extend to a vision-capable chatbot that calls tools based on what it sees.
