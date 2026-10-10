package com.mojentic.llm.recovery

import com.mojentic.llm.CompletionEvidence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/** Completion recovery is opt-in; admission owns permission to resend local inference. */
public class RecoveryPolicy(
    public val maxAttempts: Int = 1,
    public val baseDelayMillis: Long = 100,
    public val delayCeilingMillis: Long = 30_000,
    public val budgetMillis: Long? = null,
    public val deadlineEpochMillis: Long? = null,
    public val retryableStatuses: Set<Int> = setOf(TOO_MANY_REQUESTS, SERVER_ERROR, BAD_GATEWAY, UNAVAILABLE, GATEWAY_TIMEOUT),
    public val retryTransport: Boolean = true,
    public val admission: (suspend (RecoveryFailure) -> Boolean)? = null,
    public val observer: (RecoveryEvent) -> Unit = {},
    public val capture: ((RecoveryWire) -> Unit)? = null,
    public val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    public val monotonicClock: () -> Long = monotonicMillisClock(),
    public val sleep: suspend (Long) -> Unit = { delay(it) },
    public val jitter: (Long) -> Long = { ceiling -> Random.nextLong(ceiling + 1) },
) {
    init {
        require(maxAttempts > 0)
        require(baseDelayMillis >= 0 && delayCeilingMillis >= baseDelayMillis && delayCeilingMillis < Long.MAX_VALUE)
        require(budgetMillis == null || budgetMillis >= 0)
    }

    override fun toString(): String = "RecoveryPolicy(maxAttempts=$maxAttempts)"
}

/** One-based actual attempt identity; IDs do not imply provider idempotency. */
@Serializable
public data class RecoveryIdentity(val logicalId: String, val attemptId: String, val attemptNumber: Int) {
    public companion object {
        /** Creates a fresh logical ID or attempt ID. */
        public fun newId(): String = Uuid.random().toString()
    }
}

/** Safe reason codes; no provider messages or arbitrary metadata are included. */
@Serializable
public enum class RecoveryReason {
    HTTP_TRANSIENT,
    HTTP_PERMANENT,
    TRANSPORT,
    PARTIAL_SUCCESS,
    PROTOCOL,
    CAPTURE,
    EXHAUSTED,
    ADMISSION_REJECTED,
    ADMISSION_REQUIRED,
    BUDGET,
    PROVIDER_MINIMUM,
    CANCELLATION,
    POLICY_FAILED,
}

/** Category of the original failed request. */
@Serializable
public enum class RecoveryCategory { HTTP, TRANSPORT, PROTOCOL, CLIENT_TIMEOUT, CANCELLATION }

/** Progress observed at the response boundary, before capture or decoding. */
@Serializable
public data class RecoveryProgress(
    val headersReceived: Boolean,
    val rawBytes: Long,
    val semanticObserved: Boolean,
    val semanticDelivered: Boolean = false,
    val contentObserved: Boolean = false,
    val reasoningObserved: Boolean = false,
    val toolFragmentsObserved: Boolean = false,
    val completedToolCallsObserved: Int = 0,
    val replayUnsafe: Boolean = semanticObserved,
    val observed: RecoverySemanticProgress = RecoverySemanticProgress(),
    val delivered: RecoverySemanticProgress = RecoverySemanticProgress(),
) {
    /** Retains the pre-streaming constructor and its default-argument bridge. */
    public constructor(
        headersReceived: Boolean,
        rawBytes: Long,
        semanticObserved: Boolean,
        semanticDelivered: Boolean = false,
        contentObserved: Boolean = false,
        reasoningObserved: Boolean = false,
        toolFragmentsObserved: Boolean = false,
        completedToolCallsObserved: Int = 0,
        replayUnsafe: Boolean = semanticObserved,
    ) : this(
        headersReceived,
        rawBytes,
        semanticObserved,
        semanticDelivered,
        contentObserved,
        reasoningObserved,
        toolFragmentsObserved,
        completedToolCallsObserved,
        replayUnsafe,
        RecoverySemanticProgress(),
        RecoverySemanticProgress(),
    )

    /** Retains the pre-streaming copy signature while preserving streaming evidence. */
    public fun copy(
        headersReceived: Boolean = this.headersReceived,
        rawBytes: Long = this.rawBytes,
        semanticObserved: Boolean = this.semanticObserved,
        semanticDelivered: Boolean = this.semanticDelivered,
        contentObserved: Boolean = this.contentObserved,
        reasoningObserved: Boolean = this.reasoningObserved,
        toolFragmentsObserved: Boolean = this.toolFragmentsObserved,
        completedToolCallsObserved: Int = this.completedToolCallsObserved,
        replayUnsafe: Boolean = this.replayUnsafe,
    ): RecoveryProgress = RecoveryProgress(
        headersReceived,
        rawBytes,
        semanticObserved,
        semanticDelivered,
        contentObserved,
        reasoningObserved,
        toolFragmentsObserved,
        completedToolCallsObserved,
        replayUnsafe,
        observed,
        delivered,
    )
}

/** Explicit Retry-After parse result; invalid values never replace policy backoff. */
@Serializable
public sealed interface RecoveryRetryAfter {
    @Serializable
    public data object Absent : RecoveryRetryAfter

    @Serializable
    public data object Invalid : RecoveryRetryAfter

    @Serializable
    public data class Delay(public val millis: Long) : RecoveryRetryAfter
}

/** Safe failure summary. Raw evidence is accessible only through explicit inspection. */
public class RecoveryFailure internal constructor(
    public val provider: String,
    public val operation: String,
    public val identity: RecoveryIdentity,
    private val details: RecoveryDetails,
    private val evidence: SensitiveRecoveryEvidence,
) {
    public val category: RecoveryCategory get() = details.category
    public val status: Int? get() = details.status
    public val progress: RecoveryProgress get() = details.progress
    public val retryAfter: RecoveryRetryAfter get() = details.retryAfter
    public val reason: RecoveryReason get() = details.reason
    public val wireSent: Boolean get() = details.wireSent

    /** Arbitrary provider strings are withheld from safe summaries; inspect raw headers/body explicitly. */
    public val providerCode: String? = null
    public val providerRequestId: String? = null

    /** Safe serializable snapshot without raw bytes, headers or Throwable objects. */
    public fun summary(): RecoveryFailureSummary = RecoveryFailureSummary(
        provider, operation, identity, category, status, progress, retryAfter, reason, acceptance, phase, eligible, wireSent,
    )

    /** Local inference acceptance cannot be proven from socket closure or HTTP status. */
    public val acceptance: RecoveryAcceptance = RecoveryAcceptance.UNKNOWN

    /** The next prospective attempt; pending admission never consumes this number. */
    public val nextAttemptNumber: Int = identity.attemptNumber + 1

    /** Eligibility is separate from caller admission. */
    public val eligible: Boolean = reason == RecoveryReason.HTTP_TRANSIENT || reason == RecoveryReason.TRANSPORT

    /** Evidence distinguishes response receipt and decoding, but not connecting from sending. */
    public val phase: RecoveryPhase = when {
        category == RecoveryCategory.PROTOCOL -> RecoveryPhase.DECODING
        progress.headersReceived -> RecoveryPhase.RECEIVING
        else -> RecoveryPhase.UNKNOWN
    }

    /** Original request failure when a capture hook also failed; may contain secrets. */
    public fun inspectBoundaryCause(): Throwable? = evidence.boundaryCause

    /** Validated completion telemetry retained on interruption; provider values require explicit inspection. */
    public fun inspectCompletionEvidence(): CompletionEvidence? = evidence.completion

    /** The original object, which may contain secrets. Never log implicitly. */
    public fun inspectCause(): Throwable? = evidence.originalCause

    /** Exact partial body; caller owns privacy and storage. */
    public fun inspectBytes(): ByteArray = evidence.receivedBytes.copyOf()

    /** Unfiltered headers; caller owns privacy and storage. */
    public fun inspectHeaders(): Map<String, List<String>> = evidence.receivedHeaders.mapValues { it.value.toList() }
    override fun toString(): String = "RecoveryFailure(provider=$provider, status=$status, reason=$reason, identity=$identity)"
}

/** Terminal bounded recovery report, with no unsafe Throwable cause chain. */
public class RecoveryException(
    public val failures: List<RecoveryFailure>,
    public val reason: RecoveryReason,
    private val auxiliaryCause: Throwable? = null,
) :
    RuntimeException("Completion recovery ended: $reason; attempts=${failures.count { it.wireSent }}") {
    public val wireAttempts: Int = failures.count { it.wireSent }

    /** Caller hook failure, if any. This object can contain secrets. */
    public fun inspectAuxiliaryCause(): Throwable? = auxiliaryCause
    public fun summary(): RecoveryReport = RecoveryReport(reason, failures.map { it.summary() }, wireAttempts)
}

/** Payload-free lifecycle record. */
public data class RecoveryEvent(
    val stage: RecoveryStage,
    val identity: RecoveryIdentity,
    val delayMillis: Long? = null,
    val failure: RecoveryFailure? = null,
    val failures: List<RecoveryFailure> = emptyList(),
    val wireAttempts: Int = failures.count { it.wireSent },
    val progress: RecoveryProgress? = failure?.progress,
    val frameIndex: Long? = null,
    val metrics: RecoveryStreamMetrics? = null,
) {
    /** Retains the pre-streaming constructor and its default-argument bridge. */
    public constructor(
        stage: RecoveryStage,
        identity: RecoveryIdentity,
        delayMillis: Long? = null,
        failure: RecoveryFailure? = null,
        failures: List<RecoveryFailure> = emptyList(),
        wireAttempts: Int = failures.count { it.wireSent },
        progress: RecoveryProgress? = failure?.progress,
    ) : this(stage, identity, delayMillis, failure, failures, wireAttempts, progress, null, null)

    /** Retains the pre-streaming copy signature while preserving streaming evidence. */
    public fun copy(
        stage: RecoveryStage = this.stage,
        identity: RecoveryIdentity = this.identity,
        delayMillis: Long? = this.delayMillis,
        failure: RecoveryFailure? = this.failure,
        failures: List<RecoveryFailure> = this.failures,
        wireAttempts: Int = this.wireAttempts,
        progress: RecoveryProgress? = this.progress,
    ): RecoveryEvent = RecoveryEvent(
        stage,
        identity,
        delayMillis,
        failure,
        failures,
        wireAttempts,
        progress,
        frameIndex,
        metrics,
    )

    /** Serializable lifecycle snapshot with no raw provider or wire evidence. */
    public fun summary(): RecoveryLifecycleSummary = RecoveryLifecycleSummary(
        stage,
        identity,
        delayMillis,
        failures.map { it.summary() },
        wireAttempts,
        progress,
        frameIndex,
        metrics,
    )
}

/** Admission pending and backoff do not count as wire attempts. */
@Serializable
public enum class RecoveryStage {
    STARTED,
    FAILED,
    ADMISSION_PENDING,
    ADMITTED,
    REJECTED,
    SCHEDULED,
    SUCCEEDED,
    EXHAUSTED,
    CANCELLED,
    PROGRESS,
    METRICS,
    INTERRUPTED,
}

/** Explicit sensitive capture. Headers exclude authorization on requests. No implicit formatting of payloads. */
public class RecoveryWire(
    public val identity: RecoveryIdentity,
    private val request: ByteArray,
    private val response: ByteArray?,
    private val headers: Map<String, List<String>>,
    public val status: Int?,
    public val complete: Boolean = false,
    public val progress: RecoveryProgress = RecoveryProgress(status != null, response?.size?.toLong() ?: 0, false),
) {
    public fun inspectRequest(): ByteArray = request.copyOf()
    public fun inspectResponse(): ByteArray? = response?.copyOf()
    public fun inspectHeaders(): Map<String, List<String>> = headers.mapValues { it.value.toList() }
    override fun toString(): String = "RecoveryWire(identity=$identity, status=$status)"
}

/** Provider execution cannot be inferred from a local socket closing. */
@Serializable
public enum class RecoveryAcceptance { YES, NO, UNKNOWN }

/** Only evidence-supported phases are reported. */
@Serializable
public enum class RecoveryPhase { UNKNOWN, RECEIVING, DECODING }

/** Cancellation remains a coroutine cancellation and carries safe completed-attempt history. */
public class RecoveryCancellationException(
    public val failures: List<RecoveryFailure>,
    private val originalCancellation: CancellationException,
) :
    CancellationException("Completion recovery cancelled; attempts=${failures.count { it.wireSent }}") {
    public val wireAttempts: Int = failures.count { it.wireSent }
    public fun inspectCancellationCause(): CancellationException = originalCancellation
}

private fun monotonicMillisClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}

internal data class RecoveryDetails(
    val category: RecoveryCategory,
    val status: Int?,
    val progress: RecoveryProgress,
    val retryAfter: RecoveryRetryAfter,
    val reason: RecoveryReason,
    val wireSent: Boolean,
)

internal class SensitiveRecoveryEvidence(
    val originalCause: Throwable?,
    val receivedBytes: ByteArray,
    val receivedHeaders: Map<String, List<String>>,
    val boundaryCause: Throwable?,
    val completion: CompletionEvidence?,
)

private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR = 500
private const val BAD_GATEWAY = 502
private const val UNAVAILABLE = 503
private const val GATEWAY_TIMEOUT = 504

/** UTF-8 byte counts and tool counts; no semantic payload text is included. */
@Serializable
public data class RecoverySemanticProgress(
    val contentBytes: Long = 0,
    val reasoningBytes: Long = 0,
    val toolFragments: Long = 0,
    val completedToolCalls: Long = 0,
)

/** Validated numeric provider telemetry. Missing provider values remain absent. */
@Serializable
public data class RecoveryStreamMetrics(val usage: Map<String, Long>, val durations: Map<String, Long>)
