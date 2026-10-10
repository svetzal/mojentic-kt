package com.mojentic.llm.recovery

import com.mojentic.llm.CompletionEvidence
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.fromHttpToGmtDate
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException

/**
 * Dedicated completion client. Default JVM/Android engines disable retries and redirects.
 * Supplied engines require caller configuration; Native conformance awaits Apple validation.
 * Caller must close the client.
 */
public expect fun recoveryHttpClient(engine: HttpClientEngine? = null): HttpClient

/**
 * Dedicated completion client with a provider's connect and socket timeout in milliseconds.
 * Null keeps the engine connect default and allows an unbounded wait for generation data.
 * There is no total request timeout. Supplied engines still require caller retry configuration.
 */
public expect fun recoveryHttpClient(engine: HttpClientEngine?, timeoutMillis: Long?): HttpClient

/** Encoded request boundary shared only by opt-in ordinary and structured local-provider completions. */
public class RecoveryHttp(
    private val client: HttpClient,
    private val provider: String,
    private val url: String,
    private val apiKey: String? = null,
) {
    /** Decode is inside the recovery boundary so malformed replies have bounded, private failure evidence. */
    public suspend fun <T> execute(
        payload: String,
        operation: String,
        policy: RecoveryPolicy,
        decode: (String, Map<String, List<String>>) -> T,
    ): T = executeInternal(payload, operation, policy, decode, null)

    /** Incremental streaming boundary; the consumer validates terminal evidence before returning. */
    public suspend fun executeStream(
        payload: String,
        operation: String,
        policy: RecoveryPolicy,
        consume: suspend (RecoveryStream) -> Unit,
    ): Unit = executeInternal(payload, operation, policy, { _, _ -> }, consume)

    private suspend fun <T> executeInternal(
        payload: String,
        operation: String,
        policy: RecoveryPolicy,
        decode: (String, Map<String, List<String>>) -> T,
        consume: (suspend (RecoveryStream) -> Unit)?,
    ): T {
        val failures = mutableListOf<RecoveryFailure>()
        val logicalId = RecoveryIdentity.newId()
        var started: Long? = null
        var identity = RecoveryIdentity(logicalId, RecoveryIdentity.newId(), 1)
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val attempt = ResponseEvidence(payload, apiKey)
                policy.observer(RecoveryEvent(RecoveryStage.STARTED, identity, failures = failures.toList()))
                val received = perform(payload, identity, policy, attempt, decode, consume) {
                    started?.let { checkBudget(policy, it, 0, identity, failures) }
                }
                val result = observeSuccess(received, policy, identity, attempt, failures)
                if (result.isSuccess) return result.getOrThrow()
                val cause = result.exceptionOrNull()
                rethrowRecoveryFailure(cause)
                val failure = attempt.failure(provider, operation, identity, policy, cause)
                failures += failure
                if (started == null) started = attempt.failedAt ?: policy.monotonicClock()
                policy.observer(RecoveryEvent(RecoveryStage.FAILED, identity, failure = failure, failures = failures.toList()))
                if (cause is CancellationException) throw cause
                currentCoroutineContext().ensureActive()
                val wait = retryDelay(policy, failure, requireNotNull(started), failures)
                if (policy.admission == null) terminal(policy, identity, failures, RecoveryReason.ADMISSION_REQUIRED)
                policy.observer(RecoveryEvent(RecoveryStage.ADMISSION_PENDING, identity, failure = failure, failures = failures.toList()))
                val allowed = admit(policy, failure, requireNotNull(started), failures)
                currentCoroutineContext().ensureActive()
                if (!allowed) {
                    policy.observer(RecoveryEvent(RecoveryStage.REJECTED, identity, failure = failure, failures = failures.toList()))
                    terminal(policy, identity, failures, RecoveryReason.ADMISSION_REJECTED)
                }
                policy.observer(RecoveryEvent(RecoveryStage.ADMITTED, identity, failure = failure, failures = failures.toList()))
                checkBudget(policy, requireNotNull(started), wait, identity, failures)
                policy.observer(RecoveryEvent(RecoveryStage.SCHEDULED, identity, wait, failure, failures.toList()))
                policy.sleep(wait)
                currentCoroutineContext().ensureActive()
                checkBudget(policy, requireNotNull(started), 0, identity, failures)
                identity = RecoveryIdentity(logicalId, RecoveryIdentity.newId(), identity.attemptNumber + 1)
            }
        } catch (cause: Throwable) {
            failOperation(cause, policy, identity, failures)
        }
    }

    private suspend fun <T> observeSuccess(
        result: Result<T>,
        policy: RecoveryPolicy,
        identity: RecoveryIdentity,
        evidence: ResponseEvidence,
        failures: List<RecoveryFailure>,
    ): Result<T> {
        if (result.isFailure) return result
        return try {
            currentCoroutineContext().ensureActive()
            policy.observer(
                RecoveryEvent(
                    RecoveryStage.SUCCEEDED,
                    identity,
                    failures = failures.toList(),
                    wireAttempts = identity.attemptNumber,
                    progress = evidence.progress(),
                ),
            )
            currentCoroutineContext().ensureActive()
            result
        } catch (cause: Throwable) {
            evidence.observation = true
            evidence.boundaryCause = cause
            evidence.failedAt = policy.monotonicClock()
            Result.failure(cause)
        }
    }

    private suspend fun failOperation(
        cause: Throwable,
        policy: RecoveryPolicy,
        identity: RecoveryIdentity,
        failures: List<RecoveryFailure>,
    ): Nothing {
        if (cause is CancellationException || !currentCoroutineContext().isActive) {
            // Observation cannot replace authoritative cancellation, even if the observer fails.
            runCatching {
                policy.observer(
                    RecoveryEvent(RecoveryStage.CANCELLED, identity, failure = failures.lastOrNull(), failures = failures.toList()),
                )
            }
            val cancelled = cause as? CancellationException ?: CancellationException("Completion cancelled")
            throw RecoveryCancellationException(failures.toList(), cancelled)
        }
        rethrowRecoveryFailure(cause)
        throw RecoveryException(failures.toList(), RecoveryReason.POLICY_FAILED, cause)
    }

    private suspend fun <T> perform(
        payload: String,
        identity: RecoveryIdentity,
        policy: RecoveryPolicy,
        evidence: ResponseEvidence,
        decode: (String, Map<String, List<String>>) -> T,
        consume: (suspend (RecoveryStream) -> Unit)?,
        beforeSend: () -> Unit,
    ): Result<T> = try {
        evidence.capture = true
        policy.capture?.invoke(RecoveryWire(identity, payload.encodeToByteArray(), null, emptyMap(), null))
        evidence.capture = false
        currentCoroutineContext().ensureActive()
        beforeSend()
        evidence.sent = true
        val result = client.preparePost(url) {
            contentType(ContentType.Application.Json)
            apiKey?.let { header("Authorization", "Bearer $it") }
            setBody(payload)
        }.execute { response ->
            if (consume != null && response.status.value in SUCCESS_RANGE) {
                evidence.streaming = true
                evidence.status = response.status.value
                evidence.headers = response.headers.entries().associate { it.key to it.value.toList() }
                consume(RecoveryStream(response.bodyAsChannel(), evidence, identity, policy, payload))
                if (!evidence.responseCaptured) evidence.captureResponse(policy, identity, payload, complete = true)
                decode("", evidence.headers)
            } else {
                readResponse(response, evidence, identity, policy, payload)
                check(response.status.value in SUCCESS_RANGE) { "HTTP failure" }
                evidence.decoding = true
                decode(evidence.bytes.toByteArray().decodeToString(), evidence.headers)
            }
        }
        currentCoroutineContext().ensureActive()
        Result.success(result)
    } catch (cause: Throwable) {
        evidence.failedAt = evidence.failedAt ?: policy.monotonicClock()
        Result.failure(captureFailure(cause, evidence, policy, identity, payload))
    }

    private fun captureFailure(
        cause: Throwable,
        evidence: ResponseEvidence,
        policy: RecoveryPolicy,
        identity: RecoveryIdentity,
        payload: String,
    ): Throwable {
        if (cause is RecoveryException) return cause
        evidence.boundaryCause = cause
        return if (!evidence.capture && !evidence.responseCaptured) {
            try {
                evidence.captureResponse(policy, identity, payload, complete = true)
                cause
            } catch (captureCause: Throwable) {
                if (cause is CancellationException) cause else captureCause
            }
        } else {
            cause
        }
    }

    private suspend fun readResponse(
        response: HttpResponse,
        evidence: ResponseEvidence,
        identity: RecoveryIdentity,
        policy: RecoveryPolicy,
        payload: String,
    ) {
        evidence.status = response.status.value
        if (response.status.value !in SUCCESS_RANGE) evidence.failedAt = policy.monotonicClock()
        evidence.headers = response.headers.entries().associate { it.key to it.value.toList() }
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = channel.readAvailable(buffer)
            if (count < 0) break
            if (count > 0) {
                evidence.bytes.addAll(buffer.copyOf(count).toList())
                evidence.captureResponse(policy, identity, payload, complete = false)
            }
        }
        evidence.captureResponse(policy, identity, payload, complete = true)
    }

    private fun rethrowRecoveryFailure(cause: Throwable?) {
        if (cause is RecoveryException) throw cause
    }

    private fun retryDelay(
        policy: RecoveryPolicy,
        failure: RecoveryFailure,
        started: Long,
        failures: List<RecoveryFailure>,
    ): Long {
        val count = failures.size
        val identity = failure.identity
        if (failure.reason !in RETRY_REASONS) terminal(policy, identity, failures, failure.reason)
        if (count >= policy.maxAttempts) terminal(policy, identity, failures, RecoveryReason.EXHAUSTED)
        var ceiling = policy.baseDelayMillis
        repeat((count - 1).coerceAtMost(MAX_EXPONENT)) {
            ceiling = if (ceiling > policy.delayCeilingMillis / 2) policy.delayCeilingMillis else ceiling * 2
        }
        val jitter = policy.jitter(ceiling)
        require(jitter in 0..ceiling) { "Jitter must be inside the ceiling" }
        val minimum = (failure.retryAfter as? RecoveryRetryAfter.Delay)?.millis ?: 0
        if (minimum > policy.delayCeilingMillis) terminal(policy, identity, failures, RecoveryReason.PROVIDER_MINIMUM)
        val wait = maxOf(jitter, minimum)
        checkBudget(policy, requireNotNull(started), wait, identity, failures)
        return wait
    }

    private suspend fun admit(
        policy: RecoveryPolicy,
        failure: RecoveryFailure,
        started: Long,
        failures: List<RecoveryFailure>,
    ): Boolean {
        checkBudget(policy, started, 0, failure.identity, failures)
        val elapsed = remainingMillis(policy.monotonicClock(), started)
        val remaining = minOf(
            policy.budgetMillis?.minus(elapsed) ?: Long.MAX_VALUE,
            policy.deadlineEpochMillis?.let { remainingMillis(it, policy.clock()) } ?: Long.MAX_VALUE,
        )
        val hook = requireNotNull(policy.admission)
        return if (remaining == Long.MAX_VALUE) {
            hook(failure)
        } else {
            withTimeoutOrNull(remaining) { hook(failure) }
                ?: terminal(policy, failure.identity, failures, RecoveryReason.BUDGET)
        }
    }

    private fun checkBudget(
        policy: RecoveryPolicy,
        started: Long,
        wait: Long,
        identity: RecoveryIdentity,
        failures: List<RecoveryFailure>,
    ) {
        val now = policy.clock()
        val elapsed = remainingMillis(policy.monotonicClock(), started)
        val budget = policy.budgetMillis
        val deadline = policy.deadlineEpochMillis
        val budgetExpired = budget != null && (elapsed >= budget || wait > budget - elapsed)
        val deadlineExpired = deadline != null && (now >= deadline || wait > remainingMillis(deadline, now))
        if (budgetExpired || deadlineExpired) {
            terminal(policy, identity, failures, RecoveryReason.BUDGET)
        }
    }

    private fun terminal(
        policy: RecoveryPolicy,
        identity: RecoveryIdentity,
        failures: List<RecoveryFailure>,
        reason: RecoveryReason,
    ): Nothing {
        val last = failures.lastOrNull()
        val streaming = last?.operation == "stream" || last?.operation == "streamEvents"
        val stage = if (streaming &&
            (last.progress.replayUnsafe || last.progress.semanticDelivered)
        ) {
            RecoveryStage.INTERRUPTED
        } else {
            RecoveryStage.EXHAUSTED
        }
        policy.observer(RecoveryEvent(stage, identity, failure = failures.lastOrNull(), failures = failures.toList()))
        throw RecoveryException(failures.toList(), reason)
    }

    private companion object {
        const val BUFFER_SIZE = 8192
        const val MAX_EXPONENT = 63
        val SUCCESS_RANGE = 200..299
        val RETRY_REASONS = setOf(RecoveryReason.HTTP_TRANSIENT, RecoveryReason.TRANSPORT)
    }
}

internal class ResponseEvidence(private val payload: String = "", private val apiKey: String? = null) {
    var streaming = false
    var delivered = false
    var deliveredCounts = RecoverySemanticProgress()
    var streamProgress = RecoveryProgress(false, 0, false)

    fun progress(): RecoveryProgress = if (streaming) {
        streamProgress.copy(
            headersReceived = status != null,
            rawBytes = bytes.size.toLong(),
            semanticDelivered = delivered || deliveredCounts.any(),
            replayUnsafe = streamProgress.replayUnsafe || streamProgress.observed.any() ||
                streamProgress.completedToolCallsObserved > 0 || delivered || deliveredCounts.any(),
            delivered = deliveredCounts,
        )
    } else {
        observedProgress(status, bytes.toByteArray())
    }

    var sent = false
    var observation = false
    var failedAt: Long? = null
    var status: Int? = null
    var headers: Map<String, List<String>> = emptyMap()
    val bytes = mutableListOf<Byte>()
    var capture = false
    var decoding = false
    var responseCaptured = false
    var boundaryCause: Throwable? = null
    var completion: CompletionEvidence? = null

    fun wire(identity: RecoveryIdentity, payload: String, complete: Boolean = false): RecoveryWire =
        RecoveryWire(
            identity,
            payload.encodeToByteArray(),
            bytes.toByteArray(),
            headers,
            status,
            complete,
            progress(),
        )

    fun failure(
        provider: String,
        operation: String,
        identity: RecoveryIdentity,
        policy: RecoveryPolicy,
        cause: Throwable?,
    ): RecoveryFailure {
        val details = RecoveryDetails(
            category(cause),
            status,
            progress(),
            retryAfter(policy.clock()),
            reason(policy, cause),
            sent,
            RecoveryMetadata.code(bytes.toByteArray(), payload, apiKey),
            RecoveryMetadata.requestId(headers, payload, apiKey),
        )
        return RecoveryFailure(
            provider,
            operation,
            identity,
            details,
            SensitiveRecoveryEvidence(cause, bytes.toByteArray(), headers, boundaryCause, completion),
        )
    }

    fun captureResponse(policy: RecoveryPolicy, identity: RecoveryIdentity, payload: String, complete: Boolean) {
        capture = true
        policy.capture?.invoke(wire(identity, payload, complete))
        responseCaptured = complete
        capture = false
    }

    private fun reason(policy: RecoveryPolicy, cause: Throwable?): RecoveryReason = when {
        cause is CancellationException -> RecoveryReason.CANCELLATION
        capture -> RecoveryReason.CAPTURE
        observation -> RecoveryReason.POLICY_FAILED
        partialStreaming() -> RecoveryReason.PARTIAL_SUCCESS
        cause is RecoveryStreamException -> RecoveryReason.PROTOCOL
        decoding -> RecoveryReason.PROTOCOL
        partialOrdinary() -> RecoveryReason.PARTIAL_SUCCESS
        status != null && status !in SUCCESS_RANGE -> httpReason(policy)
        policy.retryTransport && isTransport(cause) -> RecoveryReason.TRANSPORT
        else -> RecoveryReason.HTTP_PERMANENT
    }

    private fun partialStreaming(): Boolean = streaming && (progress().replayUnsafe || progress().semanticDelivered)

    private fun partialOrdinary(): Boolean = !streaming && status in SUCCESS_RANGE && bytes.isNotEmpty()

    private fun httpReason(policy: RecoveryPolicy): RecoveryReason =
        if (status in policy.retryableStatuses && status !in PERMANENT) RecoveryReason.HTTP_TRANSIENT else RecoveryReason.HTTP_PERMANENT

    private fun isTransport(cause: Throwable?): Boolean = cause is IOException || cause is HttpRequestTimeoutException

    private fun category(cause: Throwable?): RecoveryCategory = when {
        cause is CancellationException -> RecoveryCategory.CANCELLATION
        decoding -> RecoveryCategory.PROTOCOL
        status != null && (!streaming || status !in SUCCESS_RANGE) -> RecoveryCategory.HTTP
        streaming && cause is RecoveryStreamException -> RecoveryCategory.PROTOCOL
        cause is HttpRequestTimeoutException -> RecoveryCategory.CLIENT_TIMEOUT
        else -> RecoveryCategory.TRANSPORT
    }

    private fun retryAfter(now: Long): RecoveryRetryAfter {
        val value = headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }?.value?.firstOrNull()
            ?: return RecoveryRetryAfter.Absent
        val normalized = value.trim()
        if (normalized.isNotEmpty() && normalized.all { it in '0'..'9' }) {
            val seconds = normalized.toLongOrNull()
            val millis = if (seconds == null || seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
            return RecoveryRetryAfter.Delay(millis)
        }
        return try {
            val date = normalized.fromHttpToGmtDate().timestamp
            RecoveryRetryAfter.Delay(remainingMillis(date, now))
        } catch (_: Exception) {
            RecoveryRetryAfter.Invalid
        }
    }

    private companion object {
        val PERMANENT = setOf(400, 401, 403)
        val SUCCESS_RANGE = 200..299
    }
}

private fun remainingMillis(later: Long, now: Long): Long = when {
    later <= now -> 0
    now < 0 && later > Long.MAX_VALUE + now -> Long.MAX_VALUE
    else -> later - now
}

/** Numeric evidence independently vetoes replay, even without a semantic flag. */
private fun RecoverySemanticProgress.any(): Boolean =
    contentBytes > 0 || reasoningBytes > 0 || toolFragments > 0 || completedToolCalls > 0
