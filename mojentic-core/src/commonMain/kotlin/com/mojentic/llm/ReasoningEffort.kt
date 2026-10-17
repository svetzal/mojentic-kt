package com.mojentic.llm

/** Native reasoning controls. Null effort preserves the provider's unknown default. */
public enum class ReasoningEffort {
    DISABLED,
    ENABLED,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    ;

    /** Exact lowercase name; boolean controls require provider-specific encoding. */
    public val wireValue: String
        get() = name.lowercase()
}
