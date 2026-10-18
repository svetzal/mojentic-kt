# Kotlin 3.0.0 distribution-readiness self-assessment

This assessment covers RELEASE-3.0.0.md parts 1–5 on
`fa9694903ab083f7d97e331b7f723f65dc2976a9` plus the worktree changes identified
in `docs/release-readiness/source.sha256`. JVM and Android are the supported
targets. The release version remains 2.1.0 while 3.0.0 approval is held.

The **October 17 library self-validation decision supersedes every historical
external/paired-verification blocker and limited-scope readiness disclaimer in
the records below**. This library validates distribution readiness through its
own public API, executable contracts, production adapters and fresh Maven
consumer. No dependent must install an unreleased library, and no other port or
provider must execute this library's snapshot. These results establish the
specified library behavior against deterministic HTTP inputs; they do not claim
live provider operation, model efficacy or another application's compatibility.
Registry publication and fresh registry installation remain separate release
work. No sibling repository, Git ref or remote publication was changed.

## Current authorities and executable identities

The initiative brief was read and its recorded SHA-256 verified:
`ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
Release parts 1–5 and the binding contracts were inspected read-only at
mojentic-unify `ef9339c6a9682ed5f060bd690b738f6b8eea1d2d`.

| Authority / executable set | Current SHA-256 |
| --- | --- |
| RELEASE-3.0.0.md | `ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e` |
| TRANSIENT-RECOVERY-2026-10.md including October 12 | `a53a3a4eb118fa4687dd703d6ce918dd40dd6cb4eda796abc6c429a6e932e7a3` |
| REASONING-PROTOTYPE-2026-10.md | `0a40f3ad45c593bc779411021e45db569e73d3ecce86c99b201e7f8ebb656809` |
| MODEL-METADATA-2026-10.md including October 17 timing decisions | `d9945dca45ce09e2163843263a75b5e93028b00ee1baf191fc3ff13396830e9c` |
| `contracts/native_reasoning_v1.json` / native-reasoning-v1 | `d5539eb334b69e0f8b698e8cd2c1402772dfabd09af92c652e1d1dc35f34e127` |
| `contracts/context_window_v1.json` / context-window-v1 | `9327502962112a020bafb46c6a677366df732f5a29cd44c92f17e8f632281395` |
| `contracts/request_fidelity_v1.json` / request-fidelity-v1 | `6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea` |

The current `source.sha256` replaces historical Kotlin implementation manifests,
which describe earlier intermediate worktrees. It hashes library source, tests,
API dumps, build configuration, consumer checks and CI workflows using sorted
repository-relative paths. Recovery executable sets kt-admission-v1,
kt-invalid-policy-v1, capabilities/typed-operation matrices,
kt-stream-first-token-v1 and kt-retry-eligibility-v1 are identified by these
current source hashes.
The standalone executable set is **kt-distribution-readiness-v1**, identified
by the `tests/distribution/` and `tools/` entries in that manifest.
Existing fixture identities are checked by tests. The packaged consumer reads
the same JSON artifacts and validates their hashes before deriving HTTP replies.

## Complete binding-decision mapping

Classes without a provider prefix are in `com.mojentic.omlx`; paired Anthropic
classes live in `com.mojentic.anthropic`. These are consumer-perspective public
API tests, with actual production adapter HTTP requests. Common tests also run
on the Android host target. `build` and `allTests` run the contract suites;
CI executes JVM and Android host tests. Detailed per-case mappings in the
historical records and `RECOVERY-CONFORMANCE.md` remain supporting evidence.

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
| Lifecycle ordering and wire counts, request/response capture including partial/error bytes, safe default formatting, capture failures never succeed/retry | `CompletionRecoveryHttpTest.malformedAndCaptureFailuresAreTerminalWithPrivateCauseIdentity`, `captureAccountsForSemanticFieldsBeforeHookFailure`; `StreamingRecoveryHttpTest.finalizationObserverFailureProducesOnlyPrivateErrorThroughGatewayAndBroker`; Anthropic evidence/capture tests |
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
| Retry admission/backoff stays on one clock; delayed collection excluded; each tool continuation restarts | `FirstDispatchTimingProofTest.delayedCollectionRetryAndMixedFrameUseOneDispatchClock`; `ClientStreamTimingHttpTest.realHttpRetryIncludesAdmittedDelayAndNewCollectionStartsFreshClock`, tool-continuation cases; controlled clocks, no sleep-based timing claims |
| Nullable unset temperature/numCtx/maxTokens; no invented defaults; one output limit, exact explicit zero; removed numPredict/sentinels; copies preserve other policy | Both `RequestFidelityContractTest` provider matrices; `CompletionConfigTest.depthCopyPreservesRecoveryReasoningRetentionAndUnsetSampling`; API dumps |
| Provider shapes and precedence; Ollama num_predict only from maxTokens; OpenAI output field and registry restrictions; oMLX no unset limit; Anthropic required registry/4096 fallback | `RequestFidelityContractTest` in both provider packages, including `knownRegistryIdsAndAliasesUseMaximumOutputAndUnknownNamesUse4096`; `ContextWindowRequestFidelityTest` |
| Public migration/docs for all three capabilities | `docs/use-cases/model-context-window.md`, `native-reasoning.md`, `request-fidelity.md`, streaming guide and CHANGELOG Unreleased |

## Release parts 4–5 and demonstrated corrections

All six module build files target JVM/Android. Fresh Maven inspection requires
exactly root/JVM/Android publications for each module and rejects any iOS variant.
CHANGELOG's removal entry directs native Apple callers to mojentic-sw; callers
must replace iOS klibs/XCFramework integration rather than expecting new iOS
coordinates. Nullable limits, removed numPredict, new enum/stream cases and
reasoning retention migrations are documented under Unreleased.

Release part 5's Python assistant filter, Rust negative cast and TypeScript
zero-temperature bugs belong to other ports and are not modified here. Kotlin's
request-fidelity matrix preserves zero; Anthropic ordinary/structured recovery
and assistant-history tests preserve assistant messages; removed numPredict and
shared output-limit validation avoid the Rust cast path. No claim is made about
the other ports' current readiness.

The demonstrated runtime omissions fixed here are obsolete Retry-After dates
(shared normalization before Ktor parsing) and the stale version constant:
`Mojentic.VERSION` now matches unchanged Maven/Gradle version `2.1.0`, rather than
`0.3.0-SNAPSHOT`. The packaged consumer compares it to resolved coordinates.
The separate release must change both values to 3.0.0. No release bump occurs
here. Retry-After parsing/coverage and packaged distribution checks preserve the
completed recovery/reasoning/metadata design.

## Fresh package, quality, security and CI evidence

See [current results](docs/release-readiness/results.md) for exact commands,
source identity, artifact hashes, POM/module inspection, six gate outcomes,
security evidence and hosted-run distinction. The separate consumer build lives
in `tests/distribution`; run `tools/verify-distribution.sh`. It uses Maven
artifacts and their transitive dependencies with no project dependency,
composite substitution or library source inclusion. It includes semantic HTTP
checks, errors/absent fields and terminal failure behavior, not just identifiers
or test counts. The caller-selected eligibility negative control enabled
transport retry against disabled-case assertions and consumed the queued stream success (exit 1).
Setting `retryTransport=false` passed the same HTTP test (exit 0). The final
differential matrix verifies both controls. Foundry retains actual exits and logs.
No new production omission was demonstrated; this correction adds executable
proof and refreshes verification evidence without changing runtime behavior.

## Historical implementation records (superseded scope/coordination statements)

The following records preserve intermediate source hashes, decisions and test
mappings. Their external-verification blockers, separate-scope exclusions and
readiness disclaimers are superseded by the current self-assessment above.

# 3.0.0 recovery and model metadata conformance

This record covers the October 12 admission, invalid-policy and completion-adapter
Capabilities and Operation/provider guarantees.
It does not certify all of transient recovery or readiness to release 3.0.0. JVM and Android are the
supported targets. Provider wire formats and published dependencies are unchanged.

## Contract identity and verification boundary

The initiative brief and `../RELEASE-3.0.0.md` have SHA-256
`ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
The authoritative `../TRANSIENT-RECOVERY-2026-10.md`, including the October 12
amendment, has SHA-256
`a53a3a4eb118fa4687dd703d6ce918dd40dd6cb4eda796abc6c429a6e932e7a3`.
The older port copy has SHA-256
`be317890d22c02f69cda2272aae3c59dc5531c35866ff120b780f852141d9cd0`;
it is not the authority for admission without a hook or without a decision.
These are prose contract hashes, not executable shared-contract verification.
The authoritative contract was read from initiative repository commit
`4b08dcd1d87f520a4c70928c33a2bc84c228093d`; no change was made there.

The original local executable admission cases were versioned as **kt-admission-v1**
at the admission correction recorded below (historical file hashes):

| Executable cases | SHA-256 |
| --- | --- |
| `mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/AdmissionAmendmentHttpTest.kt` | `8ecce6f0e3a516321f62c83f3b25f7704b42a03e99cd5dc7bc7e55f1de4dbcd0` |
| `mojentic-anthropic/src/jvmTest/kotlin/com/mojentic/anthropic/AdmissionAmendmentHttpTest.kt` | `55b77395d5d2fba75a7709d86839707c6f0b4019d88ad11bcf833ed7c3c2ff9e` |

These use actual gateway HTTP clients against scripted loopback sockets, rather
than transport mocks. They characterize consumer behavior, immutable payloads and
lifecycle outcomes. Their scripts are independently authored fixtures, not doubles
derived from a provider-verified shared contract set.

## Admission decisions mapped to named tests

| Guarantee | Named tests (class is `AdmissionAmendmentHttpTest` unless qualified) |
| --- | --- |
| Remote failures may retry without a hook | OpenAI `remoteRetryWithoutHookPreservesExactPayloadAndIdentity`; OpenAI/Ollama/oMLX `noHookDistinguishesRemoteAndLocalAcrossPublicEntrypoints`; Anthropic `remoteNoHookRetriesAcrossPublicEntrypoints` |
| Local failures with unknown acceptance require admission | `noHookDistinguishesRemoteAndLocalAcrossPublicEntrypoints` covers Ollama/oMLX, HTTP 500/502/504 and socket closure; acceptance remains UNKNOWN |
| A suspended hook cannot consume a queued sentinel | OpenAI/Ollama/oMLX `pendingRejectUndecidedAndCancellationNeverConsumeQueuedSentinel`; Anthropic `pendingRejectUndecidedAndCancellationPreventQueuedSentinel` |
| False rejects; null ends undecided, with distinct reasons and no rejection event for null | Both pending/sentinel tests above; `undecidedWithoutBudgetIsDistinctFromRejection` also covers the unbounded hook path |
| Cancellation wins over a later allow decision | Both pending/sentinel tests above; existing `CompletionRecoveryHttpTest.cancellationWinsDuringActiveRequestAdmissionAndBackoff`, `StreamingRecoveryHttpTest.cancellationDuringAdmissionAndBackoffNeverResends`, `AnthropicRecoveryHttpTest.cancellationWinsInRequestAdmissionBackoffAndPausedToolDelivery` |
| Semantic delivery prevents transparent replay without a hook | OpenAI/Ollama/oMLX `semanticDeliveryWithoutHookPreventsReplayThroughBrokerAndSession`; Anthropic `deliveredContentWithoutHookPreventsReplayThroughBrokerAndSession` |
| Default policy still makes one attempt | `defaultOneAttemptStillPreventsQueuedSentinel`; Anthropic `defaultOneAttemptPreventsQueuedSentinel`; existing `CompletionRecoveryHttpTest.retriesDisabledPreserveSuccessAndLegacyFailure` |
| Payload bytes and attempt IDs stay correct; history and safe fields remain bounded/private | No-hook tests assert exact captured/sent bytes, stable logical ID, distinct attempt IDs, ordered attempt numbers, one retained failure, category, phase, eligibility, acceptance and private sentinel exclusion |
| Existing tool depth, counts and history remain intact | `CompletionRecoveryHttpTest.brokerStructuredAndSessionToolCompletionKeepDepthAndExecuteOnce`, `completedToolIsNotReplayedWhenNextCompletionExhausts`; `AnthropicRecoveryHttpTest.brokerToolsExecuteOnceAcrossFollowupRecovery`, `streamingBrokerAndSessionCompleteToolsOnce` |
| Existing bounded exhaustion and admission budgets remain intact | `CompletionRecoveryHttpTest.exhaustionRetainsEveryAttemptAndTypedCause`; `AnthropicRecoveryHttpTest.persistent504HasOrderedBoundedHistory`; `AnthropicRecoveryEvidenceTest.pendingAdmissionWaitsForExplicitPermissionAndBudgetBoundsIt` |

The new entrypoint matrices exercise ordinary and structured gateways, broker
ordinary/structured calls, sessions, gateway/broker/session streams, and the
supported gateway and broker event APIs. Anthropic has no separate event-stream gateway API.
No-hook tests cover transport closure and HTTP 500, 502 and 504. Hooks still run
before every eligible retry when configured, including on remote providers.
Eligibility and replay safety are checked before admission. Automatic remote
retries emit SCHEDULED without inventing a hook admission or decision event.

Current adapters have no exact-attempt evidence source establishing acceptance
NO. The local no-hook exemption is consequently unavailable today. This work
neither infers NO from status/closure nor introduces a cancellation/status operation.
The capability declarations below expose the absence of those operations.
A custom local server behind an OpenAI/Anthropic adapter must configure a hook;
admission follows the adapter's provider identity, not URL host heuristics.

## Verification record

Tested base commit: `c097479525d6c88989c77ab53e2a365cea6c78ab`, plus the uncommitted
admission correction. Foundry owns finalization; no new commit or ref is created.
The first real OpenAI HTTP 502 probe failed with ADMISSION_REQUIRED before the
source correction (exit 1), then completed successfully with identical retry
payload bytes and correct identities after it (exit 0).

Before the final test-helper refactor, the focused command passed (exit 0), with six OpenAI/Ollama/oMLX test methods
and four Anthropic methods, each with zero failures, errors or skips:

```sh
./gradlew :mojentic-openai:jvmTest --tests '*AdmissionAmendmentHttpTest*' \
  :mojentic-anthropic:jvmTest --tests '*AdmissionAmendmentHttpTest*' \
  -Pkotlin.compiler.execution.strategy=in-process
```

The deterministic socket tests run on JVM. Android compilation and existing host
tests are checked separately; no Android device transport run is claimed.
The final full gate and local CI-equivalent task command passed (exit 0):

```sh
./gradlew ktlintCheck detekt build allTests apiCheck dokkaGenerate \
  jvmJar assembleAndroidMain jvmTest testAndroidHostTest
```

It used JDK 21 for Gradle and JDK 17 for compilation, a writable copy of the
existing Gradle cache via `GRADLE_USER_HOME=/tmp/mojentic-kt-gradle`, and
`kotlin.compiler.execution.strategy=in-process` in that scratch Gradle home to
avoid the sandbox's read-only Kotlin daemon directory. No project toolchain,
dependency or build configuration was changed. Standard Gradle up-to-date/cache
reuse remained enabled. Result files contain 775 JVM/Android executions in 134
suites, with zero failures, errors or skips; these are execution counts across
targets, not unique tests. Both new admission classes passed (6 and 4 methods).
The final command passed after extracting test stream-content collection to
satisfy Detekt; no warning suppression was added.

Dokka emitted unresolved-link warnings in existing documentation. An unchanged
realtime source also emitted a redundant-else compiler warning during the full
build sequence. No warning-free build claim is made. Hosted GitHub Actions and
Android device transport verification were not run. JaCoCo is not wired in this
KMP repository; its configured test gate is `allTests`.

The configured security audit and CI toolchain detection passed (exit 0):

```sh
./gradlew javaToolchains dependencyCheckAggregate --no-parallel \
  -PdependencyCheckDataDirectory=/tmp/mojentic-kt-gradle/dependency-check-data \
  '-PdependencyCheckNvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz'
```

The feed metadata was checked first (`lastModifiedDate` October 16, 2026,
18:00:09 -04:00). Both JDK 17 and 21 were detected. The unchanged configured
scope/threshold produced 305 dependency entries, zero active vulnerabilities and
52 suppressed findings. No suppression, dependency, threshold or scope changed.
OSS Index remained unavailable without credentials, and the .NET assembly analyzer
reported its missing runtime. This is a configured-audit pass, not a claim of
unfiltered or unavailable-analyzer verification. The JSON report SHA-256 is
`c8bfde43e90af22642f83c6b7f6a965bb2c03e3efa429e33cc869ccfa604749b`.

## Initiative finding: shared verification is missing

No provider wire boundary was changed. No identifiable executable contract set
verified by both these consumers and OpenAI, Anthropic, Ollama or oMLX was found
or executed. Provider-side tested commits, verification commands and results are
unavailable; two-sided compatibility is **not established** by these loopback
scripts. Coordination must establish a shared executable contract/version and
provider verification for supported versions, optional fields, transient and
permanent errors before claiming cross-repository contract conformance. Do that
work in the relevant authorized repositories; this task changes Kotlin only.

## Explicit outstanding decisions and separate work

- An evidence-backed acceptance-NO path requires an agreed exact-attempt evidence
  source; none is introduced or claimed here.
- Shared executable contract distribution and provider verification remain
  initiative coordination work, as above.
- Reasoning, metadata, typed operation/provider vocabulary,
  remaining recovery amendments belong to separate scope. Invalid-policy validation
  is covered by the correction below.
- Other ports and consumer migrations are not verified here. No version bump,
  release, tag or publication is authorized by this correction.

## Invalid-policy correction (October 12 Typed errors)

Tested revision: `137a8b827faadd09d0d94c7dafa493df648094b2` plus this
uncommitted correction. Foundry owns Git finalization. The initiative brief hash
was verified as `ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
The authoritative amendment hash remains
`a53a3a4eb118fa4687dd703d6ce918dd40dd6cb4eda796abc6c429a6e932e7a3`.
Its Typed errors decision requires invalid policies to fail with a typed policy
error on every entrypoint. The older port copy does not include this amendment.

`RecoveryPolicy` remains immutable and validates synchronously at construction.
An invalid policy cannot be passed to a completion API. Each existing constraint
now throws `InvalidRecoveryPolicyException`, a `MojenticException`, with a safe
`RecoveryPolicyViolation` enum field, no cause and no retained caller values or
callbacks. Parameter-order validation, accepted values and all valid request
behavior are preserved. No gateway payload, recovery/admission logic or tool
execution algorithm changed. The Unreleased changelog contains migration code.

### Named executable evidence: kt-invalid-policy-v1

| Constraint or behavior | `RecoveryPolicyTest` | `InvalidRecoveryPolicyHttpTest` in both `com.mojentic.omlx` and `com.mojentic.anthropic` |
| --- | --- | --- |
| Zero attempts | `zeroAttemptsHasTypedConstraint` | `zeroAttemptsRejectsBeforeEveryPublicSurface` |
| Negative attempts | `negativeAttemptsHasTypedConstraint` | `negativeAttemptsRejectsBeforeEveryPublicSurface` |
| Negative base delay | `negativeBaseDelayHasTypedConstraint` | `negativeBaseDelayRejectsBeforeEveryPublicSurface` |
| Ceiling below base | `ceilingBelowBaseHasTypedConstraint` | `ceilingBelowBaseRejectsBeforeEveryPublicSurface` |
| Negative ceiling | `negativeCeilingHasTypedConstraint` | `negativeCeilingRejectsBeforeEveryPublicSurface` |
| Long.MAX_VALUE ceiling | `maximumLongCeilingHasTypedConstraint` | `maximumLongCeilingRejectsBeforeEveryPublicSurface` |
| Negative budget | `negativeBudgetHasTypedConstraint` | `negativeBudgetRejectsBeforeEveryPublicSurface` |
| Multiple invalid values | `multipleInvalidValuesReportFirstConstraintWithoutInvokingCallbacks` | Each HTTP case asserts actual exception subtype, violation, absent cause/suppressed errors and private-sentinel exclusion |
| Zero delays and budget, no construction hooks | `zeroDelaysAndBudgetAreValidWithoutCallingHooks` | Each HTTP case follows rejection with a valid zero-delay success through the same surface |
| Maximum valid values and equal delays | `maximumValidValuesAndEqualDelaysArePreserved` | Existing payload/retry regressions run in the full gate |
| Optional defaults and unrestricted fields | `defaultsRemainOneAttemptWithOptionalLimitsAbsent`, `pastDeadlinesAndCallerStatusesRemainUnrestricted` | Existing default-one-attempt and cancellation/admission evidence above remains required |

The HTTP cases cover OpenAI, Anthropic, Ollama and oMLX: ordinary completion,
structured JSON and structured response, gateway streaming, broker completion,
`generateResponse`, structured broker completion, broker streaming, session send
and session streaming. OpenAI/Ollama/oMLX additionally cover gateway `streamEvents`
and broker `generateStreamEvents`; Anthropic has no event-stream gateway surface.
All stream results are collected and content is asserted. Each invalid call site
constructs its policy as the public API argument, so rejection precedes entry
into the request pipeline. Assertions establish zero server requests, admission,
capture, lifecycle callbacks and tool execution. An instrumented tool is supplied
where supported. A subsequent successful call verifies the same live socket and
adapter path, lifecycle/capture wiring and absence of spurious tool execution.

The proof-first `invalidPolicyRejectsBeforeSessionHttpAndCallbacks` probe failed
with the original generic IllegalArgumentException (exit 1), then passed after
the constructor correction (exit 0), including a successful real session HTTP
response. The final probe additionally asserts the typed violation and cause.

These are local executable consumer cases, not a provider-verified shared
contract. The shared-contract coordination gap above remains open: no common
executable contract version/hash, provider commits or provider verification
commands/results are available for either supported old or new provider
versions. Two-sided compatibility remains unverified; no sibling repository was
changed. Other typed-error amendments remain separate work.

The local **kt-invalid-policy-v1** executable set includes these tests and the
shared admission surface helpers at the tested revision:

| File | SHA-256 |
| --- | --- |
| `mojentic-core/src/commonTest/kotlin/com/mojentic/llm/recovery/RecoveryPolicyTest.kt` | `e967c26a1e2e5bc49595a52aea15cbdf983fb82a5f3d30e0181a1433ab5cd590` |
| `mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/InvalidRecoveryPolicyHttpTest.kt` | `ed016444d3fe8c040eb921a250decb51f690e4f5bed3163fd90bffdae8fcb520` |
| `mojentic-anthropic/src/jvmTest/kotlin/com/mojentic/anthropic/InvalidRecoveryPolicyHttpTest.kt` | `ec511aaceaefb020fb1aff8112aab06f9c3fd445966a479d6a6126654adcc5cf` |
| `mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/AdmissionAmendmentHttpTest.kt` | `cca1cdaab29bab853ed5c98f3e28b1c99abcedcd41e45500fb5b1bdf427c947b` |
| `mojentic-anthropic/src/jvmTest/kotlin/com/mojentic/anthropic/AdmissionAmendmentHttpTest.kt` | `a5a492b7348f156ac083cec8b3c419498417fc23235d5d59b77c70ab351d4075` |

The historical kt-admission-v1 hashes above identify the earlier admission-only
record. The helper files now also expose structured-response/native-response
surfaces and accept instrumented tools; this correction reruns their original
admission/cancellation/tool behavior tests as well as the invalid-policy cases.

### Final verification of this correction

The focused command passed (exit 0) after formatting:

```sh
./gradlew :mojentic-core:jvmTest --tests '*RecoveryPolicyTest*' \
  :mojentic-openai:jvmTest --tests '*InvalidRecoveryPolicyHttpTest*' \
  :mojentic-anthropic:jvmTest --tests '*InvalidRecoveryPolicyHttpTest*'
```

`apiDump` generated only the two new public types in the core JVM API baseline.
The expanded Anthropic test dispatcher initially failed Detekt's complexity
threshold; extracting stream-text collection resolved it without suppression.
The final `./gradlew ktlintCheck detekt` passed (exit 0).

Only the root `.hone-gates.json` was discovered. All six required gates and the
applicable local build/security CI tasks passed together (exit 0, 3m 54s):

```sh
./gradlew ktlintCheck detekt build allTests apiCheck dokkaGenerate \
  jvmJar assembleAndroidMain jvmTest testAndroidHostTest javaToolchains \
  dependencyCheckAggregate --no-parallel \
  -PdependencyCheckDataDirectory=/tmp/mojentic-kt-gradle/dependency-check-data \
  '-PdependencyCheckNvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz'
```

Foundry capture wrapped these runs. They used the existing writable Gradle cache
at `GRADLE_USER_HOME=/tmp/mojentic-kt-gradle`, in-process Kotlin compilation,
JDK 21 for Gradle and the JDK 17 compiler toolchain. Toolchain detection confirmed
both installed JDKs. Cache reuse was enabled. No project build configuration,
dependency or audit suppression changed. No publication task was run.

The final reports contain 814 JVM/Android executions across 138 suites, with zero
failures, errors or skips. These are executions across targets, not unique test
counts. `RecoveryPolicyTest` passed all 12 methods on both JVM and Android host;
the OpenAI/Ollama/oMLX invalid-policy class passed all 8 methods and the Anthropic
class all 7. The original admission classes passed all 6 and 4 methods,
respectively, including their expanded public-surface loops. Android device
transport, hosted CI and macOS execution were not run. Coverage/JaCoCo is not
wired in this KMP project; `allTests` is its required test gate. Dokka v2 uses
`dokkaGenerate`; the configured security CI uses `dependencyCheckAggregate`.

The configured audit reported 305 dependency entries, zero active vulnerabilities
and 52 existing suppressed findings. NVD feed metadata was checked before the
run: `lastModifiedDate:2026-10-16T18:00:09-04:00`. The report SHA-256 is
`8123d0d95b636cb1c9ae012c682dea09f0c34906ac14f77b7e8103f29be8a00e`.
OSS Index was disabled for missing credentials, and the .NET assembly analyzer
could not initialize without its runtime. This is a configured-audit pass, not
verification by unavailable analyzers or an unfiltered vulnerability claim.

The build emitted existing unresolved Dokka links, a deprecated `readLine()`
example warning, Android analytics filesystem and native-access warnings, and
Gradle deprecation notices. No warning-free build claim is made. The generated
policy-error page was checked for its typed violation field, privacy contract
and enum link. Final diff whitespace and behavioral proof JSON/log checks passed.
Shared provider-contract verification remains missing as recorded above.


## October 12 Capabilities correction (2026-10-17)

Tested Kotlin base: `d5c517d5af5455fdf636c1ff5849d1bc340c598c` plus this
uncommitted correction. The Foundry initiative brief SHA-256 was verified as
`ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
The new public boundary is `LlmGateway.recoveryCapabilities`, with conservative
unknown defaults for custom gateways. The four completion adapters expose
endpoint-scoped facility evidence inspected on 2026-10-17. Their provider
payloads, admission/retry behavior and model capabilities are unchanged.

The public immutable value types are `RecoveryCapabilities`,
`RecoveryCapability` and `RecoverySupport`. Each facility has its own status,
endpoint scope, provider documentation URLs, inspection date and rationale.
The [recovery guide](docs/use-cases/completion-recovery.md#completion-adapter-capabilities)
records the endpoint table, inspected references and limitations. Local HTTP
cancellation is supported; remote request cancellation and individual in-flight
request status are unsupported by these adapters; idempotency is unknown.
Exact-attempt termination remains unknown for all four. No declaration can
substitute for evidence about a particular failed attempt or authorize a retry.

### Named public-surface tests

The local regression set is identified as **kt-recovery-capabilities-v1**.
It is not a provider-verified cross-repository contract. Each row below is a
named test in **each** of these four common-test suites (executed on JVM and
Android host by `allTests`):

- `mojentic-openai/src/commonTest/kotlin/com/mojentic/openai/RecoveryCapabilitiesTest.kt`
- `mojentic-anthropic/src/commonTest/kotlin/com/mojentic/anthropic/RecoveryCapabilitiesTest.kt`
- `mojentic-ollama/src/commonTest/kotlin/com/mojentic/ollama/RecoveryCapabilitiesTest.kt`
- `mojentic-openai/src/commonTest/kotlin/com/mojentic/omlx/RecoveryCapabilitiesTest.kt`

| Named test in each suite | Expected value and specific evidence |
| --- | --- |
| `localRequestCancellationIsSupportedWithoutRemoteTermination` | SUPPORTED; calling coroutine cancellation, not remote termination |
| `remoteRequestCancellationIsUnsupported` | UNSUPPORTED; OpenAI Responses / Anthropic batch exclusions; Ollama model unload exclusion; oMLX no request-scoped operation |
| `requestStatusIsUnsupported` | UNSUPPORTED; stored OpenAI completion is not in-flight status; Anthropic batch exclusion; Ollama loaded models and oMLX aggregate activity are insufficient |
| `idempotencyIsUnknownWithoutDeduplicationGuarantee` | UNKNOWN; correlation IDs are not idempotency keys |
| `exactAttemptTerminationIsUnknownWithoutAttemptEvidence` | UNKNOWN; HTTP closure and model-level aggregate activity are insufficient |

Every test obtains the facility through `LlmGateway` on a concrete adapter and
asserts its specific status, exact endpoint scope, inspection date, complete
source URL list and facility-specific rationale. These are not model-registry
or count-only assertions. The additional oMLX proof test
`localCancellationDoesNotProveRemoteOrExactAttemptTermination` asserts all five
statuses together and termination/cancellation evidence. It rejected the
inherited UNKNOWN declaration with an assertion failure (exit 1), then passed
unchanged after the adapter supplied its declarations (exit 0), before the
other named tests and documentation were expanded.

### Initiative finding: capability boundary paired verification

This adds a Kotlin library/consumer API boundary, without changing any HTTP
provider boundary. No shared executable contract mechanism was found in this
repository for this new surface. The local regression identifier above and
existing prose contract hashes do not establish cross-repository compatibility.
Kotlin-side verification runs at the tested base plus this worktree; no external
consumer commit, shared executable contract version/hash, paired command or
verification result is available. Provider-side commits and paired verification
of supported old/new versions, errors and optional fields also remain missing
as recorded in the earlier finding. Coordination must supply and verify the
same versioned executable contracts on each relevant side in separately
authorized repositories. No sibling repository was changed; paired verification
remains unfinished initiative work, and this record makes no compatibility or
release-readiness claim.

### Capability correction verification

All six required `.hone-gates.json` gates passed together (exit 0, 3m 47s):

```sh
GRADLE_USER_HOME=/tmp/mojentic-kt-gradle ./gradlew \
  ktlintCheck detekt build allTests apiCheck dokkaGenerate
```

| Required gate | Result |
| --- | --- |
| `./gradlew ktlintCheck` | PASS |
| `./gradlew detekt` | PASS |
| `./gradlew build` | PASS |
| `./gradlew allTests` | PASS |
| `./gradlew apiCheck` | PASS |
| `./gradlew dokkaGenerate` | PASS |

The final test reports contain 856 JVM/Android host executions across 146 suites,
with zero failures, errors or skips. All five named methods were checked in each
of the four adapter suites on both targets: 40 facility executions. The additional
oMLX proof passed on both targets, for 42 capability executions in total.
Existing request, payload, admission, cancellation, broker and session cases
also passed as part of the full suite. These are local behavior checks, not
live provider verification or cross-repository compatibility evidence.

`ktlintFormat apiDump` passed before the final gates. API baseline changes are
limited to the new core types/default getter and four adapter getters. Generated
Dokka pages were checked for the five evidence properties. Existing unresolved
Dokka links, an example deprecation, Android analytics filesystem and Gradle
warnings remain; no warning-free build claim is made. Gradle used its pinned
JDK 21 daemon and the JDK 17 compiler toolchain with in-process compilation and
the existing writable cache. Android device, macOS and hosted CI were not run.
Coverage/JaCoCo is not wired in this KMP project; `allTests` is the test gate.
Foundry capture retained complete run logs, and the behavioral proof JSON,
nonzero/zero exit codes and both proof logs were validated. No version,
dependency, payload, retry/admission or model-capability change was made.
No publication, commit or ref-changing command was run.


The configured dependency audit also passed (exit 0, 35s):

```sh
GRADLE_USER_HOME=/tmp/mojentic-kt-gradle ./gradlew dependencyCheckAggregate \
  --no-parallel \
  -PdependencyCheckDataDirectory=/tmp/mojentic-kt-gradle/dependency-check-data \
  '-PdependencyCheckNvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz'
```

NVD modified-feed metadata was checked before the audit:
`lastModifiedDate:2026-10-16T18:00:09-04:00`. The generated report contains 305
dependency entries, zero active findings and 52 existing suppressed findings.
Its SHA-256 is
`45d146703ab31fe8bdaa73a403802d7ee2f370539adfa13bc2d3486adecfcfbf`.
OSS Index was disabled for missing credentials; the .NET analyzer could not
initialize without its runtime. This is a configured-audit pass, not unfiltered
or unavailable-analyzer verification. No suppression or dependency was changed.

## October 12 Operation and provider correction

The authoritative amendment at `mojentic-unify` commit
`4b08dcd1d87f520a4c70928c33a2bc84c228093d` was inspected again for this
correction; its SHA-256 remains
`a53a3a4eb118fa4687dd703d6ce918dd40dd6cb4eda796abc6c429a6e932e7a3`.
The initiative brief was read and its supplied SHA-256 verified as
`ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.

Before migration, public failure and summary fields, HTTP boundary parameters,
adapter operation literals, remote admission and streaming interruption checks
used strings. The behavioral probe observed an actual adapter HTTP 503 failure
with a String provider, rejected that result, then passed with closed enums.
That first probe used a runtime enum-kind assertion so it could execute against
the pre-migration API; the retained regression test now asserts exact enum values.
The migration covers all these coupled paths. Report JSON retains its existing
provider and operation vocabulary. Stream and streamEvents remain distinct;
Anthropic retains its existing stream-only surface. No provider capability
claim, payload, retry timing, admission or tool behavior was amended here.

| Guarantee | Named executable tests |
| --- | --- |
| Actual ordinary and structured HTTP failures expose closed provider and operation values | `CompletionRecoveryHttpTest.httpFailureUsesClosedProviderAndOperation` (OpenAI, Ollama, oMLX) |
| Every supported gateway/broker/session completion and stream entrypoint exposes concrete enum values | OpenAI/Ollama/oMLX and Anthropic `AdmissionAmendmentHttpTest.typedFailuresPreserveLegacySerializedReports` |
| Lifecycle failure fields and serializable histories retain the same typed values | Both `typedFailuresPreserveLegacySerializedReports` matrices |
| Old/new serialized vocabulary, absent optional metadata, report/lifecycle round trips, unknown-value rejection and privacy | Both `typedFailuresPreserveLegacySerializedReports` matrices use actual HTTP 400 failures |
| Remote/local admission semantics retain the same identities | `AdmissionAmendmentHttpTest.noHookDistinguishesRemoteAndLocalAcrossPublicEntrypoints`; Anthropic `remoteNoHookRetriesAcrossPublicEntrypoints` |
| Permanent truncated failures retain request bytes, typed operations, lifecycle and tool/session behavior | Both `PermanentHttpBoundaryTest.truncatedPermanentAllPublicBoundaries` matrices |
| Delivered semantic progress still interrupts streaming without replay | `StreamingRecoveryHttpTest.deliveredProgressIndependentlyPreventsHttpReplay`, `AdmissionAmendmentHttpTest.semanticDeliveryWithoutHookPreventsReplayThroughBrokerAndSession`; Anthropic `deliveredContentWithoutHookPreventsReplayThroughBrokerAndSession` |

The local executable set is identified as **kt-recovery-identity-v1**: the new
probe and report matrices above, alongside the migrated admission/permanent
boundary assertions. Its file hashes and verification results are recorded
below after the final gates. Kotlin source identity starts at
`384d7a0aaa7140814a5d2c594f873add29bf0134` plus the uncommitted correction.

### Initiative finding: paired verification still unavailable

This changes the public Kotlin recovery API and retains its existing JSON
vocabulary. No versioned executable contract shared with an external consumer
was found in this repository. The local socket scripts are independently
maintained behavior fixtures, not doubles derived from provider-verified
contracts. They establish local adapter behavior only.

Counterpart repository, tested source identity, shared contract version/hash,
provider verification command and consumer verification result are unavailable
for this correction. Initiative coordination must assign that counterpart and
run the same identifiable executable contract set in both repositories before
claiming compatibility. No counterpart repository was modified. The previously
relayed paired-capability-verification and endpoint-specific client-abort
citation findings remain separate initiative work; this correction neither
resolves nor re-certifies them.

### Executable identity for this correction

`kt-recovery-identity-v1` identifies the probe, both typed-report matrices and
both permanent-boundary matrices listed above. The files are:

| Executable source | SHA-256 |
| --- | --- |
| `mojentic-anthropic/src/jvmTest/kotlin/com/mojentic/anthropic/AdmissionAmendmentHttpTest.kt` | `75623c9dd6ec5bdde6573af657d4480d2baeed3804a402df7d44c89a0020c561` |
| `mojentic-anthropic/src/jvmTest/kotlin/com/mojentic/anthropic/PermanentHttpBoundaryTest.kt` | `87bfc202f6ce7167e4f9c819e7a86fb7059db4141f59fb69be2e0e7025eed754` |
| `mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/AdmissionAmendmentHttpTest.kt` | `18ceb21b40108d5955554d30f54beb7c252f457144b2aac315af2914b4349b07` |
| `mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/CompletionRecoveryHttpTest.kt` | `5b16530c26647868e977f3676eb5c070528a6afd951c0446064629a941b2eccc` |
| `mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/PermanentHttpBoundaryTest.kt` | `3040a4fe56c2cdf0fcce8af2671030625860f5801e780a3efbef68ce0313d7fe` |

The SHA-256 of the sorted `sha256sum`-format file manifest (UTF-8, LF, two spaces
between hash and path, trailing LF) is `775edbce3e7d328875ae307d762df24633860867bf1b0087f613ff456259669a`.
The same manifest recipe for this correction's changed Kotlin source, test and
API baseline files gives `c91762f50b0c0dfba95cf8428bfe0104ebc77976e7baf525b00f1addadcdebd5`. This identifies the tested uncommitted
source on base `384d7a0aaa7140814a5d2c594f873add29bf0134`; it is not a new commit.
External counterpart source and verification results remain unavailable.

### Final local verification for Operation and provider

All six required `.hone-gates.json` commands passed on this uncommitted source.
Commands used `GRADLE_USER_HOME=/tmp/mojentic-c4-gradle`, JDK 21 for the Gradle
daemon, the configured JDK 17 compiler toolchain and in-process Kotlin
compilation. The writable Gradle home changes build execution storage only.

| Gate command | Exit code | Duration |
| --- | --- | --- |
| `./gradlew ktlintCheck` | 0 | 51s |
| `./gradlew detekt` | 0 | 1m 16s |
| `./gradlew build` | 0 | 2m 15s |
| `./gradlew allTests` | 0 | 9s |
| `./gradlew apiCheck` | 0 | 5s |
| `./gradlew dokkaGenerate` | 0 | 1m 59s |

`./gradlew allTests` verified all five named tests in `kt-recovery-identity-v1`:
the HTTP identity probe, both serialized-report matrices and both permanent
boundary matrices. Existing payload, admission, cancellation, streaming,
privacy and tool-execution regressions passed in the same build/test gates.
Initial formatting and Detekt method-length findings were fixed without
suppressions; the table records the complete final run after those changes.
API baseline changes are confined to the two enums and the typed failure,
summary and HTTP boundary signatures. KDoc and the recovery migration guide
were updated with them.

These are Linux/JVM HTTP results and Android compilation/build checks. No
Android device, macOS or hosted CI verification was performed. Existing Gradle,
Android analytics, Kotlin example/realtime and unresolved Dokka-link warnings
remain; no warning-free build claim is made. Paired external contract
verification remains unavailable as recorded in the initiative finding above.
The behavioral proof's JSON shape, recorded nonzero/zero probe exits and both
log files were validated. Changes remain uncommitted; versions, refs,
dependencies, capability claims and publication were not changed.

The configured dependency audit also passed (exit 0, 1m 21s):

```sh
GRADLE_USER_HOME=/tmp/mojentic-c4-gradle ./gradlew dependencyCheckAggregate \
  --no-parallel \
  -PdependencyCheckDataDirectory=/tmp/mojentic-c4-gradle/dependency-check-data
```

The existing NVD bulk-feed transport was used after checking modified-feed
metadata (`lastModifiedDate:2026-10-16T18:00:09-04:00`). The report has 305
dependency entries, zero active findings and 52 existing suppressed findings;
its SHA-256 is `83cea66657f93c5dd2974bf4bd05e4f3d0f43c0f26b5aa23edfc0a53be755909`.
OSS Index lacked credentials and the .NET analyzer lacked its runtime. This
is a configured-audit pass with those existing limits; no dependencies,
suppressions, analyzer settings or advisory policy were changed.

## Model metadata section 1: context-window discovery (2026-10-17)

This correction covers only section 1 of `MODEL-METADATA-2026-10.md` and the
context-window portion of release part 3. Timing, request-default migration,
reasoning, remaining recovery amendments and coordinated release readiness are
separate initiative work. No versions, refs or published artifacts change.

### Authority and executable identity

- Initiative brief / release plan SHA-256:
  `ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
- Model metadata prose SHA-256:
  `a085da1c0d110f9d587a24ace6ac2854b653e1ac28905b3829d94d665b2c906e`;
  initiative repository source `4b08dcd1d87f520a4c70928c33a2bc84c228093d`.
- Executable **context-window-v1** is copied byte-for-byte from Elixir
  `test/contracts/context_window_v1.json` at inspected source
  `df29a1c36f6b4b172b3ff352d40201d6583a9890` to
  [contracts/context_window_v1.json](contracts/context_window_v1.json).
  Its SHA-256 is
  `9327502962112a020bafb46c6a677366df732f5a29cd44c92f17e8f632281395`.
  The differently hashed Python artifact was not used.
- Kotlin tested source starts at
  `b663e6a600c5e4c80edc994347b5d40e1f1eb63c` plus this uncommitted correction.
  The final implementation/test/API manifest hash is recorded below.

`ContextWindowContractTest.artifactIdentity` checks the version and full artifact
hash on every run. Discovery socket replies come directly from its `cases`;
expected values, field provenance and optional `model_num_ctx` are read from
those same cases. All 42 HTTP rows, four registry rows and eight effective rows
execute through the public methods or public pure helper. No independently
authored discovery fixtures substitute for this shared set. Additional malformed
shape tests are explicitly local extensions. The v1 parameter strings contain
literal escaped separators; the parser accepts those and ordinary newline/tab
text, with the latter separately tested.

### Section-1 mapping

Test classes are in `mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/`
unless qualified. JVM tests execute in both `build` and `allTests` gates and
CI's `jvmTest`.

| Binding behavior | Executable evidence |
| --- | --- |
| Optional operation, legacy custom gateways return not advertised | `ContextWindowContractTest.legacyGatewayInheritsNotAdvertised` implements the old source interface without a metadata override |
| Exact oMLX selection, configured host, GET path, Bearer key and absent key | `ContextWindowProofTest.exactSelectionThroughHttp`; `sharedHttpCases` executes artifact `exact-selection-without-api-key`, HTTP errors and 201 success; checks exact request line, body and authorization |
| Ollama POST body, both architecture prefixes, full field provenance, `model_num_ctx` | `sharedHttpCases`: `qwen3_5`, `qwen4_exp`, `model-num-ctx`, `whitespace-num-ctx`; asserts method/path, JSON content type, exact selected model and sole request body field |
| Missing fields / matching keys / models return null | `sharedHttpCases` missing-info, no-matching-key, no-architecture-from-num-ctx; `additionalMalformedShapesMissingModelsAndOptionalFields` adds absent oMLX model/field |
| Present malformed fields/shapes, nonpositive/noninteger values and malformed parameters fail | `sharedHttpCases` invalid-window, invalid-num-ctx, malformed and reject-json-string-object rows; local additional shape/value matrix; all assert `LlmGatewayException` |
| Non-2xx status is an error, all 2xx accepted, transport cause retained | `sharedHttpCases` 401/404/418 and 201 rows; `transportFailuresRetainTypedCause` exercises closed real sockets and retained cause; `redirectsAreHttpErrorsWithoutChangingCompletionClients` prevents a 302 from consuming a sentinel |
| Request-free exact OpenAI registry lookup, unknown model returns null | `registryLookupHasAnActiveZeroRequestObserverAndNegativeControl`: shared registry rows plus live listener; `availableModels` negative control produces an observed request and typed rejection |
| Other gateways return not advertised | `legacyGatewayInheritsNotAdvertised`; common `AnthropicGatewayTest.contextWindowIsNotAdvertisedWithoutRequests` rejects unexpected I/O |
| Pure effective calculation: unknown, caller precedence, model limit, capacity, all clamp branches | `sharedEffectiveCasesAndUnchangedConfigurationDefaults`: all eight shared rows, explicit config overload and unchanged 32,768 default |
| Lookup leaves subsequent ordinary, structured and streaming bytes unchanged | `ContextWindowRequestFidelityTest.discoveryPreservesOrdinaryStructuredAndBothStreamingRequestBytes`: three providers, five public entrypoints, two configs; compares exact body bytes, headers and request lines before/after lookup and verifies completion outcomes |

The existing oMLX completion characterization suite passed before implementing
discovery. The behavioral proof then rejected the inherited null result for the
shared exact-model HTTP case (exit 1) and passed after implementing the real
GET/selection boundary (exit 0). Final tests retain that case.

The artifact's ancillary `lookup_cases` concern existing model-list behavior,
not the new metadata operation. Existing `availableModels` ordering remains
unchanged: Kotlin sorts Ollama names; the artifact's Ollama list row preserves
provider order and supplies only `name`, whereas Kotlin's existing parser
requires `model`. `ancillaryModelListCasesCharacterizeExistingBoundaryDifferences`
executes those three rows, checks the two conforming list results and records
the known Ollama typed rejection. These pre-existing list differences are
retained for initiative coordination; no full-artifact or model-list parity
claim is made.

### Initiative findings: external verification remains unfinished

Kotlin's public metadata adapter is locally verified against the identifiable
Elixir shared cases. Real loopback HTTP tests verify local consumer behavior;
they do not verify a live oMLX/Ollama provider or an external application's use
of the new Kotlin API. Completion reply fixtures reused from existing Kotlin
regressions prove local request stability and successful outcomes only.

Elixir's inspected source identity is recorded above, but its paired verification
was not run in this Kotlin-only task. The external command to coordinate is:

```sh
scripts/verify-context-window-contract
```

Its result and the tested Elixir implementation identity must be supplied by the
Elixir initiative work, against the exact shared artifact hash above. Provider
server identities, live verification commands/results and a paired Kotlin
consumer repository/commit are also unavailable. Coordination must assign those
repositories and run the same versioned cases in their gates. Compatibility
across repositories is unfinished until both sides are verified. No other
repository was modified.

The existing recovery-API gap recorded above remains open: no shared executable
external recovery contract/counterpart has been verified for the typed provider,
operation and capability API changes. This metadata contract does not resolve
that gap, endpoint-specific client-abort evidence, publication prerequisites or
the consumer migrations described in the initiative brief.

### Kotlin executable source identity

The 23-file manifest
[contracts/context_window_v1.kotlin.manifest.sha256](contracts/context_window_v1.kotlin.manifest.sha256)
has SHA-256
`53b64999288898efbb80908dc8acdd978b7d639e5a62a3b17e757106c5dc628d`.
It identifies the new provider implementations, pure parsers/helper, public
API baselines, shared artifact, tests and reused socket/completion fixtures,
plus the unchanged completion configuration, registry, oMLX settings and gate
configuration. `sha256sum -c contracts/context_window_v1.kotlin.manifest.sha256`
passed with exit 0. Together with the base commit above, this identifies the
tested uncommitted source without modifying refs.

### Local verification results

Linux verification on 2026-10-17 used JDK 21 for Gradle, the configured JDK 17
compiler toolchain, a writable isolated Gradle cache and in-process Kotlin
compilation. No toolchain, dependency, plugin, suppression or completion-default
files were changed.

All six `.hone-gates.json` tasks passed in one invocation (exit 0, 2m 43s):

```sh
./gradlew ktlintCheck detekt build allTests apiCheck dokkaGenerate
```

This includes the shared section-1 artifact cases in the ordinary JVM test gate.
The final reports contain 499 JVM and 373 Android host tests, with zero failures,
errors or skips. The metadata contract class has nine passing test methods,
the HTTP proof has one and the 30-scenario completion fidelity matrix has one.
The additional common Anthropic default test passes on both targets.

The explicit applicable build-CI tasks and toolchain check also passed (exit 0,
20s):

```sh
./gradlew javaToolchains jvmJar assembleAndroidMain apiCheck jvmTest \
  testAndroidHostTest --no-daemon
```

The toolchain output confirms both JDK 17 and 21 are installed. Dokka generated
the model value, gateway operation and effective-window pages. Existing Gradle
deprecations, Android analytics permissions, example/realtime warnings and
Dokka cross-module link warnings remain; this is not a warning-free build claim.
No macOS, Android device or hosted CI run was performed.

Initial Detekt throw-count findings were fixed by splitting parser validation,
and the Anthropic inherited-method API baseline was regenerated. No findings
were suppressed. Final `git diff --check` and the behavioral proof JSON/type,
observed nonzero/zero exit and combined-log checks passed. The work remains
uncommitted for Foundry finalization.

The applicable security-CI audit passed (exit 0, 33s):

```sh
./gradlew dependencyCheckAggregate --no-parallel --no-daemon \
  -PdependencyCheckDataDirectory="$GRADLE_USER_HOME/dependency-check-data" \
  '-PdependencyCheckNvdDatafeedUrl=https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz'
```

The NVD modified-feed metadata was checked first and reported
`lastModifiedDate:2026-10-16T18:00:09-04:00`. This changes data transport only.
Dependency-Check 13.0.0 reports 305 dependencies, zero active findings and 52
existing suppressed findings. The generated report SHA-256 is
`dbb0b33b69796a9c9adc5226654054b90287d27e0dff62d76ccadf98dadc3824`.
OSS Index lacked credentials and the .NET analyzer lacked its runtime; the
audit retains those configured analyzer and suppression limits. No suppression,
advisory acceptance or dependency change
was introduced. This configured-audit pass does not establish the absence of
all vulnerabilities. Publication/install checks were not run because publishing
is outside this correction's authorization.

## Native reasoning (release part 2)

Binding source: `REASONING-PROTOTYPE-2026-10.md`, SHA-256
`0a40f3ad45c593bc779411021e45db569e73d3ecce86c99b201e7f8ebb656809`.
The initiative brief was verified against the supplied SHA-256
`ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
Reference source inspected: Rust `8ac38ea134af7816c6fdd02e07a420ed3be1a2f4`
(`llm/reasoning.rs`, `gateway.rs`, broker and provider adapters), following the
contract's prototype reference `67791b7`. Kotlin base tested:
`7ac5f6c27e111e9f5662dae443d9feeb564f2726` plus the uncommitted reasoning
changes identified by `contracts/native_reasoning_v1.kotlin.manifest.sha256`
(manifest SHA-256 `cb8ec8fdc576946095a79fadbc8fd02d79051ea089f8efa381bbcfca637b1385`).
No other repository was changed.

The existing JSON-artifact and loopback HTTP contract mechanism is reused as
**native-reasoning-v1**, at `contracts/native_reasoning_v1.json` (SHA-256
`d5539eb334b69e0f8b698e8cd2c1402772dfabd09af92c652e1d1dc35f34e127`). Its identity is
asserted by `NativeReasoningControlsTest.contractIdentity`; the source/test
manifest identifies the executable cases and implementation. Scripted peers in
`NativeReasoningCases` derive their replies from this artifact. These are Kotlin
consumer verification cases, not independently provider-verified doubles.

| Decision | Named executable tests |
| --- | --- |
| Distinct six controls, exact boolean/string types, omission stays unknown; misleading names never select a profile | `NativeReasoningControlsTest.everyEffortAndOmissionHasExactWireTypeAcrossCompletionModes` (Ollama/oMLX, ordinary/structured/streaming, recovery on/off) |
| Exact Qwen3.8 set; unsupported effort/history rejected before HTTP | `NativeReasoningControlsTest.profilesValidateExactQwenSetAndHistoryBeforeHttpAcrossModes` |
| Native assistant reasoning separate from visible content and tool results | `NativeReasoningProofTest.ordinaryToolContinuationRetainsNativeAssistantAtHttpBoundary`; `NativeReasoningContinuityTest.ordinaryAndStreamedToolsPreserveAssistantAssociationOnlyWhenSelected` |
| Ordinary/streamed completed tools retain one assistant with all calls; exact oMLX IDs remain associated with results | `NativeReasoningContinuityTest.ordinaryAndStreamedToolsPreserveAssistantAssociationOnlyWhenSelected` (two tools, ordinary/streamed, retention on/off, recovery on/off) |
| Session retention reaches the next user message | `NativeReasoningHttpProofTest.completedAssistantReasoningSurvivesToolAndNextSessionTurn`; the continuity matrix checks both session entrypoints |
| oMLX requests `chat_template_kwargs.preserve_thinking` only with retention | Continuity matrix above; `NativeReasoningStructuredTest.structuredBrokerPreservesSeparateNativeHistoryAndTemplateRequest` |
| Structured requests share controls/history; native thinking remains separate in structured responses | Control matrix and structured broker test above |
| OpenAI rejects the oMLX history extension and unsupported new controls | `NativeReasoningControlsTest.openaiRejectsNewControlsAndOmlxHistoryExtensionBeforeHttp` |
| Anthropic explicitly rejects unsupported new controls, keeping legacy effort handling | `NativeReasoningUnsupportedTest.newControlsAndNativeHistoryFailBeforeHttp`; existing Anthropic gateway/recovery tests |
| Partial native delivery cannot replay or enter history, even with an admission hook; retries before delivery retain only completed native history | `NativeReasoningRetryTest.partialNativeStreamCannotBeRetriedOrRetainedEvenWithAdmission`; `admittedRetryBeforeDeliveryKeepsCompletedNativeHistoryAndToolCounts` |
| Cancellation never stores native deltas or executes pending tools | `NativeReasoningFailureTest.cancellationOnNativeDeltaRollsBackSessionAndNeverExecutesTools` |
| Failed/unfinished streams never become completed history | `NativeReasoningFailureTest.incompleteNativeStreamAndProviderFailureNeverBecomeCompletedHistory`; `unfinishedOrdinaryNativeResponsesNeverReachTools` |
| Failure after tools rolls back the session without replaying executed tools | `NativeReasoningFailureTest.failedOrdinaryContinuationRollsBackNativeHistoryWithoutReplayingTools` |
| Existing recovery admission, identities, tool counts, recursion, context discovery and legacy provider behavior | Existing full suites, including `CompletionRecoveryHttpTest`, `StreamingRecoveryHttpTest`, `AdmissionAmendmentHttpTest`, `ContextWindowContractTest`, broker/session and OpenAI/Anthropic tests |

Before changing gateway behavior, inspection and the rejecting HTTP probe
established that Ollama collapsed named efforts to boolean true and broker
continuations substituted assistant stubs without native reasoning. A separate
original-source characterization executes Low/Medium/High and omission against
both providers at the base revision, checking exact fields and the legacy
assistant/tool/assistant/tool layout. The new continuity matrix also keeps that
layout when retention is disabled. Existing recovery tests change only their
expectations for Ollama structured `thinking`, which is now preserved; OpenAI
structured behavior stays unchanged. Changing Ollama named efforts and opting into
native transcripts are licensed reasoning changes; sampling defaults, output
limits, request fidelity and first-token timing are outside this correction.

The behavioral proof exercises the actual HTTP client against loopback sockets:
executing the original committed broker, session and Ollama adapter produces the missing native sentinel;
correcting the source preserves it with its tool call and visible content, then
preserves the completed transcript on the next session turn. Recovery behavior
is unchanged; no new provider cancellation/status or admission guarantee is
claimed. oMLX server-forced template settings can override the retention request.

### Counterpart verification finding

**Open initiative gap:** no provider repository or other port has verified
`native-reasoning-v1` at an identified commit. No provider verification command
has been run or claimed, and no live model/server conformance or benchmark is
claimed. The Rust source is a behavioral reference, not paired verification of
this artifact. Coordinate provider-side execution of these exact versioned
cases and record provider commits, commands and results before declaring paired
boundary conformance. Other repository changes remain outside this task.

### Local reasoning verification

Commands use a writable copied Gradle cache (`GRADLE_USER_HOME`), the installed
Android SDK (`ANDROID_HOME`), writable `ANDROID_USER_HOME`, and
`-Pkotlin.compiler.execution.strategy=in-process` because the host Gradle/Kotlin
cache locations are read-only in the task sandbox. These are execution settings,
not dependency or build-policy changes. Required gates and applicable JVM/Android
CI tasks are recorded below after execution.

Final local gate run, 2026-10-17 (each command includes the common execution
flags described above):

| Required command | Exit | Result |
| --- | --- | --- |
| `./gradlew ktlintCheck` | 0 | Pass |
| `./gradlew detekt` | 0 | Pass; no new suppressions |
| `./gradlew build` | 0 | Pass |
| `./gradlew allTests` | 0 | Pass |
| `./gradlew apiCheck` | 0 | Pass against the updated 3.0 API snapshot |
| `./gradlew dokkaGenerate` | 0 | Pass |

Local CI-equivalent verification also passed (exit 0):
`./gradlew javaToolchains jvmJar assembleAndroidMain jvmTest testAndroidHostTest apiCheck --no-daemon --stacktrace`.
Gradle detects both JDK 17 and JDK 21. The full run contains **514 JVM tests and
375 Android host tests**, with zero failures, errors or skipped tests. No hosted
GitHub Actions run or device/emulator test is claimed. Existing Gradle deprecation
messages remain; no new compiler or static-analysis warning is accepted.

Baseline characterization passed (exit 0) by temporarily executing original
committed gateway/broker/session source with a Low/Medium/High/omission HTTP
characterization harness, then restoring the corrected files. The exact local
wrapper command is archived in execution evidence. The final
ordinary/session HTTP proof executes
`./gradlew :mojentic-openai:jvmTest --tests '*NativeReasoningHttpProofTest*'`:
original committed source rejects the native sentinel (exit 1); corrected source
preserves its exact assistant/tool association and the next session turn (exit 0).
The proof's actual commands, exit codes and complete logs are retained as Foundry
execution evidence. The main source manifest and contract artifact are normal
repository files; execution logs are scratch and are not release artifacts.

The applicable CI dependency audit passed (exit 0):
`./gradlew dependencyCheckAggregate --no-parallel -PdependencyCheckDataDirectory=/tmp/mojentic-reasoning-audit-data`.
The writable audit database was copied from the host cache; no analysis
configuration, dependency, allowlist or suppression file was changed. The JSON
report scans 305 dependencies and reports zero unsuppressed vulnerabilities;
52 suppression entries remain under the repository's pre-existing policy. This
run adds no suppression and does not claim those inherited entries were newly
revalidated. NVD bulk-feed `modified.meta` was checked before the audit and
reported `lastModifiedDate: 2026-10-16T18:00:09-04:00`; feed transport retains the
existing audit scope and CVSS threshold. No fresh Maven publication was made.

## Request fidelity correction — MODEL-METADATA section 3 (2026-10-17)

This section supersedes earlier statements that completion numeric defaults stay
unchanged. Scope is request fidelity only; context discovery and native reasoning
retain their existing contracts, and first-token timing/recovery amendments are
separate initiative work. The verified brief hash is
`ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
The binding MODEL-METADATA document hash is
`a085da1c0d110f9d587a24ace6ac2854b653e1ac28905b3829d94d665b2c906e`.

### Executable identity and scope

Reuse the unchanged Elixir `request-fidelity-v1` artifact as
`contracts/request_fidelity_v1.json`, SHA-256
`6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea`.
Both Kotlin contract classes verify this identity. Inputs, retryable 503 and
response content/terminal semantics come from that artifact. Ollama responses
add the assistant role required by Kotlin's existing decoder; Anthropic projects
shared answer content into its native text/tool/SSE envelope. These local HTTP
test doubles derive from the artifact; they are not evidence of external
provider agreement. Existing recovery boundary tests still exercise errors,
cancellation, admission, partial delivery and payload freezing.

The shared artifact declares unreleased 3.0.0 and explicit migration from 2.1
numeric defaults, rather than retaining the old binary ABI. Kotlin's correction
covers temperature/context/output fields; `top_p` and `top_k` have no Kotlin
configuration fields and their shared cases only exercise the supported subset.
Adding sampling fields is outside this objective; full cross-port artifact
coverage remains an initiative finding. No supported old wire protocol is
removed: ordinary JSON, native structured output, SSE and Ollama NDJSON remain
covered. The public numeric config/trace ABI intentionally breaks for 3.0.

| Contract decision | Named tests |
| --- | --- |
| Outgoing absence through ordinary, structured, structured-response, stream and event entrypoints | `ContextWindowRequestFidelityTest.unsetSamplingAndLimitsAreAbsentThroughEveryEntrypoint` (Ollama/OpenAI/oMLX) |
| Exact explicit values including zero; one provider output field; unchanged retry bytes | `RequestFidelityContractTest.ollamaOrdinaryStructuredAndStreamingPreserveExplicitAndUnsetFieldsAndRetryBytes`, `openaiOrdinaryStructuredAndStreamingPreserveExplicitAndUnsetFieldsAndRetryBytes`, `omlxOrdinaryStructuredAndStreamingPreserveExplicitAndUnsetFieldsAndRetryBytes` |
| Anthropic absence, explicit values, forced tool schema, streaming and retry bytes | Anthropic `RequestFidelityContractTest.anthropicOrdinaryStructuredAndStreamingPreserveFieldsAndIdenticalRetryPayloads` |
| Anthropic known exact IDs/aliases, unknown fallback, explicit override | Anthropic `RequestFidelityContractTest.knownRegistryIdsAndAliasesUseMaximumOutputAndUnknownNamesUse4096` |
| OpenAI explicit fixed-temperature normalization and output selection | `RequestFidelityContractTest.fixedTemperatureIsNormalizedOnlyWhenSetAndReasoningUsesOneOutputField`; `OpenAIRecoveryPayloadTest.supportedPayloadIsFrozenBeforeAdmissionAcrossAllEntrypoints` |
| Warn and drop unsupported effort | `RequestFidelityContractTest.droppedEffortWarnsAndIsAbsentOnTheWire` |
| Recovery/profile/retention/format/tool limits survive copies | `CompletionConfigTest.depthCopyPreservesRecoveryReasoningRetentionAndUnsetSampling`; existing broker/session native-reasoning continuity and recovery tests |
| Metadata lookup leaves request bytes unchanged; effective-window precedence | `ContextWindowRequestFidelityTest.discoveryPreservesOrdinaryStructuredAndBothStreamingRequestBytes`; `ContextWindowContractTest` (unset config now follows model parameter/capacity) |

The initial real-HTTP rejection observed an unset Ollama ordinary request carrying
`temperature: 1.0`, `num_ctx: 32768`, `num_predict: 16384`. The identical named
absence probe then passed after changing nullable config defaults and adapter
mapping, before expanding the contract fixtures/docs. Execution evidence retains
both actual exit codes and complete logs. Recovery serialization continues to
freeze each request before admission and reuses identical bytes on retry.

### Local and paired verification

Tested Kotlin base: `436c334d63c5477dbf9dda59ba1b66b58319124a` plus this
uncommitted worktree, identified by
`contracts/request_fidelity_v1.kotlin.manifest.sha256`. No commit or ref changes
are made. Artifact source observed at Elixir
`df29a1c36f6b4b172b3ff352d40201d6583a9890`; that observation is not a test run.

Local command:
`./gradlew :mojentic-openai:jvmTest :mojentic-anthropic:jvmTest --tests '*RequestFidelityContractTest*' --tests '*ContextWindowRequestFidelityTest*' --tests '*OpenAIRecoveryPayloadTest*'`.
Execution uses writable `GRADLE_USER_HOME=/tmp/mojentic-reasoning-gradle`,
`ANDROID_HOME=/home/svetzal/Android/Sdk`, `ANDROID_USER_HOME=/tmp/mojentic-android`,
`-Pkotlin.compiler.execution.strategy=in-process --offline --console=plain`.
These settings accommodate read-only host caches without changing dependencies.

**Open initiative finding — paired verification unavailable:** no external
provider or downstream consumer repository was authorized for changes or tested
in this task. Elixir's counterpart command is
`scripts/verify-request-fidelity-contract`; it was not run here, so no counterpart
result or tested counterpart commit is claimed. Provider owners must execute the
same versioned cases, including the Anthropic projection, and record commands,
commits and results. This local Kotlin verification cannot close paired boundary
conformance, cross-port sampling coverage, live-provider behavior or coordinated
release readiness. No sibling repository was changed.

Final local verification, 2026-10-17, on the Kotlin base plus worktree above:

| Discovered `.hone-gates.json` command | Exit | Result |
| --- | --- | --- |
| `./gradlew ktlintCheck` | 0 | Pass |
| `./gradlew detekt` | 0 | Pass, including all configured platform/test analysis |
| `./gradlew build` | 0 | Pass, including examples and Android host checks |
| `./gradlew allTests` | 0 | Pass |
| `./gradlew apiCheck` | 0 | Pass against regenerated intentional 3.0 ABI snapshot |
| `./gradlew dokkaGenerate` | 0 | Pass |

The targeted local contract command above passed (exit 0); the final full gates
include those same classes. Final test reports contain **525 JVM tests and 376
Android host tests**, zero failures, errors or skips. KMP `allTests` and Dokka v2
`dokkaGenerate` are this repository's test/documentation gates; a JaCoCo coverage
report is not wired, so no coverage percentage is claimed. Existing Gradle
10 deprecation messages, Android analytics cache notices, a redundant `else`
compiler warning in the unchanged realtime gateway, and unresolved Dokka links in
unchanged declarations remain visible. No new suppression or baseline was added.

The tested source/API/test manifest SHA-256 is
`f777e42d3650c79e0ec98da7af6e5ec538cdb10c1e6b30e25137fab12848c181`;
`sha256sum -c contracts/request_fidelity_v1.kotlin.manifest.sha256` passes.
The unchanged shared JSON artifact and both contract identity tests preserve the
version/hash independently from this evolving Kotlin source manifest.

Additional requested security verification passed (exit 0):
`./gradlew dependencyCheckAggregate --no-parallel -PdependencyCheckDataDirectory=/tmp/mojentic-reasoning-audit-data`
with the same writable environment/compiler/offline settings. Before execution,
NVD `modified.meta` reported `lastModifiedDate:2026-10-16T18:00:09-04:00`.
The existing feed transport, complete audit configuration and severity threshold
were retained. No dependency, allowlist or suppression file was changed. The
report's unsuppressed findings are zero; inherited suppressions were not newly
revalidated. Audit output notes OSS Index lacks credentials and the .NET assembly
analyzer lacks a runtime, so this is a passing configured audit rather than a
claim of exhaustive vulnerability detection. No Maven publication was performed.

## Client streaming timing — MODEL-METADATA section 2 (2026-10-17)

This section supersedes earlier statements that first-token timing remains
pending. It implements section 2 and release part 3 only; it does not close
unrelated recovery amendments or authorize publication. The verified initiative
brief / RELEASE-3.0.0 SHA-256 is
`ea6a0efa56bfb3cd74b5ce728f80cc33c6fbd853114bec94b8aabe79f5d28e6e`.
The owner-approved MODEL-METADATA document, including the October 17 dispatch,
retry and continuation decisions, has SHA-256
`d9945dca45ce09e2163843263a75b5e93028b00ee1baf191fc3ff13396830e9c`.
The initiative repository was observed read-only at
`ef9339c6a9682ed5f060bd690b738f6b8eea1d2d`.

### Executable scope and behavioral reference

The local executable set is **kt-stream-first-token-v1**:
`ClientStreamTimingHttpTest`, `ClientStreamTimingTest` and
`FirstDispatchTimingProofTest`. HTTP response scripts derive from section 2 and
the read-only **ts-stream-first-token-v1** behavioral reference. The frozen
`mojentic-ex/test/contracts/reference/ts-stream-first-token-v1.test.ts` snapshot
has SHA-256 `e94d4f53928b3ea519eceedcb0f7e7a41ae2eb5da52076369d92374108b3d2a8`.
Anthropic adds its required message/block lifecycle; Ollama projects its argument
objects and required assistant role. The tests exercise public gateway,
and broker APIs through production adapters; JVM
cases use actual loopback HTTP sockets, while common tests control byte-channel
arrival times through Ktor's production adapter with MockEngine.

The owner authorized using this TypeScript set as a read-only behavioral
reference without counterpart execution or manifest evidence. No TypeScript or
external producer execution is claimed or required for this local telemetry
correction. No provider wire protocol is changed. Live provider compatibility and
paired verification gaps recorded for earlier boundary changes remain separate
initiative work; this section does not close them.

### Section-2 mapping

Class names below are `ClientStreamTimingHttpTest` unless qualified.

| Binding behavior | Named consumer-perspective verification |
| --- | --- |
| Start at first dispatch; exclude delayed collection | `FirstDispatchTimingProofTest.delayedCollectionRetryAndMixedFrameUseOneDispatchClock` checks zero pre-collection requests, then exact 77 ms; `ClientStreamTimingTest.delayedCollectionAndAdmittedRetryKeepFirstDispatchClockWithThinkingPrecedence` checks exact 60 ms |
| Success evidence for OpenAI, Anthropic, Ollama and oMLX, ordinary and recovery | `publicStreamsMeasureExactKindsAndKeepProviderUnitsOnOrdinaryAndRecoveryPaths` |
| Thinking/content/tool precedence independent of field order | The first-dispatch probes place content before thinking; `failuresBeforeTokensAndArgumentFragmentsRetainExactTerminalEvidence` places content and tool arguments in the same frame and checks exact kinds |
| Empty/keepalive frames do not count; no-token streams omit both first-token fields and still report duration | `noTokenSuccessIgnoresKeepaliveAndEmptyFramesOnBothPublicApis` asserts absent pairs and exact 17 ms through every supported public stream API |
| Tool arguments count before completed calls; names and empty arguments do not | `ClientStreamTimingTest.argumentFragmentStartsClockBeforeCompletedToolOnBothPaths` sends names/empty arguments at 15 ms, first arguments at 25 ms, completed calls at 45 ms; checks tool_call / 25 / 45 |
| Failure before and after tokens, including argument rejection | `failuresBeforeTokensAndArgumentFragmentsRetainExactTerminalEvidence` checks HTTP 400, interrupted bodies and rejected tools, exact 31 ms and token kind on both paths |
| First-token time differs from failed request duration | `ClientStreamTimingTest.terminalFailureAfterContentKeepsFirstTokenTimeAndFullDuration` checks content / 25 / 45 on ordinary and recovery paths |
| Capture may interrupt before parsing/delivery, but received arguments still count | `recoveryCaptureFailureRetainsArgumentTimingBeforeParserOrDelivery` checks tool_call / 31 / 31, zero delivery, zero admissions and one HTTP request |
| Retain the first clock across admitted retry and admission/backoff delays | `realHttpRetryIncludesAdmittedDelayAndNewCollectionStartsFreshClock` checks 10 + 7 admission + 40 backoff + 20 response = 77 ms and byte-identical retry payloads for all providers |
| Final recovery failure includes pending admission time through existing private inspection | `terminalFailureDurationIncludesPendingAdmissionWithoutRestartOrReplay` checks 17 ms in Error.evidence, inspectCompletionEvidence() and the terminal EXHAUSTED observer, with no resend |
| Tool execution and prior completion time do not enter continuation clocks | `brokerToolContinuationStartsIndependentClockAfterToolExecution` checks tool_call / 23 / 23, one tool execution with 500 ms caller work, then content / 11 / 11 for all four providers and both paths |
| Provider metrics keep original units | Success matrix preserves oMLX usage values 0.7, 0.2, 0.5 and 0.9 seconds and Ollama total_duration 900 nanoseconds beside client milliseconds |
| Timing configuration preserves wire bytes/defaults | `timingConfigurationPreservesRawRequestBytesAndDefaults` compares actual raw request bytes and header values across timing-source configuration changes |
| Existing streaming order, cancellation, reasoning retention and retry admission stay intact | Existing delivery-before-EOF, native reasoning, recovery/admission and request-fidelity suites run in the full gates. Older provider-evidence assertions exclude only the three new client fields; exact timing is asserted in the named executable set above |

### Public API and migration

`GatewayStreamEvent.Completed` and broker/session `StreamEvent.Completed` expose
one terminal evidence value per successful completion, before any subsequent tool
continuation. `CompletionStreamEvent.Error.evidence` covers every dispatched
failure. Ordinary lower-level stream failures retain their original cause in
`LlmGatewayException` and expose `completionEvidence`; recovery keeps its typed
exceptions and `RecoveryFailure.inspectCompletionEvidence()` path. Evidence is
an immutable value; recovery's private evidence holder safely finalizes the last
failure's duration before terminal observation and when the logical operation
ends. Timing state is passed explicitly through adapter calls and a new
RecoveryHttp.executeStream overload; the original overload remains supported.
No coroutine context, dispatcher, job or buffer is added. Recovery retains its
own collector-cancellation handling; the paused-tool cancellation regression
keeps every original assertion. Cancellation remains cancellation. Existing permissive legacy EOF handling is unchanged.

Recompile consumers, handle the new Completed cases in exhaustive event `when`
expressions, and account for the extra terminal event per lower-level completion.
Existing custom gateways can still omit that event. `CompletionConfig` adds
`streamTimeSource` for ordinary streams; recovery uses its existing policy
`monotonicClock`. Both default clocks are monotonic. No request field, default,
reasoning control or retry-admission decision was changed. The only dependency
addition is the Anthropic project on the OpenAI JVM test classpath, to exercise
all four adapters in one socket contract matrix; published dependencies do not
change.

### Tested source and verification

Kotlin base: `76e531bbfcae97c491be4a8c73608ad02ec42440` plus the uncommitted
candidate described here. The baseline real-HTTP probe used an archive of that
commit with the same consumer test/server, without changing any refs. It failed
behaviorally: expected 77 ms, observed absent timing. The initial controlled Ktor
probe likewise failed (expected 60 ms, observed absent timing) before production
changes, then passed with exact timing. Execution evidence retains the actual
nonzero and zero exits and complete logs. No sibling checkout was modified.

The final code/API/test identity is
`29e86aa08698b48f3704882643d7a38e49bb4d59ada178ceada4e6ccf520905a`.
It is SHA-256 over UTF-8 `sha256sum` records, sorted by repository-relative path,
for changed/new Kotlin, Gradle Kotlin DSL and API files together with
`.hone-gates.json` (33 files total). The unchanged base supplies the remaining files. Earlier
implementation manifests in this record are historical snapshots; the unchanged
shared JSON contract artifacts and their tests remain in the full gates.

| Executable source in kt-stream-first-token-v1 | SHA-256 |
| --- | --- |
| `ClientStreamTimingTest.kt` | `61162e4e8abdbc0c7c5911f97434778df6fb669ca271adccee15cc58f71993c6` |
| `ClientStreamTimingHttpTest.kt` | `0f54f8651654b4bf9a4e27fcb8ce529d1df800f24925dab9df29e8da15672241` |
| `FirstDispatchTimingProofTest.kt` | `78617ce25fe9ebe312186392e97be2d9898a0e06a575123fb50be4ea53140642` |

The baseline and corrected socket proof use byte-identical consumer test and
server source. The baseline exits 1 for absent timing; the corrected executable
set exits 0. The targeted command was:

```bash
./gradlew ktlintFormat detekt \
  :mojentic-anthropic:jvmTest --tests '*cancellationWinsInRequestAdmissionBackoffAndPausedToolDelivery*' \
  :mojentic-openai:jvmTest --tests '*ClientStreamTiming*' --tests '*FirstDispatchTimingProof*' --offline
```

This passes all 12 timing tests and the unchanged cancellation test's five
phases. The full gates then run every suite without test filters. All commands
use `foundry capture` for execution logs, writable
`GRADLE_USER_HOME=/tmp/mojentic-kt-timing-gradle`,
`ANDROID_HOME=/home/svetzal/Android/Sdk` and
`ANDROID_USER_HOME=/tmp/mojentic-kt-timing-android`. The writable Gradle home has
`kotlin.compiler.execution.strategy=in-process` to avoid read-only host daemon
state. Repository dependencies, SDK targets and compiler settings are unchanged.

Final `.hone-gates.json` verification on this candidate, 2026-10-17:

| Command | Exit | Captured stdout SHA-256 |
| --- | --- | --- |

| `./gradlew ktlintCheck` | 0 | `f023d7c93c6a10ff6c56be11c6011f2d2b5f8c61f6c0fd56c222828b309bc075` |
| `./gradlew detekt` | 0 | `6ae7b70f86a6975e77a057813aebdeb369f2a945fd6a23d7a862604cc7c4de14` |
| `./gradlew build` | 0 | `09548b743849ce82f705c921fa4564282b6db3f9f7afb2b5552f6422609451f1` |
| `./gradlew allTests` | 0 | `cd3897fa6d879140ea8a7b8831cf1f537d603f82e6b48c3345fde7c388253c56` |
| `./gradlew apiCheck` | 0 | `245e87d6d7efbc3157d9bb04d4d1573c202b84317d72a57145d05b87a01d5fd4` |
| `./gradlew dokkaGenerate` | 0 | `5efad233e0d1f695a3e2ff5bf87efb7ec1e5bfee557a66d5069b7cdfc9c85e37` |

Final reports contain **537 JVM tests in 99 suites and 379 Android host tests in
66 suites**, with zero failures, errors or skips. API checks pass against the
regenerated intentional 3.0 snapshot. Existing Gradle deprecation, realtime
compiler and unresolved Dokka link notices remain visible; no new suppression
or baseline was added. Coverage reporting is not wired, so no coverage percentage
is claimed.

The dependency audit also passes (exit 0):

```bash
./gradlew dependencyCheckAggregate --no-parallel \
  -PdependencyCheckDataDirectory=/tmp/mojentic-kt-timing-gradle/dependency-check-data --offline
```

NVD `modified.meta` was checked first and reported
`lastModifiedDate:2026-10-16T18:00:09-04:00`. The report scans 304 dependencies and
has zero unsuppressed vulnerabilities. Existing audit scope, CVSS threshold and
suppression file are unchanged; inherited suppressions were not newly
revalidated. The audit reports the existing unavailable .NET assembly analyzer.
No external dependency version, publication, repository ref or sibling checkout
was changed. Foundry retains execution evidence; all changes remain uncommitted.

Dependency-audit stdout SHA-256: `11dbdeefe851a5f32fcee3c256664557c8212ce22a4ff582db7b090e8dbe76a6`.
