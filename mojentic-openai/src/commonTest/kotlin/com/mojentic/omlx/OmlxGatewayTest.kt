package com.mojentic.omlx

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.CompletionConfig
import com.mojentic.llm.LlmMessage
import com.mojentic.llm.LlmToolCall
import com.mojentic.llm.ReasoningEffort
import com.mojentic.llm.ResponseFormat
import com.mojentic.llm.tools.LlmTool
import com.mojentic.llm.tools.ToolDescriptor
import com.mojentic.omlx.OmlxTestServer.Companion.JSON_HEADERS
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class OmlxGatewayTest {
    private var gateway: OmlxGateway? = null
    private val messages = listOf(LlmMessage.user("Reply with exactly: hello"))
    private val model = "Qwen3.8-27B-MLX-8bit"

    @AfterTest
    fun tearDown() {
        gateway?.close()
    }

    private fun OmlxTestServer.open(apiKey: String? = null, host: String? = null): OmlxGateway =
        gateway(host = host, apiKey = apiKey).also { gateway = it }

    private val resolveDate = object : LlmTool {
        override val descriptor = ToolDescriptor(
            name = "resolve_date",
            description = "Resolve a relative date",
            parameters = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { put("relative", buildJsonObject { put("type", "string") }) })
            },
        )

        override suspend fun execute(arguments: JsonObject): String = "2026-09-29"
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content

    // 1. Configuration: /v1 prefix and bearer header

    @Test
    fun chatGoesToV1PathWithNoAuthorizationWithoutAKey() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.CHAT_THINKING)

        server.open().complete(model, messages)

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://localhost:8000/v1/chat/completions", request.url.toString())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun bearerHeaderIsSentWithAKey() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.MODELS)

        server.open(apiKey = "secret", host = "http://studio.local:9000/").availableModels()

        val request = server.requests.single()
        assertEquals("http://studio.local:9000/v1/models", request.url.toString())
        assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun oneTimeoutAppliesToEveryRequestIncludingLoad() = runTest {
        val server = OmlxTestServer { request ->
            val body = if (request.url.encodedPath.endsWith("/load")) OmlxFixtures.MODEL_LOAD else OmlxFixtures.CHAT_THINKING
            respond(body, HttpStatusCode.OK, JSON_HEADERS)
        }
        val gateway = server.gateway(timeout = 90.seconds).also { gateway = it }

        gateway.complete(model, messages)
        gateway.loadModel(model)

        server.requests.forEach { request ->
            val timeout = request.getCapabilityOrNull(HttpTimeoutCapability)
            assertEquals(90_000L, timeout?.socketTimeoutMillis, "socket timeout for ${request.url}")
            assertEquals(90_000L, timeout?.connectTimeoutMillis, "connect timeout for ${request.url}")
        }
    }

    // 2. Request body

    @Test
    fun requestBodyCarriesEveryConfiguredFieldUnchangedForAnyModelName() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.CHAT_TOOL_CALL)
        // "o3" in the name would make the OpenAI registry treat this as a reasoning model.
        val localModel = "o3-style-local-model"
        val config = CompletionConfig(
            temperature = 0.2,
            maxTokens = 512,
            reasoningEffort = ReasoningEffort.HIGH,
            responseFormat = ResponseFormat.Json(),
        )

        server.open().complete(localModel, messages, listOf(resolveDate), config)

        val body = server.body
        assertEquals(localModel, body.string("model"))
        assertEquals(0.2, body.getValue("temperature").jsonPrimitive.content.toDouble())
        assertEquals("512", body.string("max_tokens"))
        assertEquals("high", body.string("reasoning_effort"))
        assertEquals(buildJsonObject { put("type", "json_object") }, body["response_format"])
        val tool = body.getValue("tools").jsonArray.single().jsonObject
        assertEquals("function", tool.string("type"))
        assertEquals("resolve_date", tool.getValue("function").jsonObject.string("name"))
        assertEquals("user", body.getValue("messages").jsonArray.single().jsonObject.string("role"))
        listOf("max_completion_tokens", "num_ctx", "num_predict", "options", "stream_options").forEach {
            assertFalse(it in body, "$it must not be sent: $body")
        }
    }

    @Test
    fun maxTokensIsAlwaysSentAndNullReasoningEffortIsOmitted() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.CHAT_THINKING)

        server.open().complete(model, messages)

        val body = server.body
        assertEquals(CompletionConfig.DEFAULT_MAX_TOKENS.toString(), body.string("max_tokens"))
        assertFalse("reasoning_effort" in body, "null reasoning effort must be omitted: $body")
        assertFalse("response_format" in body, "absent format must be omitted: $body")
        assertFalse("tools" in body, "no tools were given: $body")
    }

    // 3. Thinking

    @Test
    fun reasoningContentMapsToThinkingAndUsageIsKeptAsReported() = runTest {
        val response = OmlxTestServer.answering(OmlxFixtures.CHAT_THINKING).open().complete(model, messages)

        assertEquals("hello", response.content)
        assertEquals(
            "We need to reply exactly: hello. User said \"Reply with exactly: hello\". Need final \"hello\". Ensure no extra.",
            response.thinking,
        )
        assertEquals("stop", response.finishReason)
        assertEquals(model, response.providerModel)
        assertEquals(Json.parseToJsonElement(OmlxFixtures.CHAT_THINKING).jsonObject["usage"], response.usage)
        assertEquals("8.78", response.usage?.string("model_load_duration"))
        assertNull(response.metadata)
    }

    @Test
    fun responseWithoutReasoningContentHasNullThinking() = runTest {
        val response = OmlxTestServer.answering(OmlxFixtures.CHAT_THINKING_DISABLED).open().complete(model, messages)

        assertEquals("hello", response.content)
        assertNull(response.thinking)
    }

    // 4. Tool calls

    @Test
    fun toolCallIsParsedAsTheOpenAIGatewayParsesIt() = runTest {
        val response = OmlxTestServer.answering(OmlxFixtures.CHAT_TOOL_CALL).open()
            .complete(model, messages, listOf(resolveDate))

        val call = response.toolCalls.single()
        assertEquals("call_bd4d55c2", call.id)
        assertEquals("resolve_date", call.name)
        assertEquals(buildJsonObject { put("relative", "today") }, call.arguments)
        assertNull(response.content)
        assertEquals("tool_calls", response.finishReason)
        assertTrue(response.thinking.orEmpty().startsWith("The user is asking"))
    }

    @Test
    fun toolResultRoundTripSendsTheCallAndResultAndMapsTheAnswer() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.CHAT_AFTER_TOOL_RESULT)
        val call = LlmToolCall(id = "call_bd4d55c2", name = "resolve_date", arguments = buildJsonObject { put("relative", "today") })
        val history = messages + LlmMessage.assistant(toolCalls = listOf(call)) + LlmMessage.tool("2026-09-29", call)

        val response = server.open().complete(model, history, listOf(resolveDate))

        val sent = server.body.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "tool"), sent.map { it.string("role") })
        val sentCall = sent[1].getValue("tool_calls").jsonArray.single().jsonObject
        assertEquals("call_bd4d55c2", sentCall.string("id"))
        assertEquals("function", sentCall.string("type"))
        assertFalse("index" in sentCall, "null fields stay omitted: $sentCall")
        assertEquals("resolve_date", sentCall.getValue("function").jsonObject.string("name"))
        assertEquals("""{"relative":"today"}""", sentCall.getValue("function").jsonObject.string("arguments"))
        assertEquals("call_bd4d55c2", sent[2].string("tool_call_id"))
        assertEquals("2026-09-29", sent[2].string("content"))
        assertEquals("Today's date is **September 29, 2026** (2026-09-29).", response.content)
        assertEquals("The tool returned the date: 2026-09-29. I should tell the user the current date.", response.thinking)
    }

    // 5. Structured output

    private val schema = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
                put("age", buildJsonObject { put("type", "integer") })
            },
        )
    }

    @Test
    fun structuredRequestSendsJsonSchemaNamedResponseAndParsesTheObject() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.CHAT_JSON_SCHEMA)

        val response = server.open().completeJsonResponse(model, messages, schema)

        val format = server.body.getValue("response_format").jsonObject
        assertEquals("json_schema", format.string("type"))
        assertEquals(
            buildJsonObject {
                put("name", "response")
                put("schema", schema)
            },
            format["json_schema"],
        )
        val structured = response.structuredJson as JsonObject
        assertEquals("Ada", structured.string("name"))
        assertEquals("36", structured.string("age"))
        assertEquals("""{"name": "Ada", "age": 36}""", response.content)
        assertNull(response.metadata)
    }

    private fun warningServer(vararg warnings: String): OmlxTestServer = OmlxTestServer {
        respond(
            OmlxFixtures.CHAT_JSON_SCHEMA,
            HttpStatusCode.OK,
            headers {
                append(HttpHeaders.ContentType, "application/json")
                warnings.forEach { append(HttpHeaders.Warning, it) }
            },
        )
    }

    @Test
    fun warningHeadersOnAStructuredRequestAreJoinedIntoMetadata() = runTest {
        val response = warningServer("199 omlx \"grammar unavailable\"", "199 omlx \"prompt fallback\"").open()
            .completeJsonResponse(model, messages, schema)

        assertEquals(
            buildJsonObject { put("response_format_warning", "199 omlx \"grammar unavailable\", 199 omlx \"prompt fallback\"") },
            response.metadata,
        )
        assertEquals("Ada", (response.structuredJson as JsonObject).string("name"))
    }

    @Test
    fun warningHeaderIsRecordedForAJsonResponseFormatWithOrWithoutSchema() = runTest {
        listOf(ResponseFormat.Json(), ResponseFormat.Json(schema)).forEach { format ->
            val response = warningServer("199 omlx \"prompt fallback\"").open()
                .complete(model, messages, config = CompletionConfig(responseFormat = format))

            assertEquals("199 omlx \"prompt fallback\"", response.metadata?.string("response_format_warning"), "for $format")
            gateway?.close()
        }
    }

    @Test
    fun warningHeaderIsIgnoredForTextOrAbsentFormats() = runTest {
        listOf(ResponseFormat.Text, null).forEach { format ->
            val response = warningServer("199 omlx \"prompt fallback\"").open()
                .complete(model, messages, config = CompletionConfig(responseFormat = format))

            assertNull(response.metadata, "for $format")
            gateway?.close()
        }
    }

    // 6. Truncation during thinking

    @Test
    fun lengthResponseKeepsPartialReasoningInContentUnchanged() = runTest {
        val response = OmlxTestServer.answering(OmlxFixtures.CHAT_LENGTH).open()
            .complete(model, messages, config = CompletionConfig(maxTokens = 5))

        assertEquals("length", response.finishReason)
        assertEquals("We need to respond to", response.content)
        assertNull(response.thinking)
    }

    // 9. Models

    @Test
    fun availableModelsReturnsTheIds() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.MODELS)

        assertEquals(listOf(model), server.open().availableModels())
        assertEquals(HttpMethod.Get, server.requests.single().method)
    }

    @Test
    fun availableModelsAreSorted() = runTest {
        val server = OmlxTestServer.answering("""{"object":"list","data":[{"id":"zeta"},{"id":"alpha"},{"id":"mid"}]}""")

        assertEquals(listOf("alpha", "mid", "zeta"), server.open().availableModels())
    }

    @Test
    fun loadModelPostsToTheLoadPath() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.MODEL_LOAD)

        server.open().loadModel(model)

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://localhost:8000/v1/models/Qwen3.8-27B-MLX-8bit/load", request.url.toString())
    }

    @Test
    fun modelIdIsPercentEncodedAsOnePathSegment() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.MODEL_LOAD)

        server.open().loadModel("mlx-community/Qwen 3")

        assertEquals("/v1/models/mlx-community%2FQwen%203/load", server.requests.single().url.encodedPath)
    }

    @Test
    fun unloadModelPostsToTheUnloadPath() = runTest {
        val server = OmlxTestServer.answering(OmlxFixtures.MODEL_UNLOAD)

        server.open().unloadModel(model)

        val request = server.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://localhost:8000/v1/models/Qwen3.8-27B-MLX-8bit/unload", request.url.toString())
    }

    @Test
    fun unloadOfAModelThatIsNotLoadedIsAProviderErrorWithStatusAndBody() = runTest {
        val gateway = OmlxTestServer.answering(OmlxFixtures.ERROR_MODEL_NOT_LOADED, HttpStatusCode.BadRequest).open()

        val error = assertFailsWith<LlmGatewayException> { gateway.unloadModel(model) }

        assertTrue("400" in error.message.orEmpty(), error.message)
        assertTrue(OmlxFixtures.ERROR_MODEL_NOT_LOADED in error.message.orEmpty(), error.message)
    }

    // 6 (contract section). Errors

    @Test
    fun unknownModelIsAProviderErrorWithStatusAndBody() = runTest {
        val gateway = OmlxTestServer.answering(OmlxFixtures.ERROR_MODEL_NOT_FOUND, HttpStatusCode.NotFound).open()

        val error = assertFailsWith<LlmGatewayException> { gateway.complete("nope", messages) }

        assertTrue("404" in error.message.orEmpty(), error.message)
        assertTrue(OmlxFixtures.ERROR_MODEL_NOT_FOUND in error.message.orEmpty(), error.message)
    }

    @Test
    fun wrongApiKeyIsAProviderErrorWithStatusAndBody() = runTest {
        val body = """{"error":{"message":"Invalid API key","type":"authentication_error","param":null,"code":null}}"""
        val gateway = OmlxTestServer.answering(body, HttpStatusCode.Unauthorized).open(apiKey = "wrong")

        val error = assertFailsWith<LlmGatewayException> { gateway.availableModels() }

        assertTrue("401" in error.message.orEmpty(), error.message)
        assertTrue(body in error.message.orEmpty(), error.message)
    }

    // 10. Embeddings

    @Test
    fun embedSendsOneRequestWithTheWholeTextAndReturnsTheFirstEmbedding() = runTest {
        val server = OmlxTestServer.answering("""{"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.25,-0.5,1.0]}]}""")

        val embedding = server.open().embed("bge-m3-mlx", "a long document")

        val request = server.requests.single()
        assertEquals("http://localhost:8000/v1/embeddings", request.url.toString())
        assertEquals(
            buildJsonObject {
                put("model", "bge-m3-mlx")
                put("input", "a long document")
            },
            server.body,
        )
        assertContentEquals(floatArrayOf(0.25f, -0.5f, 1.0f), embedding)
    }

    @Test
    fun embedBatchSendsOneRequestPerText() = runTest {
        val server = OmlxTestServer.answering("""{"data":[{"embedding":[1.0]}]}""")

        val embeddings = server.open().embedBatch("bge-m3-mlx", listOf("one", "two"))

        assertEquals(2, embeddings.size)
        assertEquals(listOf("one", "two"), server.requests.map { server.bodyOf(it).string("input") })
    }

    @Test
    fun missingEmbeddingModelIsRejectedBeforeAnyRequest() = runTest {
        val server = OmlxTestServer.answering("""{"data":[{"embedding":[1.0]}]}""")
        val gateway = server.open()

        assertFailsWith<IllegalArgumentException> { gateway.embed("", "text") }
        assertFailsWith<IllegalArgumentException> { gateway.embed("  ", "text") }
        assertFailsWith<IllegalArgumentException> { gateway.embedBatch("", listOf("text")) }
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun chatModelForEmbeddingsIsAProviderError() = runTest {
        val gateway = OmlxTestServer.answering(OmlxFixtures.ERROR_NOT_EMBEDDING_MODEL, HttpStatusCode.BadRequest).open()

        val error = assertFailsWith<LlmGatewayException> { gateway.embed(model, "text") }

        assertTrue("400" in error.message.orEmpty(), error.message)
        assertTrue(OmlxFixtures.ERROR_NOT_EMBEDDING_MODEL in error.message.orEmpty(), error.message)
    }

    @Test
    fun keepAliveFrameIsADataLineWhoseModelIsExactlyKeepalive() {
        val json = Json
        val keepAlive = OmlxFixtures.STREAM_THINKING_SSE.lineSequence().first()

        assertTrue(isKeepAliveFrame(keepAlive, json))
        assertFalse(isKeepAliveFrame(keepAlive.replace("\"keepalive\"", "\"keepalive-7b\""), json))
        assertFalse(isKeepAliveFrame(": keep-alive", json))
        assertFalse(isKeepAliveFrame(OmlxFixtures.STREAM_THINKING_SSE.lines()[2], json))
        assertFalse(isKeepAliveFrame("data: keepalive", json))
    }
}
