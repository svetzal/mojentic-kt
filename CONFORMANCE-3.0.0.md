# 3.0.0 admission conformance

This record covers the October 12 admission guarantees only. It does not certify
all of transient recovery or readiness to release 3.0.0. JVM and Android are the
supported targets. Provider wire formats and dependencies are unchanged.

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

The local executable admission cases are versioned here as **kt-admission-v1**:

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
neither infers NO from status/closure nor introduces a cancellation/status API.
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
- Reasoning, metadata, capability citations, typed operation/provider vocabulary,
  policy validation and remaining recovery amendments belong to separate scope.
- Other ports and consumer migrations are not verified here. No version bump,
  release, tag or publication is authorized by this correction.
