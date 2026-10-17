package com.mojentic.llm

import com.mojentic.errors.LlmGatewayException
import com.mojentic.llm.recovery.RecoveryException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Client measurements for one collected completion, shared across its admitted wire attempts.
 * Mutable timing and frame state is protected by a coroutine-aware mutex; evidence snapshots are immutable.
 */
public class StreamTiming(private val clock: () -> Long) {
    private val mutex = Mutex()
    private var started: Long? = null
    private var first: Long? = null
    private var kind: String? = null
    private var provider = CompletionEvidence()
    private val pendingBytes = mutableListOf<Byte>()
    internal suspend fun isDispatched(): Boolean = mutex.withLock { started != null }

    /** Starts only once, immediately before dispatch. */
    public suspend fun start(): Unit = mutex.withLock {
        if (started == null) started = clock()
        pendingBytes.clear()
    }

    /** Observe complete wire lines before recovery capture callbacks can interrupt parsing. */
    public suspend fun observeBytes(bytes: ByteArray): Unit = mutex.withLock {
        for (byte in bytes) {
            if (byte == '\n'.code.toByte()) {
                observeFrame(pendingBytes.toByteArray().decodeToString().removeSuffix("\r"))
                pendingBytes.clear()
            } else {
                pendingBytes += byte
            }
        }
    }

    /** Observes a complete SSE data line or NDJSON frame without changing its bytes or delivery order. */
    public suspend fun observe(line: String): Unit = mutex.withLock { observeFrame(line) }

    private fun observeFrame(line: String) {
        val payload = if (line.startsWith("data:")) line.removePrefix("data:").trim() else line
        val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return
        if (root["model"].text() == "keepalive") return
        observeToken(root)
        observeProvider(root)
    }

    private fun observeToken(root: JsonObject) {
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = root["message"] as? JsonObject
        val delta = choice?.get("delta") as? JsonObject ?: root["delta"] as? JsonObject
        val block = root["content_block"] as? JsonObject
        val sources = listOfNotNull(delta, message, block)
        if (first == null) {
            val observed = when {
                sources.any { it.nonempty("thinking", "reasoning_content") } -> "thinking"
                sources.any { it.nonempty("content", "text") } -> "content"
                sources.any { it.nonempty("partial_json") || it.hasArguments(allowObjects = it === message) } -> "tool_call"
                else -> null
            }
            if (observed != null && started != null) {
                first = clock()
                kind = observed
            }
        }
    }

    private fun observeProvider(root: JsonObject) {
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = root["message"] as? JsonObject
        val delta = choice?.get("delta") as? JsonObject ?: root["delta"] as? JsonObject
        val durations = root.filterKeys { it in DURATION_KEYS }
        val usage = provider.usage.orEmpty() + (message?.get("usage") as? JsonObject).orEmpty() +
            (root["usage"] as? JsonObject).orEmpty() + root.filterKeys { it in USAGE_KEYS }
        provider = provider.copy(
            finishReason = choice?.get("finish_reason").text() ?: delta?.get("stop_reason").text()
                ?: root["done_reason"].text() ?: provider.finishReason,
            usage = JsonObject(usage).takeIf { it.isNotEmpty() },
            providerModel = root["model"].text() ?: message?.get("model").text() ?: provider.providerModel,
            metadata = JsonObject(provider.metadata.orEmpty() + durations).takeIf { it.isNotEmpty() },
        )
    }

    /** Merges client milliseconds into provider evidence; provider durations keep their original units. */
    public suspend fun evidence(existing: CompletionEvidence? = null): CompletionEvidence = mutex.withLock {
        val base = existing ?: provider
        val metadata = provider.metadata.orEmpty() + base.metadata.orEmpty() + buildMap {
            started?.let { origin ->
                put("request_duration_ms", JsonPrimitive((clock() - origin).coerceAtLeast(0)))
                first?.let {
                    put("time_to_first_token_ms", JsonPrimitive((it - origin).coerceAtLeast(0)))
                    put("first_token_kind", JsonPrimitive(kind))
                }
            }
        }
        base.copy(
            finishReason = base.finishReason ?: provider.finishReason,
            usage = base.usage ?: provider.usage,
            providerModel = base.providerModel ?: provider.providerModel,
            metadata = JsonObject(metadata).takeIf { it.isNotEmpty() },
        )
    }

    private companion object {
        private val USAGE_KEYS = setOf("prompt_eval_count", "eval_count")
        private val DURATION_KEYS = setOf("total_duration", "load_duration", "prompt_eval_duration", "eval_duration")
    }
}

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun JsonObject.nonempty(vararg keys: String): Boolean = keys.any { this[it].text()?.isNotEmpty() == true }
private fun JsonObject.hasArguments(allowObjects: Boolean): Boolean = (this["tool_calls"] as? JsonArray).orEmpty().any { call ->
    val function = (call as? JsonObject)?.get("function") as? JsonObject
    val arguments = function?.get("arguments")
    (allowObjects && arguments is JsonObject) || arguments.text()?.isNotEmpty() == true
}

/** Dispatch hook shared by ordinary production streaming adapters. */
public suspend fun <T> HttpStatement.executeTimed(timing: StreamTiming, block: suspend (HttpResponse) -> T): T {
    timing.start()
    return execute(block)
}

/** Reads and measures a frame before parsing or downstream delivery. */
public suspend fun ByteReadChannel.readTimedLine(timing: StreamTiming): String? = readLine()?.also {
    timing.observe(it)
}

private fun CompletionConfig.newStreamTiming(): StreamTiming {
    val clock = recovery?.monotonicClock ?: streamTimeSource.markNow().let { origin ->
        { origin.elapsedNow().inWholeMilliseconds }
    }
    return StreamTiming(clock)
}

/** Adds terminal evidence to the legacy streaming API while retaining typed recovery failures. */
public fun timedGatewayStream(
    config: CompletionConfig,
    source: (StreamTiming) -> Flow<GatewayStreamEvent>,
): Flow<GatewayStreamEvent> = flow {
    val timing = config.newStreamTiming()
    val events = source(timing)
    // Recovery owns collector cancellation and retains its original typed failure evidence.
    val guarded = if (config.recovery == null) {
        events.catch { cause ->
            when {
                cause is CancellationException || cause is RecoveryException || !timing.isDispatched() -> throw cause
                else -> throw LlmGatewayException(cause.message ?: "Stream failed", cause, timing.evidence())
            }
        }
    } else {
        events
    }
    guarded.collect { emit(it) }
    emit(GatewayStreamEvent.Completed(timing.evidence()))
}

/** Adds client timing to terminal success and failure without changing content ordering. */
public fun timedCompletionStream(
    config: CompletionConfig,
    source: (StreamTiming) -> Flow<CompletionStreamEvent>,
): Flow<CompletionStreamEvent> = flow {
    val timing = config.newStreamTiming()
    source(timing).collect { event ->
        emit(
            when (event) {
                is CompletionStreamEvent.Content -> event

                is CompletionStreamEvent.Completed -> event.copy(evidence = timing.evidence(event.evidence))

                is CompletionStreamEvent.Error -> {
                    val evidence = when (val reason = event.reason) {
                        is StreamErrorReason.IncompleteCompletion -> reason.evidence

                        is StreamErrorReason.IncompleteStream -> reason.evidence

                        is StreamErrorReason.RequestFailed -> (reason.cause as? RecoveryException)
                            ?.failures?.lastOrNull()?.inspectCompletionEvidence()

                        else -> null
                    }
                    event.copy(evidence = timing.evidence(evidence))
                }
            },
        )
    }
}
