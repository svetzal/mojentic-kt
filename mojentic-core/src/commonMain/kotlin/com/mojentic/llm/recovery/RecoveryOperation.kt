package com.mojentic.llm.recovery

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Completion operation shared by every adapter; serialized names preserve existing report vocabulary. */
@Serializable
public enum class RecoveryOperation {
    /** Ordinary completion, including broker and session turns. */
    @SerialName("complete")
    COMPLETE,

    /** Schema-constrained completion, including structured response entrypoints. */
    @SerialName("structured")
    STRUCTURED,

    /** Incremental completion through the legacy stream entrypoint. */
    @SerialName("stream")
    STREAM,

    /** Incremental completion through StreamEventsGateway, when implemented by the adapter. */
    @SerialName("streamEvents")
    STREAM_EVENTS,
}
