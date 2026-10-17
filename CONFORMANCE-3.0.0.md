# 3.0.0 recovery conformance

This record covers the October 12 admission and invalid-policy guarantees only.
It does not certify all of transient recovery or readiness to release 3.0.0. JVM and Android are the
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
