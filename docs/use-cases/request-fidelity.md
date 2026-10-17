# Request fidelity

`CompletionConfig()` leaves `temperature`, `numCtx` and `maxTokens` unset.
Ordinary, structured and streaming requests omit unset provider fields. Recovery
reuses the same serialized payload on each attempt. Metadata lookup does not
change later requests, and first-token timing is unchanged by this correction.

```kotlin
val providerDefaults = CompletionConfig()
val explicit = CompletionConfig(temperature = 0.0, numCtx = 8192, maxTokens = 73)
val formerDefaults = CompletionConfig(temperature = 1.0, numCtx = 32_768, maxTokens = 16_384)
```

| Provider | Context | Output limit | Temperature |
| --- | --- | --- | --- |
| Ollama | `options.num_ctx` when set | `options.num_predict` when set | `options.temperature` when set |
| OpenAI | Omitted | `max_tokens` for chat, `max_completion_tokens` for reasoning, when set | When set; fixed registry models use their sole allowed value; forbidden temperatures are omitted |
| oMLX | Omitted | `max_tokens` when set | When set |
| Anthropic | Omitted | Explicit `max_tokens`, otherwise registered maximum or 4096 | When set |

Anthropic's required fallback registry recognizes `claude-sonnet-4-5`,
`claude-sonnet-4-5-20250929`, `claude-haiku-4-5`, and
`claude-haiku-4-5-20251001`, each with 64,000 output tokens. These match the
[Sonnet](https://platform.claude.com/docs/en/models/sonnet-4-5/overview) and
[Haiku](https://platform.claude.com/docs/en/models/haiku-4-5/overview) limits.
Unregistered IDs, including unrecognized suffixes, use 4096; no name guessing.
Explicit limits override the fallback without clamping or sentinel rewriting.
OpenAI warns when dropping an explicit effort on a model without that capability.
Reasoning mappings and caller-selected profiles otherwise keep their behavior.

This is a source and binary breaking change. Replace `numPredict = n` with
`maxTokens = n` and remove `-1` sentinel assignments. Sampling/context/output default constants
and old constructor/copy bridges are removed; recompile callers and handle null
when reading config or tracer sampling values. To preserve former numeric
settings, supply all three explicitly as above. Unset Ollama settings may mean a
smaller context or unlimited output; unset OpenAI output limits can increase
reasoning generation and cost. Client-side recovery, reasoning retention, profiles
and tool iteration limits remain client settings, never provider fields.

The Ollama effective-window helper uses explicit `numCtx`, then the model's
`num_ctx`, then advertised capacity, clamped to advertised capacity. Null config
values now naturally follow model/server precedence.

Local contract execution and outstanding paired verification are recorded in
[CONFORMANCE-3.0.0.md](../../CONFORMANCE-3.0.0.md).
