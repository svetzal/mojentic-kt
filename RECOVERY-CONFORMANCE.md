# Completion recovery conformance — bounded Kotlin slice

This implements ordinary and structured recovery through public Ollama/oMLX
adapters, including the broker and session call paths. It does not claim whole
mission alignment. Intent references are `TRANSIENT-RECOVERY-2026-10.md` and
`RECOVERY-REQUEST-2026-10.txt`, plus the October 10 supplement supplied in the
Foundry task. Neither checked-in intent file contains that supplement; its added
constraints are permanent truncated status, pre-capture semantic accounting,
private causes/metadata and cancellation precedence. The requested Rust comparator
is exactly `4ca1ed279c02eab37827a1ed07c30e961155ecf3`; this worktree has not fetched,
inspected or changed the Rust implementation or any harness pin.

Baseline Kotlin revision: `e313f32030b0613dc954912b1b4920493f3b45e6`.
Working-source SHA-256 hashes are recorded in `.foundry/recovery-source-hashes.json`.
They describe the reviewed dirty implementation, not a released revision.

## Assertion-backed acceptance

The matrix in `CompletionRecoveryHttpTest` runs every ordinary/structured case
against both public adapters using their default real JVM HTTP engine and a
scripted loopback HTTP/1.1 server. No LlmGateway mocks or private retry helper tests
are used. The earlier `OllamaRecoveryProofTest` establishes rejection and correction
of truncated permanent HTTP status handling before fixture/document expansion.

| Acceptance | Test and assertions |
| --- | --- |
| 503 recovery | `recover503PreservesExactPayloadIdentityAndLifecycle`: identical request bytes across wires and capture, stable logical IDs, distinct attempt IDs, one-based numbers, terminal status captures, reasoning/usage/finish compatibility, complete event order |
| 504 exhaustion | `exhaustionRetainsEveryAttemptAndTypedCause`: exactly three requests, complete failure list, numeric statuses, private bytes/headers, original typed cause identity, safe formatting |
| Retry-After | `retryAfterSecondsDatesInvalidAndMinimumRefusal`: seconds, future/past dates, invalid/negative values, exact injected delays, overflow minimum refusal |
| Admission | `admissionWaitsForExplicitDecisionAndRejectPreventsResend`: pending means one request and incomplete operation; explicit allow makes second request; reject/missing hook prevents it |
| Cancellation | `cancellationWinsDuringActiveRequestAdmissionAndBackoff`: one actual request and failed event, one terminal cancellation, accessible history, no subsequent success/send |
| Permanent truncated/partial bodies | `permanentTruncationAndPartialSuccessNeverReplay`: 400/401/403 remain permanent despite configured status selection and transport retry; partial successful body also blocks replay; bytes/status/typed cause retained |
| Malformed/capture failure | `malformedAndCaptureFailuresAreTerminalWithPrivateCauseIdentity`: one request for malformed body; zero requests for pre-send capture failure; one for response-capture failure; original hook cause retained privately |
| Recovery budget/deadline | `budgetsDoNotTimeoutHealthyGenerationAndPreventLateResend`, `insufficientBudgetRefusesProviderMinimum`, `deadlineIsRecheckedAfterRequestCaptureBeforeResend`: first-failure budget excludes prior generation; admitted generation may finish late; provider-minimum and final pre-send deadline checks refuse recovery |
| Jitter/redirects | `fullJitterCeilingsSaturateAndRedirectsStaySingleRequest`: exact exponential ceilings, capped and near-Long-maximum arithmetic, injected half-ceiling jitter, one wire for redirect |
| Pre-capture semantics | `captureAccountsForSemanticFieldsBeforeHookFailure`: content, reasoning, tool fragments and whitespace raw bytes observed before throwing capture; no delivered output or resend |
| Observation | `slowFailureObserverConsumesBudgetAndSuccessObserverFailureCountsWire`: observer time consumes recovery budget; observer failure retains typed cause and actual wire count without replay |
| Disabled compatibility | `retriesDisabledPreserveSuccessAndLegacyFailure`: unchanged successful fields and legacy exception, one attempt by default in both legacy and safe-report modes |
| Broker/session/tools | `brokerStructuredAndSessionToolCompletionKeepDepthAndExecuteOnce`: public broker structured schema path, session recovery, unchanged tool-result payload, tool executes once during recovered completion; a newly requested tool reaches existing depth guard; `completedToolIsNotReplayedWhenNextCompletionExhausts` proves exactly one tool execution despite three failed completion attempts |

See [migration examples](docs/use-cases/completion-recovery.md). Non-streaming
observed fields are tracked before capture, while delivered semantic output remains
false until the completed result is returned. Incomplete JSON prefixes are
conservative semantic evidence; any partial successful body vetoes replay even
without a recognizable output field. Raw whitespace is not semantic output.
Arbitrary provider code/request-ID strings are withheld from safe summaries and their public optional fields remain null; unfiltered evidence is retained for explicit inspection. Validated provider metadata extraction remains a later gap. Serializable `failure.summary()` contains only safe typed evidence. Unfiltered headers,
partial bytes and original exceptions require explicit inspection.

## Provider capabilities and remaining mission gaps

| Adapter/operation | This slice | Ownership/termination/idempotency |
| --- | --- | --- |
| Ollama ordinary/structured | opt-in request recovery | unknown; caller admission required |
| oMLX ordinary/structured | opt-in request recovery | unknown; caller admission required |
| Ollama/oMLX streaming (`stream`, `streamEvents`) | existing paths preserved; recovery pending | no added termination proof |
| OpenAI/Anthropic completion/streaming | existing behavior preserved; recovery pending | not investigated here |
| Embeddings, realtime, model management | existing behavior preserved; outside completion recovery | not investigated here |

JVM default OkHttp retries and redirects are disabled; supplied engines must be
configured by their owner. Android uses the same client configuration, but only
Linux host checks are available here. Darwin conformance and Apple simulator
execution require controller validation. The existing Apple workflow
`.github/workflows/build.yml` builds/tests core on macOS; its presence is not proof
that this dirty change passed Apple CI. No new native/Apple success is claimed.
Linux compiles iOS sources/metadata, but framework linking and simulator execution are skipped; those exclusions remain visible in complete build logs. There is no
provider-specific ownership/termination evidence implemented here, no whole-agent
recovery, no tool replay, no streaming continuation, no sibling-port or harness
integration, and no live-model efficacy or infrastructure-origin claim. Native
reasoning history is limited to the existing `LlmMessage` representation; this
slice adds no reasoning-history fields. Existing Ollama structured success still
leaves `thinking` unset, and existing ordinary finish handling is unchanged.

## Gates and provenance

Run the actual KMP gates: `./gradlew ktlintCheck detekt build allTests apiCheck
 dokkaGenerate`. KMP `allTests`
covers configured JVM/Android/native tests rather than generic JVM `test`;
`dokkaGenerate` is the configured Dokka v2 replacement for `dokkaHtml`. No Jacoco
coverage task is configured and no coverage tooling or threshold changes are made.
The security gate is the unfiltered `dependencyCheckAggregate --no-parallel`,
rather than a narrowed JVM `dependencyCheckAnalyze`. Publishing metadata is checked
with `publishToMavenLocal` and POM/module inspection. Sandbox caches and Maven local
output use `/tmp` so host caches and `~/.m2` are not modified.

Early behavioral proof is `.foundry/proof.json`, with actual rejecting/corrected
exit codes and complete logs. Complete subsequent fixture/gate logs are under
`.foundry/logs/`. Gate outcomes and the independent-review record are finalized
there after validation. API snapshot changes are restricted to intentional
additions; legacy CompletionConfig constructor/copy descriptors are retained.
Foundry owns finalization: this worktree remains dirty, with no ref modifications,
commit, push, tag, branch, PR or release.

Final Linux validation: the combined six gates passed (544 tasks), including 16
HTTP matrix tests with zero failures. The unfiltered audit scanned 335 entries and
reported zero unsuppressed findings. Complete outcomes are in
`.foundry/validation.json`; consumer coordinates and metadata hashes are in
`.foundry/logs/metadata-inspection.json`. Existing unrelated Dokka link and Gradle
deprecation warnings remain visible in the logs. Local publication uses a temporary
signing key only to exercise the configured signing tasks; no release is published.
Native metadata dependencies match audited JVM family versions, while exact Native
binary analysis remains pending controller validation.
