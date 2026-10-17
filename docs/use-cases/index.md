# Use Cases

Task-oriented guides covering the four core capabilities of Mojentic's Kotlin port. Start here.

| Guide | What you'll learn |
|---|---|
| [Building Chatbots](building-chatbots.md) | Multi-turn conversation, tool calling, streaming, conversation reset. |
| [Structured Output](structured-output.md) | Typed `completeJson` calls, JSON schema generation, provider quirks. |
| [Building Agents](building-agents.md) | Iterative solver, recursive solver, ReAct, async pubsub dispatcher, shared working memory, tracing. |
| [Image Analysis](image-analysis.md) | Multipart user messages, vision-capable models, multimodal structured output. |
| [Stream Events](stream-events.md) | Single-turn streaming that ends with proof of completion or a typed error. |
| [Native Responses](native-responses.md) | Single native responses with caller-owned context, unlimited tool rounds, bounded parallel tools. |
| [Native Reasoning](native-reasoning.md) | Explicit efforts and profiles, provider mappings, opt-in native history and 3.0 migration. |
| [Model Context Windows](model-context-window.md) | Advertised capacity, provenance, and Ollama effective context upper bounds. |
| [Local Models with oMLX](omlx.md) | The oMLX gateway: configuration, thinking, truncation, structured-output warnings, model load and unload. |

These guides target the **Kotlin port specifically**. For the cross-language design rationale, see [`PARITY.md`](https://github.com/svetzal/mojentic-unify/blob/main/PARITY.md) in the monorepo.
