package com.mojentic.openai

/**
 * Classification of OpenAI model types based on their capabilities and parameters.
 *
 * Mirrors the `ModelType` enum in the other Mojentic ports (`mojentic-ts`,
 * `mojentic-py`, `mojentic-ex`, `mojentic-ru`).
 */
public enum class ModelType {
    /** Models like o1, o3, gpt-5 that use `max_completion_tokens`. */
    REASONING,

    /** Standard chat models that use `max_tokens`. */
    CHAT,

    /** Text embedding models. */
    EMBEDDING,

    /** Content moderation models. */
    MODERATION,
}

/**
 * Static metadata about an OpenAI model.
 *
 * Mirrors the `ModelCapabilities` interface in the other Mojentic ports — a
 * lookup record describing the context window, feature flags, temperature
 * restrictions, and per-API support a broker / chat session might consult
 * before issuing a call. Update conservatively: stale entries are far better
 * than wrong ones.
 *
 * @property id the model identifier as used by the OpenAI API
 * @property modelType classification used to pick token-limit parameters
 * @property contextWindow maximum context window in tokens, or `null` if unknown
 * @property maxOutputTokens maximum output tokens per response, or `null` if unknown
 * @property supportsTools whether the model accepts tool / function definitions
 * @property supportsStreaming whether the model supports streaming responses
 * @property supportsVision whether the model accepts image input
 * @property supportedTemperatures `null` means all temperatures are supported;
 *   an empty list means the `temperature` parameter is not allowed; a non-empty
 *   list enumerates the only permitted values
 * @property supportsChatApi whether the model is reachable via the Chat Completions API
 * @property supportsCompletionsApi whether the model is reachable via the legacy Completions API
 * @property supportsResponsesApi whether the model is reachable via the Responses API
 */
public data class OpenAIModelInfo(
    val id: String,
    val modelType: ModelType,
    val contextWindow: Int?,
    val maxOutputTokens: Int?,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
    val supportsVision: Boolean,
    val supportedTemperatures: List<Double>?,
    val supportsChatApi: Boolean,
    val supportsCompletionsApi: Boolean,
    val supportsResponsesApi: Boolean,
) {
    /**
     * Whether this model is a reasoning model — equivalent to checking
     * [modelType] against [ModelType.REASONING]. Reasoning models use
     * `max_completion_tokens` instead of `max_tokens` and accept a
     * `reasoning_effort` parameter.
     */
    val supportsReasoningEffort: Boolean
        get() = modelType == ModelType.REASONING

    /**
     * The correct request parameter name for token limits, based on [modelType].
     */
    val tokenLimitParam: String
        get() = if (modelType == ModelType.REASONING) "max_completion_tokens" else "max_tokens"

    /**
     * Whether the given [temperature] value is permitted for this model.
     *
     * Returns `true` when [supportedTemperatures] is `null` (no restriction),
     * `false` when it is empty (the parameter is not allowed at all), and
     * otherwise whether [temperature] is one of the enumerated values.
     */
    public fun supportsTemperature(temperature: Double): Boolean {
        val temps = supportedTemperatures ?: return true
        if (temps.isEmpty()) return false
        return temps.contains(temperature)
    }
}

/**
 * Token-window and output-limit constants shared across the model catalog.
 * Named to keep the catalog tables readable and self-documenting.
 */
private object TokenLimits {
    const val GPT5_FLAGSHIP_CONTEXT: Int = 300_000
    const val GPT5_FLAGSHIP_OUTPUT: Int = 50_000
    const val GPT5_SMALL_CONTEXT: Int = 200_000
    const val GPT5_SMALL_OUTPUT: Int = 32_768
    const val DEEP_RESEARCH_CONTEXT: Int = 200_000
    const val DEEP_RESEARCH_OUTPUT: Int = 100_000
    const val O_SERIES_CONTEXT: Int = 128_000
    const val O_SERIES_OUTPUT: Int = 32_768
    const val GPT41_FLAGSHIP_CONTEXT: Int = 200_000
    const val GPT41_FLAGSHIP_OUTPUT: Int = 32_768
    const val GPT41_SMALL_CONTEXT: Int = 128_000
    const val GPT41_SMALL_OUTPUT: Int = 16_384
    const val GPT4O_CONTEXT: Int = 128_000
    const val GPT4O_OUTPUT: Int = 16_384
    const val GPT4_CONTEXT: Int = 32_000
    const val GPT4_OUTPUT: Int = 8_192
    const val GPT35_CONTEXT: Int = 16_385
    const val GPT35_OUTPUT: Int = 4_096
    const val LEGACY_CONTEXT: Int = 16_384
    const val LEGACY_OUTPUT: Int = 4_096
    const val CODEX_CONTEXT: Int = 200_000
    const val CODEX_OUTPUT: Int = 32_768
    const val GPT54_FLAGSHIP_CONTEXT: Int = 1_050_000
    const val GPT54_SMALL_CONTEXT: Int = 400_000
    const val GPT54_OUTPUT: Int = 128_000
}

/** The single temperature value permitted by reasoning models. */
private val REASONING_TEMPERATURES: List<Double> = listOf(1.0)

/**
 * Builds the explicit OpenAI model catalog.
 *
 * Extracted from [OpenAIModelRegistry] as a top-level builder so the registry
 * object keeps a small public surface; the per-family helpers below mirror the
 * `initializeDefaultModels` sections of the other Mojentic ports.
 */
private object ModelCatalog {
    fun build(): List<OpenAIModelInfo> = buildList {
        addAll(reasoningEntries())
        addAll(chatEntries())
        addAll(gpt35Entries())
        addAll(embeddingEntries())
        addAll(legacyAndCodexEntries())
        addAll(gpt54PlusEntries())
    }

    // -- Reasoning models (o1, o3, o4, gpt-5 / 5.1 / 5.2 series) -------------
    // Per API audit (2026-02-04): all reasoning models support tools and
    // streaming, except gpt-5-mini and o4-mini which may have incomplete
    // tool support.
    private val reasoningModelIds: List<String> = listOf(
        "o1", "o1-2024-12-17",
        "o3", "o3-2025-04-16",
        "o3-deep-research", "o3-deep-research-2025-06-26",
        "o3-mini", "o3-mini-2025-01-31",
        "o3-pro", "o3-pro-2025-06-10",
        "o4-mini", "o4-mini-2025-04-16",
        "o4-mini-deep-research", "o4-mini-deep-research-2025-06-26",
        "gpt-5", "gpt-5-2025-08-07",
        "gpt-5-codex",
        "gpt-5-mini", "gpt-5-mini-2025-08-07",
        "gpt-5-nano", "gpt-5-nano-2025-08-07",
        "gpt-5-pro", "gpt-5-pro-2025-10-06",
        "gpt-5.1", "gpt-5.1-2025-11-13", "gpt-5.1-chat-latest",
        "gpt-5.2", "gpt-5.2-2025-12-11", "gpt-5.2-chat-latest",
    )

    private fun reasoningEntries(): List<OpenAIModelInfo> = reasoningModelIds.map { model ->
        val isDeepResearch = model.contains("deep-research")
        val isGpt5 = model.contains("gpt-5")
        val isMiniOrNano = model.contains("mini") || model.contains("nano")

        val contextTokens: Int
        val outputTokens: Int
        when {
            isGpt5 -> {
                contextTokens =
                    if (isMiniOrNano) TokenLimits.GPT5_SMALL_CONTEXT else TokenLimits.GPT5_FLAGSHIP_CONTEXT
                outputTokens =
                    if (isMiniOrNano) TokenLimits.GPT5_SMALL_OUTPUT else TokenLimits.GPT5_FLAGSHIP_OUTPUT
            }
            isDeepResearch -> {
                contextTokens = TokenLimits.DEEP_RESEARCH_CONTEXT
                outputTokens = TokenLimits.DEEP_RESEARCH_OUTPUT
            }
            else -> {
                contextTokens = TokenLimits.O_SERIES_CONTEXT
                outputTokens = TokenLimits.O_SERIES_OUTPUT
            }
        }

        // API endpoint support flags.
        val isResponsesOnly =
            model.contains("pro") || isDeepResearch || model == "gpt-5-codex"
        val isBothEndpoint = model == "gpt-5.1" || model == "gpt-5.1-2025-11-13"

        OpenAIModelInfo(
            id = model,
            modelType = ModelType.REASONING,
            contextWindow = contextTokens,
            maxOutputTokens = outputTokens,
            // Per audit: all reasoning models support tools except these two.
            supportsTools = !(model == "gpt-5-mini" || model == "o4-mini"),
            supportsStreaming = true,
            supportsVision = false,
            // o1/o3/o4 and gpt-5 series only permit temperature = 1.0.
            supportedTemperatures = REASONING_TEMPERATURES,
            supportsChatApi = !isResponsesOnly,
            supportsCompletionsApi = isBothEndpoint,
            supportsResponsesApi = isResponsesOnly,
        )
    }

    // -- Chat models (GPT-4, GPT-4.1, GPT-4o, gpt-5-chat) -------------------
    private val chatModelIds: List<String> = listOf(
        "chatgpt-4o-latest",
        "gpt-4", "gpt-4-0125-preview", "gpt-4-0613",
        "gpt-4-1106-preview",
        "gpt-4-turbo", "gpt-4-turbo-2024-04-09", "gpt-4-turbo-preview",
        "gpt-4.1", "gpt-4.1-2025-04-14",
        "gpt-4.1-mini", "gpt-4.1-mini-2025-04-14",
        "gpt-4.1-nano", "gpt-4.1-nano-2025-04-14",
        "gpt-4o", "gpt-4o-2024-05-13", "gpt-4o-2024-08-06", "gpt-4o-2024-11-20",
        "gpt-4o-audio-preview", "gpt-4o-audio-preview-2024-12-17",
        "gpt-4o-audio-preview-2025-06-03",
        "gpt-4o-mini", "gpt-4o-mini-2024-07-18",
        "gpt-4o-mini-audio-preview", "gpt-4o-mini-audio-preview-2024-12-17",
        "gpt-4o-mini-search-preview", "gpt-4o-mini-search-preview-2025-03-11",
        "gpt-4o-search-preview", "gpt-4o-search-preview-2025-03-11",
        "gpt-5-chat-latest",
        "gpt-5-search-api", "gpt-5-search-api-2025-10-14",
    )

    private val chatBothEndpointIds: Set<String> = setOf(
        "gpt-4.1-nano",
        "gpt-4.1-nano-2025-04-14",
        "gpt-4o-mini",
        "gpt-4o-mini-2024-07-18",
    )

    private fun chatEntries(): List<OpenAIModelInfo> = chatModelIds.map { model ->
        val isMiniOrNano = model.contains("mini") || model.contains("nano")
        val isAudio = model.contains("audio")
        val isSearch = model.contains("search")
        val isGpt41 = model.contains("gpt-4.1")
        val isGpt5Chat = model == "gpt-5-chat-latest"

        val contextTokens: Int
        val outputTokens: Int
        when {
            isGpt5Chat -> {
                contextTokens = TokenLimits.GPT5_FLAGSHIP_CONTEXT
                outputTokens = TokenLimits.GPT5_FLAGSHIP_OUTPUT
            }
            isGpt41 -> {
                contextTokens =
                    if (isMiniOrNano) TokenLimits.GPT41_SMALL_CONTEXT else TokenLimits.GPT41_FLAGSHIP_CONTEXT
                outputTokens =
                    if (isMiniOrNano) TokenLimits.GPT41_SMALL_OUTPUT else TokenLimits.GPT41_FLAGSHIP_OUTPUT
            }
            model.contains("gpt-4o") -> {
                contextTokens = TokenLimits.GPT4O_CONTEXT
                outputTokens = TokenLimits.GPT4O_OUTPUT
            }
            else -> {
                contextTokens = TokenLimits.GPT4_CONTEXT
                outputTokens = TokenLimits.GPT4_OUTPUT
            }
        }

        OpenAIModelInfo(
            id = model,
            modelType = ModelType.CHAT,
            contextWindow = contextTokens,
            maxOutputTokens = outputTokens,
            // Per audit: chatgpt-4o-latest, gpt-4.1-nano, audio and search
            // models do not support tools.
            supportsTools =
            model != "chatgpt-4o-latest" && model != "gpt-4.1-nano" && !isSearch && !isAudio,
            // Per audit: audio models require the audio modality and do not stream.
            supportsStreaming = !isAudio,
            // Per audit: vision retained for gpt-4o family (probe limitation).
            supportsVision = model.contains("gpt-4o") ||
                model.contains("audio-preview") ||
                model.contains("realtime"),
            // Per audit: search models do not allow the temperature parameter.
            supportedTemperatures = if (isSearch) emptyList() else null,
            supportsChatApi = true,
            supportsCompletionsApi = chatBothEndpointIds.contains(model),
            supportsResponsesApi = false,
        )
    }

    // -- GPT-3.5 series -----------------------------------------------------
    private fun gpt35Entries(): List<OpenAIModelInfo> = listOf(
        "gpt-3.5-turbo",
        "gpt-3.5-turbo-0125",
        "gpt-3.5-turbo-1106",
        "gpt-3.5-turbo-16k",
        "gpt-3.5-turbo-instruct",
        "gpt-3.5-turbo-instruct-0914",
    ).map { model ->
        val isInstruct = model.contains("instruct")
        OpenAIModelInfo(
            id = model,
            modelType = ModelType.CHAT,
            contextWindow = TokenLimits.GPT35_CONTEXT,
            maxOutputTokens = TokenLimits.GPT35_OUTPUT,
            supportsTools = !isInstruct,
            supportsStreaming = !isInstruct,
            supportsVision = false,
            supportedTemperatures = null,
            supportsChatApi = !isInstruct,
            supportsCompletionsApi = isInstruct,
            supportsResponsesApi = false,
        )
    }

    // -- Embedding models ---------------------------------------------------
    private fun embeddingEntries(): List<OpenAIModelInfo> = listOf(
        "text-embedding-3-large",
        "text-embedding-3-small",
        "text-embedding-ada-002",
    ).map { model ->
        OpenAIModelInfo(
            id = model,
            modelType = ModelType.EMBEDDING,
            contextWindow = null,
            maxOutputTokens = null,
            supportsTools = false,
            supportsStreaming = false,
            supportsVision = false,
            supportedTemperatures = null,
            supportsChatApi = false,
            supportsCompletionsApi = false,
            supportsResponsesApi = false,
        )
    }

    // -- Legacy & Codex models ----------------------------------------------
    private fun legacyAndCodexEntries(): List<OpenAIModelInfo> = listOf(
        OpenAIModelInfo(
            id = "babbage-002",
            modelType = ModelType.CHAT,
            contextWindow = TokenLimits.LEGACY_CONTEXT,
            maxOutputTokens = TokenLimits.LEGACY_OUTPUT,
            supportsTools = false,
            supportsStreaming = false,
            supportsVision = false,
            supportedTemperatures = null,
            supportsChatApi = false,
            supportsCompletionsApi = true,
            supportsResponsesApi = false,
        ),
        OpenAIModelInfo(
            id = "davinci-002",
            modelType = ModelType.CHAT,
            contextWindow = TokenLimits.LEGACY_CONTEXT,
            maxOutputTokens = TokenLimits.LEGACY_OUTPUT,
            supportsTools = false,
            supportsStreaming = false,
            supportsVision = false,
            supportedTemperatures = null,
            supportsChatApi = false,
            supportsCompletionsApi = true,
            supportsResponsesApi = false,
        ),
        OpenAIModelInfo(
            id = "gpt-5.1-codex-mini",
            modelType = ModelType.REASONING,
            contextWindow = TokenLimits.CODEX_CONTEXT,
            maxOutputTokens = TokenLimits.CODEX_OUTPUT,
            supportsTools = false,
            supportsStreaming = false,
            supportsVision = false,
            supportedTemperatures = null,
            supportsChatApi = false,
            supportsCompletionsApi = true,
            supportsResponsesApi = false,
        ),
        OpenAIModelInfo(
            id = "codex-mini-latest",
            modelType = ModelType.REASONING,
            contextWindow = TokenLimits.CODEX_CONTEXT,
            maxOutputTokens = TokenLimits.CODEX_OUTPUT,
            supportsTools = false,
            supportsStreaming = false,
            supportsVision = false,
            supportedTemperatures = null,
            supportsChatApi = false,
            supportsCompletionsApi = false,
            supportsResponsesApi = true,
        ),
    )

    // -- GPT-5.4 / GPT-5.5 era reasoning models (added 2026-05-21) ---------
    // These break the older gpt-5 context/output formula (1.05M or 400K
    // context, 128K output, image input, both Chat Completions + Responses
    // APIs), so they are registered explicitly.
    private fun gpt54PlusEntries(): List<OpenAIModelInfo> = listOf(
        "gpt-5.4" to TokenLimits.GPT54_FLAGSHIP_CONTEXT,
        "gpt-5.4-2026-03-05" to TokenLimits.GPT54_FLAGSHIP_CONTEXT,
        "gpt-5.4-mini" to TokenLimits.GPT54_SMALL_CONTEXT,
        "gpt-5.4-mini-2026-03-17" to TokenLimits.GPT54_SMALL_CONTEXT,
        "gpt-5.4-nano" to TokenLimits.GPT54_SMALL_CONTEXT,
        "gpt-5.4-nano-2026-03-17" to TokenLimits.GPT54_SMALL_CONTEXT,
        "gpt-5.5" to TokenLimits.GPT54_FLAGSHIP_CONTEXT,
        "gpt-5.5-2026-04-23" to TokenLimits.GPT54_FLAGSHIP_CONTEXT,
        "gpt-5.5-pro" to TokenLimits.GPT54_FLAGSHIP_CONTEXT,
        "gpt-5.5-pro-2026-04-23" to TokenLimits.GPT54_FLAGSHIP_CONTEXT,
    ).map { (model, contextTokens) ->
        OpenAIModelInfo(
            id = model,
            modelType = ModelType.REASONING,
            contextWindow = contextTokens,
            maxOutputTokens = TokenLimits.GPT54_OUTPUT,
            supportsTools = true,
            supportsStreaming = true,
            supportsVision = true,
            supportedTemperatures = REASONING_TEMPERATURES,
            supportsChatApi = true,
            supportsCompletionsApi = false,
            supportsResponsesApi = true,
        )
    }
}

/**
 * Registry of OpenAI model configurations and capabilities.
 *
 * Provides a centralised lookup of model-specific capabilities, parameter
 * mappings, and per-API support flags. Unknown model names fall back to
 * substring pattern matching against [ModelType], and finally to a chat-model
 * default — the registry never throws for an unrecognised model.
 *
 * This object is structurally equivalent to the `OpenAIModelRegistry` class in
 * the other Mojentic ports, adapted to an idiomatic Kotlin singleton.
 */
public object OpenAIModelRegistry {
    /**
     * Substring patterns used to infer a [ModelType] for unknown model names.
     * Ordered most-specific first so that, e.g., `gpt-5.2` wins over `gpt-5`.
     */
    private val patternMappings: List<Pair<String, ModelType>> = listOf(
        "o1" to ModelType.REASONING,
        "o3" to ModelType.REASONING,
        "o4" to ModelType.REASONING,
        "gpt-5.5" to ModelType.REASONING,
        "gpt-5.4" to ModelType.REASONING,
        "gpt-5.3" to ModelType.REASONING,
        "gpt-5.2" to ModelType.REASONING,
        "gpt-5.1" to ModelType.REASONING,
        "gpt-5" to ModelType.REASONING,
        "gpt-4.1" to ModelType.CHAT,
        "gpt-4" to ModelType.CHAT,
        "gpt-3.5" to ModelType.CHAT,
        "chatgpt" to ModelType.CHAT,
        "text-embedding" to ModelType.EMBEDDING,
        "text-moderation" to ModelType.MODERATION,
    )

    private val entries: Map<String, OpenAIModelInfo> = ModelCatalog.build().associateBy { it.id }

    /**
     * Returns the registered [OpenAIModelInfo] for [model], or `null` if the
     * model is not in the explicit catalog.
     *
     * Callers that want graceful fallback for unknown models should use
     * [capabilitiesFor] instead.
     */
    public fun info(model: String): OpenAIModelInfo? = entries[model]

    /**
     * Returns the capabilities for [model], never throwing.
     *
     * Resolution order:
     * 1. exact match against the explicit catalog;
     * 2. substring pattern match against the pattern table, inferring a
     *    [ModelType] and returning conservative defaults for that type;
     * 3. a chat-model default if nothing matches.
     *
     * Steps 2 and 3 emit a warning so callers can notice catalog drift.
     */
    public fun capabilitiesFor(model: String): OpenAIModelInfo {
        entries[model]?.let { return it }

        val modelLower = model.lowercase()
        for ((pattern, modelType) in patternMappings) {
            if (modelLower.contains(pattern)) {
                warn(
                    "Using pattern matching for unknown model: $model " +
                        "(pattern: $pattern, inferred: $modelType)",
                )
                return defaultCapabilitiesFor(model, modelType)
            }
        }

        warn("Unknown model, defaulting to chat model capabilities: $model")
        return defaultCapabilitiesFor(model, ModelType.CHAT)
    }

    /** Names of every model explicitly registered in the catalog. */
    public fun registeredModels(): List<String> = entries.keys.toList()

    /**
     * Whether [model] is classified as a reasoning model. Unknown models are
     * resolved via [capabilitiesFor], so pattern-matched models are honoured.
     */
    public fun isReasoningModel(model: String): Boolean =
        capabilitiesFor(model).modelType == ModelType.REASONING

    /**
     * Whether [model] accepts tool / function definitions. Defaults to `true`
     * for completely unknown models that match no pattern.
     */
    public fun supportsTools(model: String): Boolean = capabilitiesFor(model).supportsTools

    /**
     * Whether [model] accepts image input. Defaults to `false` for unknown models.
     */
    public fun supportsVision(model: String): Boolean = capabilitiesFor(model).supportsVision

    /**
     * Whether [model] is a reasoning model that accepts a `reasoning_effort`
     * parameter. Equivalent to [isReasoningModel]; retained as a distinct
     * helper for callers that consult it by name.
     */
    public fun supportsReasoningEffort(model: String): Boolean =
        capabilitiesFor(model).supportsReasoningEffort

    /**
     * Conservative default capabilities for a [model] of the given [modelType],
     * used as the pattern-matching fallback for unknown models.
     */
    private fun defaultCapabilitiesFor(model: String, modelType: ModelType): OpenAIModelInfo {
        val (tools, streaming, chatApi) = when (modelType) {
            ModelType.REASONING -> Triple(false, false, true)
            ModelType.CHAT -> Triple(true, true, true)
            ModelType.EMBEDDING, ModelType.MODERATION -> Triple(false, false, false)
        }
        return OpenAIModelInfo(
            id = model,
            modelType = modelType,
            contextWindow = null,
            maxOutputTokens = null,
            supportsTools = tools,
            supportsStreaming = streaming,
            supportsVision = false,
            supportedTemperatures = null,
            supportsChatApi = chatApi,
            supportsCompletionsApi = false,
            supportsResponsesApi = false,
        )
    }

    private fun warn(message: String) {
        println("[WARN] OpenAIModelRegistry: $message")
    }
}
