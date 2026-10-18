# Kotlin release contract conformance

This living assessment covers the no-network / deterministic-time correction to
release parts 1–3. It uses mojentic-unify main
`4ae2f73a0a5465b0f6509b87e1148d254f3f64cd` as read-only authority.
The Kotlin source base is `6d0de37f6520a77fb5f81b7f03e8bc263b97ba1b`;
`docs/release-readiness/source.sha256` identifies the tested modified worktree.
No production implementation, public API, API dump, version, release state or ref
changes are part of this correction. Existing JVM/Android distribution scope remains.

Library/provider verification drives the public gateways, broker and sessions with
in-process boundary doubles. The independent Maven consumer exercises those same
packaged implementations against the same versioned JSON contracts. These checks
establish library behavior and package wiring; live upstream providers are outside
this test boundary. No other repository is modified or required to verify this
unreleased local artifact.

| Shared executable contract | SHA-256 |
| --- | --- |
| native-reasoning-v1 | `d5539eb334b69e0f8b698e8cd2c1402772dfabd09af92c652e1d1dc35f34e127` |
| context-window-v1 | `9327502962112a020bafb46c6a677366df732f5a29cd44c92f17e8f632281395` |
| request-fidelity-v1 | `6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea` |

Both sides check these identities before deriving replies. Source manifests also
identify the existing Kotlin recovery/admission/eligibility/timing executable sets.
`build` and `allTests` include library contract verification; the separate
`tools/verify-distribution.sh` command includes packaged consumer verification.

## Complete binding-decision mapping

Classes without a provider prefix are in `com.mojentic.omlx`; paired Anthropic
classes live in `com.mojentic.anthropic`. These are consumer-perspective public
API tests, with public adapters and in-process HTTP transports. Common tests also run
on the Android host target. `build` and `allTests` run the contract suites;
CI executes JVM and Android host tests. Concrete assertion details are in `RECOVERY-CONFORMANCE.md` and the tables below.

| Recovery contract decision | Named executable verification |
| --- | --- |
| Typed category, HTTP status, phase, acceptance, progress, identities, bounded history, private original cause | `CompletionRecoveryHttpTest.exhaustionRetainsEveryAttemptAndTypedCause`; `RecoveryMetadataHttpTest.exactMetadataAndOrderedHistoriesAcrossAllPublicOperations`; `PermanentHttpBoundaryTest.truncatedPermanentAllPublicBoundaries` in both provider packages |
| Safe allowlisted code/request ID, credential/payload echo rejection, provider-specific headers and fields | `RecoveryMetadataHttpTest.unsafeMetadataIsOmittedButExactEvidenceRemainsPrivate`, `validatedRequestIdCannotEchoBearerCredential`; `AnthropicRecoveryHttpTest.safeSummariesWithholdCredentialPayloadAndHeaderEchoes` |
| Default one attempt, legacy success/error behavior, opt-in recovery | `CompletionRecoveryHttpTest.retriesDisabledPreserveSuccessAndLegacyFailure`; both `AdmissionAmendmentHttpTest` default-one-attempt cases |
| Positive attempts, nonnegative delays/budget, overflow-safe ceiling; synchronous typed invalid-policy error, no callbacks or I/O | `RecoveryPolicyTest` constraint/default/boundary cases; both `InvalidRecoveryPolicyHttpTest` public-entrypoint matrices |
| 429/500/502/503/504 and transient transport eligibility; permanent/auth/protocol failures veto replay | `CompletionRecoveryHttpTest.recover503PreservesExactPayloadIdentityAndLifecycle`, `permanentTruncationAndPartialSuccessNeverReplay`; `RecoveryTransportHttpTest.oneAttempt503ZeroRetainsExactFailureWithoutHiddenResend`; admission matrices cover 500/502/504; Anthropic default/permanent matrix |
| Caller-selected transport/status eligibility, independent of admission | `RetryEligibilityHttpTest.transportToggleControlsEligibilityAcrossPublicEntrypoints`, `statusSetControlsEligibilityAcrossPublicEntrypoints`, `disabledTransportLeavesQueuedStreamSuccessUnused`: OpenAI/Anthropic/Ollama/oMLX ordinary, structured and streaming public APIs. Identical failure/admission/payload with only `retryTransport` or membership of 503 toggled; enabled controls recover, disabled controls retain typed ineligible failures and exact STARTED/FAILED/EXHAUSTED lifecycle without admission, backoff or a second request. Exact request bytes, headers, path and attempt identities are checked; a response callback proves queued success consumption or non-consumption. |
| Exponential full jitter, overflow saturation, provider minimum and budget/ceiling refusal; healthy generation not timed out by recovery budget | `CompletionRecoveryHttpTest.fullJitterCeilingsSaturateAndRedirectsStaySingleRequest`, `insufficientBudgetRefusesProviderMinimum`, `budgetsDoNotTimeoutHealthyGenerationAndPreventLateResend`, `deadlineIsRecheckedAfterRequestCaptureBeforeResend` |
| Retry-After seconds, IMF-fixdate, RFC 850, asctime, invalid/past/overflow values | `CompletionRecoveryHttpTest.retryAfterSecondsDatesInvalidAndMinimumRefusal`; `AnthropicRecoveryHttpTest.retryAfterSecondsDatesAndInvalid`, `retryAfterMinimumRefusesCeilingOrBudget`. `RecoveryHttpDateTest` verifies obsolete forms and RFC 850 century handling; this assessment fixes the demonstrated parsing omission and adds HTTP assertions across all four adapters |
| Remote no-hook retry; local unknown acceptance requires admission; no termination/idempotency inferred from IDs or model unload | `AdmissionAmendmentHttpTest.noHookDistinguishesRemoteAndLocalAcrossPublicEntrypoints`, `remoteRetryWithoutHookPreservesExactPayloadAndIdentity`; Anthropic `remoteNoHookRetriesAcrossPublicEntrypoints`; all four `RecoveryCapabilitiesTest` classes |
| Pending admission makes no wire attempt; explicit false/null have distinct reasons; cancellation beats later allow; budget bounds pending wait | OpenAI/Ollama/oMLX `pendingRejectUndecidedAndCancellationNeverConsumeQueuedSentinel`, `undecidedWithoutBudgetIsDistinctFromRejection`; Anthropic `pendingRejectUndecidedAndCancellationPreventQueuedSentinel` |
| Cancellation closes active request and wins in admission/backoff, capture and paused tool delivery | `CompletionRecoveryHttpTest.cancellationWinsDuringActiveRequestAdmissionAndBackoff`; `StreamingRecoveryHttpTest.cancellationDuringAdmissionAndBackoffNeverResends`; `AnthropicRecoveryHttpTest.cancellationWinsInRequestAdmissionBackoffAndPausedToolDelivery` |
| Immutable messages/native history/tools/schema/model/sampling/limits across ordinary, structured and streaming attempts | `CompletionRecoveryHttpTest.recover503PreservesExactPayloadIdentityAndLifecycle`; `StreamingRecoveryHttpTest.admittedRetriesPreserveExactPayloadCaptureAndIdentities`; `AnthropicRecoveryHttpTest.structuredRecoveryKeepsForcedToolSchemaAndDoesNotReplayTools`; request-fidelity and native-reasoning matrices |
| Observed raw vs semantic vs delivered progress; content/reasoning/tool fragments and completed tools interrupt without concatenation or replay; exhaustion stays failure | `StreamingRecoveryHttpTest.allObservedSemanticChannelsInterruptWithoutReplay`, `deliveredProgressIndependentlyPreventsHttpReplay`, `escapedSemanticKeysPreventReplayAtHttpBoundary`; `AnthropicRecoveryHttpTest.keepaliveOnlyCanRecoverButContentReasoningAndToolFragmentsInterrupt` |
| oMLX keepalive is raw-only, no metadata/frame-index pollution | `StreamingRecoveryHttpTest.keepaliveOnlyClosureRequiresAdmissionAndCanRecover`; `ClientStreamTimingHttpTest.noTokenSuccessIgnoresKeepaliveAndEmptyFramesOnBothPublicApis`; existing oMLX decoder tests |
| Tools execute once; request recovery never resets broker depth or session history | `CompletionRecoveryHttpTest.brokerStructuredAndSessionToolCompletionKeepDepthAndExecuteOnce`, `completedToolIsNotReplayedWhenNextCompletionExhausts`; Anthropic `brokerToolsExecuteOnceAcrossFollowupRecovery`, `streamingBrokerAndSessionCompleteToolsOnce` |
| Typed provider/operation vocabulary across all adapters, distinct stream/event operations, legacy serialized vocabulary and optional fields | Both `AdmissionAmendmentHttpTest.typedFailuresPreserveLegacySerializedReports`; `CompletionRecoveryHttpTest.httpFailureUsesClosedProviderAndOperation` |
| Five capabilities per adapter, endpoint-specific citations/inspection date, unsupported/unknown preserved; exact-attempt termination unknown | All four `RecoveryCapabilitiesTest` classes assert each value and citation; legacy custom gateway defaults unknown. No acceptance-NO evidence or remote termination facility is invented |
| Lifecycle ordering and wire counts, request/response capture including partial/error bytes, safe default formatting, capture failures never succeed/retry | `CompletionRecoveryHttpTest.malformedAndCaptureFailuresAreTerminalWithPrivateCauseIdentity`, `captureAccountsForSemanticFieldsBeforeHookFailure`; `StreamingRecoveryLifecycleTest.finalizationObserverFailureProducesOnlyPrivateErrorThroughGatewayAndBroker`; Anthropic evidence/capture tests |
| User migration and capability table; realtime/embeddings excluded | `docs/use-cases/completion-recovery.md`, capability tests; realtime/embeddings remain separate APIs |

| Native reasoning decision | Named executable verification |
| --- | --- |
| Six distinct efforts, exact Ollama booleans/named strings and oMLX enable_thinking/reasoning_effort; omitted effort unknown | `NativeReasoningControlsTest.everyEffortAndOmissionHasExactWireTypeAcrossCompletionModes` |
| Explicit profiles never inferred from name; Qwen3.8 exact Disabled/Low/Medium/XHigh set and history capability | `NativeReasoningControlsTest.profilesValidateExactQwenSetAndHistoryBeforeHttpAcrossModes` |
| Native assistant reasoning separate from content/results; ordinary/streamed completed tools retained only when selected, stable call/result associations | `NativeReasoningContinuityTest.ordinaryAndStreamedToolsPreserveAssistantAssociationOnlyWhenSelected`; `NativeReasoningHttpProofTest.completedAssistantReasoningSurvivesToolAndNextSessionTurn` |
| Exact thinking/reasoning_content history, oMLX preserve_thinking request; structured controls/history | `NativeReasoningStructuredTest.structuredBrokerPreservesSeparateNativeHistoryAndTemplateRequest`; continuity matrix. Server-forced templates may override the request |
| Unsupported OpenAI history/new controls fail explicitly; legacy behavior remains | `NativeReasoningControlsTest.openaiRejectsNewControlsAndOmlxHistoryExtensionBeforeHttp`; Anthropic `NativeReasoningUnsupportedTest.newControlsAndNativeHistoryFailBeforeHttp` |
| Failed/cancelled/unfinished native output never fabricated into history or tool execution; retry before delivery only | `NativeReasoningFailureTest` cancellation/unfinished/followup-failure cases; `NativeReasoningRetryTest.partialNativeStreamCannotBeRetriedOrRetainedEvenWithAdmission`, `admittedRetryBeforeDeliveryKeepsCompletedNativeHistoryAndToolCounts` |

| Metadata/request-fidelity decision | Named executable verification |
| --- | --- |
| Optional single-purpose discovery; window/provenance/optional modelNumCtx, not-advertised vs typed error | `ContextWindowContractTest.sharedHttpCases`, `additionalMalformedShapesMissingModelsAndOptionalFields`, `legacyGatewayInheritsNotAdvertised`, `transportFailuresRetainTypedCause` |
| Exact oMLX model, GET host/auth, absent key; Ollama POST sole model, arbitrary architecture prefix, parameters; errors including 404 | `ContextWindowContractTest.sharedHttpCases`; `ContextWindowProofTest.exactSelectionThroughHttp` |
| Exact OpenAI registry without HTTP/guess; other gateways not advertised | `ContextWindowContractTest.registryLookupHasAnActiveZeroRequestObserverAndNegativeControl`; `AnthropicGatewayTest.contextWindowIsNotAdvertisedWithoutRequests` |
| Pure effective window: clamp caller then model then capacity, absent result stays absent; no discovery mutation | `ContextWindowContractTest.sharedEffectiveCasesAndUnsetConfigurationPrecedence`; `ContextWindowRequestFidelityTest.discoveryPreservesOrdinaryStructuredAndBothStreamingRequestBytes` |
| First-dispatch monotonic clock, thinking/content/argument precedence, keepalive/empty/name ignored, exact units; success/failure/no-token evidence | `ClientStreamTimingTest` controlled byte-channel cases; `ClientStreamTimingHttpTest.publicStreamsMeasureExactKindsAndKeepProviderUnitsOnOrdinaryAndRecoveryPaths`, `failuresBeforeTokensAndArgumentFragmentsRetainExactTerminalEvidence`, `noTokenSuccessIgnoresKeepaliveAndEmptyFramesOnBothPublicApis` |
| Retry admission/backoff stays on one clock; delayed collection excluded; each tool continuation restarts | `FirstDispatchTimingProofTest.delayedCollectionRetryAndMixedFrameUseOneDispatchClock`; `ClientStreamTimingHttpTest.retryIncludesAdmittedDelayAndNewCollectionStartsFreshClock`, tool-continuation cases; controlled clocks, no sleep-based timing claims |
| Nullable unset temperature/numCtx/maxTokens; no invented defaults; one output limit, exact explicit zero; removed numPredict/sentinels; copies preserve other policy | Both `RequestFidelityContractTest` provider matrices; `CompletionConfigTest.depthCopyPreservesRecoveryReasoningRetentionAndUnsetSampling`; API dumps |
| Provider shapes and precedence; Ollama num_predict only from maxTokens; OpenAI output field and registry restrictions; oMLX no unset limit; Anthropic required registry/4096 fallback | `RequestFidelityContractTest` in both provider packages, including `knownRegistryIdsAndAliasesUseMaximumOutputAndUnknownNamesUse4096`; `ContextWindowRequestFidelityTest` |
| Public migration/docs for all three capabilities | `docs/use-cases/model-context-window.md`, `native-reasoning.md`, `request-fidelity.md`, streaming guide and the existing migration documentation |


## Assertions for native reasoning and metadata (parts 2–3)

| Decisions | Concrete assertions in the named methods above |
| --- | --- |
| Reasoning controls and profiles | Compare exact JSON values and types for every effort and omitted effort across ordinary, structured and both stream paths; invalid profiles/history fail before any recorded request. |
| Completed assistant history and tool association | Compare native reasoning separately from content, exact tool call IDs and argument objects, result linkage, next-turn assistant history and `preserve_thinking`; retention off omits native history. Session/tool continuation executes the expected tool once. |
| Failed native output and retry | Partial reasoning/content/tool fragments never enter retained history; cancellation restores the exact prior session snapshot; unfinished completion/follow-up failures never replay executed tools. Admitted pre-delivery retries compare all request bytes and completed history. |
| Context discovery | Contract rows compare typed window, field provenance and optional `modelNumCtx`, exact HTTP method/path/auth/body and zero-request registry lookup. A negative control deliberately performs I/O on the same observer. 401/404/418, malformed/missing data and connection failures retain typed errors, while absent optional fields stay absent. |
| Effective context | Exact min/clamp results across caller/model/capacity/absent combinations; discovery before a completion leaves all ordinary/structured/stream request bytes identical. Legacy gateways return not-advertised. |
| Client timing | Controlled clocks compare exact first-token kind and millisecond values; thinking wins mixed frames, then content, then argument fragments. Keepalives, empty/name-only frames do not start token timing. Delayed collection contributes zero dispatch duration, retries/admission/backoff remain on one clock, tool continuation starts another. Provider duration units and success/error/absent-token evidence remain intact. |
| Request fidelity and compatibility | Compare explicit zero, unset/null omission and each provider's exact parameter names/values across public entrypoints; compare retries byte-for-byte. One output-limit field only; model registry normalization, Anthropic fallback and policy-preserving copies have concrete expected values. Legacy vocabulary/defaults and unknown-enum rejection remain covered. |

## Verification and reviewed inventory

`docs/testing.md` inventories every removed socket/time dependency and its
replacement. `RECOVERY-CONFORMANCE.md` maps each recovery decision to concrete
assertions. Fresh results, separate provider/consumer source identities, resolved
artifact provenance and local artifact hashes are maintained in
`docs/release-readiness/results.md` and `verification.json`. Prior runs do not
prove this modified worktree. Hosted validation of Foundry's finalized source is
separate from local verification; no hosted run is claimed here.
