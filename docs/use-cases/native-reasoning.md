# Native reasoning and migration to 3.0

Native reasoning remains separate from visible content: responses use `thinking`,
streams use `ThinkingChunk`, and caller-owned assistant history uses
`LlmMessage.reasoningContent`. Tool results never carry assistant reasoning.

```kotlin
val config = CompletionConfig(
    reasoningEffort = ReasoningEffort.XHIGH,
    reasoningProfile = ReasoningProfile.qwen38(),
    preserveReasoning = true,
)
val session = ChatSession(LlmBroker(gateway), model, config = config)
val answer = session.send("Explain the result")
```

Profiles are explicit caller assertions about model capabilities. The model name
does not select a profile. Qwen3.8 accepts exactly Disabled, Low, Medium and XHigh,
and supports native history. Enabled and High fail with `LlmGatewayException`
before HTTP when this profile is selected. Custom profiles can declare a different
exact effort set and history capability. Profiles reject both automatic retention
and caller-supplied native history when history is unsupported.

Omitting `reasoningEffort` sends no effort or enablement field. It leaves the
provider default unknown, even with a profile or retention selected. Disabled
and Enabled are separate controls, as are High and XHigh.

| Kotlin effort | Ollama `think` | oMLX |
| --- | --- | --- |
| `null` | omitted | both fields omitted |
| `DISABLED` | boolean `false` | boolean `enable_thinking: false` |
| `ENABLED` | boolean `true` | boolean `enable_thinking: true` |
| `LOW` | string `"low"` | `reasoning_effort: "low"` |
| `MEDIUM` | string `"medium"` | `reasoning_effort: "medium"` |
| `HIGH` | string `"high"` | `reasoning_effort: "high"` |
| `XHIGH` | string `"xhigh"` | `reasoning_effort: "xhigh"` |

In 2.1, Ollama collapsed Low, Medium and High to boolean `true`. Select Enabled
in 3.0 if boolean enablement is the behavior you need. oMLX's existing named
Low/Medium/High mappings remain unchanged. Existing OpenAI named Low/Medium/High
handling and Anthropic's legacy warning/ignore behavior remain unchanged.
Those adapters reject the new boolean controls, XHigh and automatic native
retention explicitly; OpenAI also rejects oMLX native history. A profile can
validate legacy efforts on either adapter, but does not add provider support.

Retention defaults to false. When true, the broker retains each completed
assistant response once, with its visible content, reasoning and all tool calls,
then appends the associated tool results. It uses that transcript for ordinary
and streamed continuations. Sessions also retain completed turns for the next
user message. When false, legacy continuation and session behavior is preserved.
Caller-supplied history is explicit and is serialized independently of automatic
retention: Ollama uses `thinking`, oMLX uses `reasoning_content` on assistants.

For oMLX, retention sends
`chat_template_kwargs: {"preserve_thinking": true}`. This is a request to the
server; forced server settings can override it. Structured output uses the same
controls, profile checks and history serialization as ordinary and streaming
completion. Ollama structured responses now expose native `thinking` too.

Cancellation and failed or unfinished streams cannot become completed session
history. Sessions roll back failed turns. Cancellation during a native delta does
not execute tools. A failure after an ordinary completed tool response does not
repeat already executed tools. The existing recursion budget and recovery policy
continue to apply; retention does not change retry admission or replay safety.
Partial native stream delivery still prevents replay before the admission hook,
even when a caller supplied a hook that would allow a retry.

These changes add enum cases and fields to public data classes. Recompile callers
and update exhaustive `when` expressions and constructor/copy references. Native
history can contain private reasoning: choose retention explicitly for your use
case. See [the conformance record](../../CONFORMANCE-3.0.0.md) for executable cases
and the remaining provider-verification gap.
