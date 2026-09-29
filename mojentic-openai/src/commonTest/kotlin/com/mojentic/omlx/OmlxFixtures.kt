// The fixtures are verbatim single-line server responses, so their lines stay long.
@file:Suppress("ktlint:standard:max-line-length")

package com.mojentic.omlx

/**
 * Raw responses from a live oMLX server, for the oMLX gateway tests.
 *
 * Provenance: copied byte for byte from `fixtures/omlx/` in the
 * `mojentic-unify` monorepo (the contract is `OMLX-2026-09.md` there). They
 * were captured on 2026-09-29 from oMLX 0.7.0rc1 (Homebrew, macOS, Apple
 * Silicon) serving `Qwen3.8-27B-MLX-8bit`, and are unedited. Response headers
 * were not captured, so tests that need one supply it. The fixtures are
 * Kotlin strings rather than resource files because `commonTest` has no
 * resource loading that works on every target, including iOS.
 *
 * Each constant is named after its source file. Every stream starts with a
 * keep-alive frame whose `model` is `keepalive`.
 */
internal object OmlxFixtures {
    /** `chat_thinking.json` */
    const val CHAT_THINKING: String = """ {"id":"chatcmpl-8c2b3fa6","object":"chat.completion","created":1790679550,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"message":{"role":"assistant","content":"hello","reasoning_content":"We need to reply exactly: hello. User said \"Reply with exactly: hello\". Need final \"hello\". Ensure no extra."},"finish_reason":"stop"}],"usage":{"prompt_tokens":57,"completion_tokens":30,"total_tokens":87,"input_tokens":57,"output_tokens":30,"prompt_tokens_details":{"cached_tokens":0},"model_load_duration":8.78,"total_time":5.77}}"""

    /** `chat_thinking_disabled.json` */
    const val CHAT_THINKING_DISABLED: String = """{"id":"chatcmpl-42f61c60","object":"chat.completion","created":1790679568,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":17,"completion_tokens":1,"total_tokens":18,"input_tokens":17,"output_tokens":1,"prompt_tokens_details":{"cached_tokens":0},"total_time":1.05}}"""

    /** `chat_tool_call.json` */
    const val CHAT_TOOL_CALL: String = """ {"id":"chatcmpl-3ad05b71","object":"chat.completion","created":1790679575,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"message":{"role":"assistant","reasoning_content":"The user is asking to use a tool to find out today's date. Let me call the resolve_date tool.","tool_calls":[{"id":"call_bd4d55c2","type":"function","function":{"name":"resolve_date","arguments":"{\"relative\": \"today\"}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":327,"completion_tokens":52,"total_tokens":379,"input_tokens":327,"output_tokens":52,"prompt_tokens_details":{"cached_tokens":0},"total_time":4.86}}"""

    /** `chat_after_tool_result.json` */
    const val CHAT_AFTER_TOOL_RESULT: String = """ {"id":"chatcmpl-d04c1cba","object":"chat.completion","created":1790679887,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"message":{"role":"assistant","content":"Today's date is **September 29, 2026** (2026-09-29).","reasoning_content":"The tool returned the date: 2026-09-29. I should tell the user the current date."},"finish_reason":"stop"}],"usage":{"prompt_tokens":372,"completion_tokens":58,"total_tokens":430,"input_tokens":372,"output_tokens":58,"prompt_tokens_details":{"cached_tokens":367},"total_time":3.84}}"""

    /** `chat_json_schema.json` */
    const val CHAT_JSON_SCHEMA: String = """{"id":"chatcmpl-25ef94b9","object":"chat.completion","created":1790679577,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"message":{"role":"assistant","content":"{\"name\": \"Ada\", \"age\": 36}"},"finish_reason":"stop"}],"usage":{"prompt_tokens":63,"completion_tokens":19,"total_tokens":82,"input_tokens":63,"output_tokens":19,"prompt_tokens_details":{"cached_tokens":0},"total_time":1.8}}"""

    /** `chat_length.json` */
    const val CHAT_LENGTH: String = """{"id":"chatcmpl-adf04565","object":"chat.completion","created":1790679884,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"message":{"role":"assistant","content":"We need to respond to"},"finish_reason":"length"}],"usage":{"prompt_tokens":56,"completion_tokens":5,"total_tokens":61,"input_tokens":56,"output_tokens":5,"prompt_tokens_details":{"cached_tokens":51},"total_time":0.91}}"""

    /** `stream_thinking.sse` */
    const val STREAM_THINKING_SSE: String = """data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":0,"model":"keepalive","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679814,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"role":"assistant"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679815,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":"\nWe"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679815,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" need to reply"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679815,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" exactly: hello"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679815,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":". User said"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679815,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" \"Reply with"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679816,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" exactly: hello"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679816,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":"\". Need final"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679816,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" \"hello\"."}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679816,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" Ensure no extra"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679816,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":".\n"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"content":"\n\nhello"}}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

data: {"id":"chatcmpl-05014002","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[],"usage":{"prompt_tokens":57,"completion_tokens":30,"total_tokens":87,"input_tokens":57,"output_tokens":30,"prompt_tokens_details":{"cached_tokens":52},"time_to_first_token":0.73,"time_to_first_visible_token":0.79,"total_time":2.7,"prompt_eval_duration":0.73,"generation_duration":1.97,"prompt_tokens_per_second":78.03,"generation_tokens_per_second":15.22}}

data: [DONE]

"""

    /** `stream_tool_call.sse` */
    const val STREAM_TOOL_CALL_SSE: String = """data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":0,"model":"keepalive","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679613,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"role":"assistant"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679615,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":"\nThe"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679615,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" user is asking"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679615,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" for today's"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679615,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" date and wants"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679616,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" me to use"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679616,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" the resolve_date"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679616,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" tool. Let"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679616,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" me call it"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679616,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" with \"today"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679617,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":"\" as the"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679617,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" relative parameter."}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679617,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":"\n"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679617,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"content":"\n\n"}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679619,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_659d0e77","type":"function","function":{"name":"resolve_date","arguments":"{\"relative\": \"today\"}"}}]}}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679619,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

data: {"id":"chatcmpl-1c4c90b8","object":"chat.completion.chunk","created":1790679619,"model":"Qwen3.8-27B-MLX-8bit","choices":[],"usage":{"prompt_tokens":318,"completion_tokens":60,"total_tokens":378,"input_tokens":318,"output_tokens":60,"prompt_tokens_details":{"cached_tokens":0},"time_to_first_token":1.95,"time_to_first_visible_token":2.01,"total_time":5.67,"prompt_eval_duration":1.95,"generation_duration":3.72,"prompt_tokens_per_second":163.38,"generation_tokens_per_second":16.13}}

data: [DONE]

"""

    /** `stream_length.sse` */
    const val STREAM_LENGTH_SSE: String = """data: {"id":"chatcmpl-6a79f6b7","object":"chat.completion.chunk","created":0,"model":"keepalive","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

data: {"id":"chatcmpl-6a79f6b7","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"role":"assistant"}}]}

data: {"id":"chatcmpl-6a79f6b7","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":"\nWe"}}]}

data: {"id":"chatcmpl-6a79f6b7","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" need to respond"}}]}

data: {"id":"chatcmpl-6a79f6b7","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{"reasoning_content":" to"}}]}

data: {"id":"chatcmpl-6a79f6b7","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[{"index":0,"delta":{},"finish_reason":"length"}]}

data: {"id":"chatcmpl-6a79f6b7","object":"chat.completion.chunk","created":1790679817,"model":"Qwen3.8-27B-MLX-8bit","choices":[],"usage":{"prompt_tokens":56,"completion_tokens":5,"total_tokens":61,"input_tokens":56,"output_tokens":5,"prompt_tokens_details":{"cached_tokens":51},"time_to_first_token":0.22,"time_to_first_visible_token":0.29,"total_time":0.55,"prompt_eval_duration":0.22,"generation_duration":0.33,"prompt_tokens_per_second":250.94,"generation_tokens_per_second":15.26}}

data: [DONE]

"""

    /** `models.json` */
    const val MODELS: String = """{"object":"list","data":[{"id":"Qwen3.8-27B-MLX-8bit","object":"model","created":1790679817,"owned_by":"omlx","max_model_len":262144}]}"""

    /** `model_load.json` */
    const val MODEL_LOAD: String = """{"status":"ok","model_id":"Qwen3.8-27B-MLX-8bit","message":"Loaded Qwen3.8-27B-MLX-8bit"}"""

    /** `model_unload.json` */
    const val MODEL_UNLOAD: String = """{"status":"ok","model_id":"Qwen3.8-27B-MLX-8bit"}"""

    /** `error_model_not_loaded.json` */
    const val ERROR_MODEL_NOT_LOADED: String = """{"error":{"message":"Model not loaded: Qwen3.8-27B-MLX-8bit","type":"invalid_request_error","param":null,"code":null}}"""

    /** `error_model_not_found.json` */
    const val ERROR_MODEL_NOT_FOUND: String = """{"error":{"message":"Model 'nope' not found. Available models: Qwen3.8-27B-MLX-8bit","type":"not_found_error","param":null,"code":null}}"""

    /** `error_not_embedding_model.json` */
    const val ERROR_NOT_EMBEDDING_MODEL: String = """{"error":{"message":"Model 'Qwen3.8-27B-MLX-8bit' is not an embedding model. Use /v1/chat/completions for LLM models.","type":"invalid_request_error","param":null,"code":null}}"""
}
