# 3.0.0 recovery and model metadata conformance

This record covers the October 12 admission, invalid-policy and completion-adapter
Capabilities and Operation/provider guarantees.
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
