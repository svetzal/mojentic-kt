# OpenAI recovery expansion — October 10 correction

The current source change starts from delivered Kotlin
`a1cd3e9515e9282c53ec9e9ec2ccb8a5d0263d44`. A read-only `git ls-remote`
confirmed origin/main at that same revision. Foundry prohibits ref mutation, so
fetch/pull/rebase and Git finalization were not performed. The checked-in contract,
request and October 10 requirements in the correction plan govern this slice;
no separate supplement file was present. Package versions and release guidance
are preserved. No sibling or harness files are edited.

The current behavioral proof is [.foundry/proof.json](.foundry/proof.json).
`OpenAIRecoveryProofTest.escapedSemanticOutputInterruptsAtRealOpenAIBoundary`
rejected unchanged OpenAI source (exit 1): escaped UTF-8 content followed by EOF
completed successfully instead of throwing. The corrected public gateway probe
passed (exit 0). Its loopback uses the default production client and distinguishable
queued success. It asserts exactly `private-é`, one wire request, no admission,
exact observed/delivered UTF-8 counts, exact request/response capture equality,
original typed EOF cause, matching capture/failure identity, single-failure history
and terminal `INTERRUPTED`. Complete proof logs are retained.

OpenAI opt-in ordinary/structured requests now encode once before `RecoveryHttp`.
Both streaming APIs use the same strict OpenAI-compatible recovery parser as oMLX;
that parser moved with oMLX behavior preserved and OpenAI-only private telemetry retention. Retries-disabled OpenAI paths still
use their distinct legacy/event parsers, including legacy malformed-chunk skipping,
reasoning/tool aggregation and event-stream no-tools/usage behavior. Opt-in structured
responses retain available reasoning; legacy structured reasoning stays absent.
No ordinary finish handling, dependency, provider capability or package version
changes are made.

The existing `CompletionRecoveryHttpTest`, `RecoveryTransportHttpTest` and
`StreamingRecoveryHttpTest` matrices now include `Provider.OPENAI`. Their public
HTTP assertions cover 503 recovery, exact frozen payload/capture bytes, logical
and attempt IDs, one-based attempt numbers, complete lifecycle/history, bounded
504, Retry-After seconds/dates/invalid/minimum/budget refusal, pending/rejected
admission, request/admission/backoff/paused-consumer cancellation, truncated
400/401/403, malformed replies, capture cause identity and zero delivery, separate
reasoning/content/tool interruptions, escaped keys and split reads, keepalive,
terminal telemetry and safe summaries against credential/payload echoes.
Broker structured generation and session ordinary/streaming tool rounds use the
same public gateway: a completed tool followed by exhausted completion executes
exactly once, retains retry payload bytes and restores session history on failure.
`OpenAIRecoveryPayloadTest` additionally asserts golden UTF-8 bytes for chat and
reasoning models across all four entrypoints, despite message-list mutation during
admission, plus exact terminal telemetry and capture equality.
`OpenAIRecoveryTelemetryTest` asserts length + DONE and EOF retain exact usage,
provider model and finish reason through the broker tracer without replacing the
original typed cause or recovery history. It also covers cancellation from terminal
usage through stream, streamEvents and broker event paths, with zero output,
original cancellation cause identity and owned socket closure. The rejecting test
exposed wrapped cancellation being caught into an Error event; opt-in gateway and
broker catches now rethrow cancellation.

Read-only comparison at Rust exactly
`4ca1ed279c02eab37827a1ed07c30e961155ecf3` confirms engine interruption on
either observed or delivered semantics, cancellable admission/backoff, cancellation
checks around capture/terminal success, and a dedicated client with hidden retries
and redirects disabled. Exact `adapter.rs`, `engine.rs` and `frames.rs` snapshots
are retained in `.foundry/logs/`. Kotlin uses its existing bounded recovery policy,
explicit admission and private causes. Missing provider metadata extraction remains
a documented shared limitation; it is not fabricated from untrusted headers.
No endpoint-specific cancellation/status/idempotency support is established here.
A timeout or closed socket does not prove inference termination.

Current validation and independent slice review are recorded in
`.foundry/validation.json`. Linux excludes iOS execution and Apple framework linking;
Apple runtime/transport validation is explicitly pending. Whole-mission review and
cross-port alignment remain required. The earlier evidence below is historical.

Current Linux validation passed all six gates together (544 tasks), all 396 JVM
tests across 68 suites (zero failures/errors/skips), and the unfiltered audit
(337 dependency entries, zero reported vulnerabilities). OSS Index was unavailable
without credentials; the .NET analyzer warning remains visible. No dependency,
audit scope or suppression change was made. Local publication with a disposable
key passed; inspection hashed 36 POMs and 36 module files with the original group
and version. Of 54 external coordinates, 42 match exact audited artifacts and 12
match audited family versions. Exact Native binary safety remains pending.
Independent review verified the source corrections and proof/gate/XML/audit
evidence. Complete logs, source snapshots, metadata, hashes and exact revisions
are retained in `.foundry/` and copied outside the worktree to
`/home/svetzal/.foundry/tool-logs/mojentic-kt-openai-recovery-c5-ca4ece-evidence`.
Foundry retains Git finalization ownership; Apple and whole-mission validation
remain explicitly pending.

---

# Completion recovery conformance — bounded Kotlin slice

Earlier slices added opt-in streaming completion recovery through both public local
adapters, `stream`, `streamEvents`, broker streaming and session streaming. It
retains the landed ordinary/structured recovery and dedicated transport.
Acceptance uses `TRANSIENT-RECOVERY-2026-10.md`, `RECOVERY-REQUEST-2026-10.txt`
and the October 10 supplement supplied in the correction plan. Rust is compared
read-only at exactly `4ca1ed279c02eab37827a1ed07c30e961155ecf3`.
No whole-mission parity, live inference, harness integration or Apple validation
is claimed.

Correction starting Kotlin revision is `985c167bf5b2a78a60f5e4f674a72e85617353b6`,
with a clean tree, preserving the streaming slice. Foundry forbids Git finalization and ref modifications: no pull, rebase, commit, push, release or tag is performed. The initial fetch
was rejected by read-only Git metadata; origin/main remains at the locally
recorded `83dd144f0f13097aed23f608476ab0daa7b6abb5`. Foundry must reconcile
the final source against current main before landing.
Existing coordinator guidance is preserved. Exact working/comparator source
hashes and complete command logs are retained under `.foundry/logs/`.

## Assertion-backed acceptance

The matrices in `CompletionRecoveryHttpTest` and `RecoveryTransportHttpTest` exercise ordinary/structured cases
against both public adapters using their default real JVM HTTP engine and a
scripted loopback HTTP/1.1 server. No LlmGateway mocks or private retry helper tests
are used. The preserved `OllamaRecoveryProofTest` retains permanent-status truncation coverage.
The early public Ollama probe `observedStreamingContentBlocksReplayBeforeCaptureDelivery`
first rejected the legacy path with a real truncated HTTP body. The correction
observed UTF-8 content before throwing capture, retained the exact typed cause,
delivered no events, and sent one actual request. That is preserved prior-slice evidence; the current correction proof below
uses escaped keys and a distinguishable queued response. The scripted
server synchronizes capture before closing a truncated body; this ensures its
bytes are available to the real engine rather than discarded during closure.

| Acceptance | Test and assertions |
| --- | --- |
| 503 `Retry-After: 0` recovery | `recover503PreservesExactPayloadIdentityAndLifecycle`: identical request bytes across wires and capture, stable logical IDs, distinct attempt IDs, one-based numbers, terminal status captures, reasoning/usage/finish compatibility, complete event order |
| One-attempt 503 | `oneAttempt503ZeroRetainsExactFailureWithoutHiddenResend`: one actual request, exact captured payload, numeric 503, retained original Retry-After and body, identity/history and ordered terminal events |
| Provider socket timeout | `healthyGenerationBeyondTenSecondsKeepsProviderSocketSettings`: all four entrypoints accept an 11-second delayed reply, oMLX configured at 15 seconds, with a 100ms recovery budget; `shorterOmlxSocketTimeoutRetainsCauseWithoutUnauthorizedResend`: both oMLX entrypoints retain SocketTimeoutException under a 200ms timeout with 700ms delayed headers, one actual request and no admission-authorized retry |
| 504 exhaustion | `exhaustionRetainsEveryAttemptAndTypedCause`: exactly three requests, complete failure list, numeric statuses, private bytes/headers, original typed cause identity, safe formatting |
| Retry-After | `retryAfterSecondsDatesInvalidAndMinimumRefusal`: seconds, future/past dates, invalid/negative values, exact injected delays, overflow minimum refusal |
| Admission | `admissionWaitsForExplicitDecisionAndRejectPreventsResend`: 503 carries `Retry-After: 0`; pending means one request and incomplete operation; exact captured bytes, numeric status/history/identity and ordered stages; explicit allow makes a second identified request; reject/missing hook prevents it |
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
| Ollama/oMLX streaming (`stream`, `streamEvents`) | opt-in request recovery; legacy paths preserved | unknown; caller admission required |
| OpenAI Chat Completions ordinary/structured/stream/streamEvents | opt-in recovery; disabled parsers preserved | endpoint cancellation/status/idempotency not established; custom-host capabilities unknown |
| Anthropic completion/streaming | existing behavior preserved; recovery pending | not investigated here |
| Embeddings, realtime, model management | existing behavior preserved; outside completion recovery | not investigated here |

Default JVM/Android OkHttp connection retries and redirects are disabled.
`retryOnConnectionFailure(false)` alone did **not** disable OkHttp's automatic
503 follow-up when `Retry-After: 0`. A per-call holder now lets the network
interceptor withhold Retry-After on 503 from OkHttp's follow-up layer; an outer
application interceptor restores only those original Retry-After values before
Ktor observes that response. Other headers retain OkHttp's normal transformations,
including Content-Encoding/Content-Length removal after transparent gzip decoding.
`compressed503PreservesDecodedFailureAndRetryAfterWithoutResend` proves that
behavior across all four public entrypoints with actual compressed response bytes. Recovery therefore owns admission and each subsequent request,
while retaining numeric status and original Retry-After evidence. The holder is
per call, without shared mutable response state. OkHttp's 408 follow-up honors
the disabled connection-retry flag; its 421 follow-up requires an HTTP/2
coalesced alternate origin, absent from these dedicated fixed-provider clients.
This proves the bounded default production loopback paths, rather than supplied
engines or all possible transports.

The dedicated client installs connect/socket-only HttpTimeout. oMLX forwards its
resolved explicit/environment/default duration, including the default ten minutes;
Ollama keeps the engine's connect default and waits without an implicit ten-second
socket read limit. Neither has a total request/generation timeout. An explicitly
short socket timeout remains a typed retained transport failure subject to normal
admission, rather than an implicit resend. Disabled recovery keeps the original
clients and existing broker/session delegation unchanged. Supplied engines must
be configured by their owner. Android uses the same client configuration, but only
Linux host checks are available here. Darwin conformance and Apple simulator
execution require controller validation. The existing Apple workflow
`.github/workflows/build.yml` builds/tests core on macOS; its presence is not proof
that this dirty change passed Apple CI. No new native/Apple success is claimed.
Linux compiles Kotlin sources/metadata for the configured targets, but iOS framework linking and simulator execution are skipped; those exclusions remain visible in complete build logs. There is no
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
`.foundry/logs/`. Actual gate outcomes and the independent-review record are recorded
there after validation. API snapshot changes are restricted to intentional
additions; legacy CompletionConfig constructor/copy descriptors are retained.
Foundry owns finalization: this worktree remains dirty, with no ref modifications,
commit, push, tag, branch, PR or release.

## Prior transport repair record (historical)

The following outcomes describe the landed transport repair, not this streaming
worktree. Current-run results are recorded in `.foundry/validation.json`.

The initial worktree was clean at preserved commit
`ec491f458c812651cfc4cb07d64cb32f39bed124`. A fresh `git fetch origin`
succeeded; `origin/main` was its direct parent `e313f32` (ahead/behind 1/0),
so no reconciliation conflict existed. Foundry forbids ref changes here; no
rebase, merge, commit, push or release was attempted. The coordinator's AGENTS.md
is preserved. This is a transport repair to the preserved slice, without streaming,
dependency, sibling-repository or harness changes.

Compared read-only against Rust
`4ca1ed279c02eab37827a1ed07c30e961155ecf3`: `src/llm/recovery/adapter.rs`
constructs a client with `reqwest::retry::never()` and redirects disabled;
`engine.rs` captures before send, records per-attempt identity/status/history and
controls retries through admission. `gateways/ollama.rs` and `gateways/omlx.rs`
clear the prepared recovery request's total timeout. Kotlin preserves its own
provider connect/socket settings without introducing that total timeout. This
comparison supports only this bounded transport/attempt contract; it makes no
whole-mission alignment claim. Reference source hashes are in
`.foundry/logs/source-hashes.json`.

Independent read-only review by `/root/review` found no blocking source defects
in the default transport repair or preserved cancellation/privacy/redirect/
permanent-status/tool-once behavior. It independently checked the actual early
proof exits/logs and expanded fixture assertions. It accepted the final header-only restoration and
fixtures, then independently verified all 20 passing HTTP tests, six gates,
the refreshed 337-entry audit, local publication and all 72 metadata hashes
with no blockers. The record is
`.foundry/logs/independent-review.md`. Apple execution remains **pending**.

Prior-run final validation outcomes are recorded in `.foundry/validation.json`
and complete command logs under `.foundry/logs/`. Prior-run gate/audit counts are
not evidence for the streaming packet. Exact Native binary vulnerability analysis remains
pending Apple/controller validation.

Final prior-run Linux results after the header refinement: the combined six
configured gates passed, 544 tasks. All 20 HTTP tests passed with zero
failures/errors, including compressed 503 handling; the delayed-response matrix
completed four real 11-second replies. The refreshed unfiltered audit passed
with 337 entries and zero unsuppressed findings. Existing suppression rules are
unchanged; consumer inspection rechecked the Android logging/GitHub Enterprise
false-positive match against the advisory subject and actual AAR classes in
`.foundry/logs/consumer-existing-false-positive.json`. The report retains its
existing missing .NET assembly-analyzer warning without reducing audit scope.
Refreshed `publishToMavenLocal` and consumer-metadata inspection passed, covering
36 POMs and 36 module files with a temporary local-only signing key and output
under `/tmp`, without remote publication. Exact consumer coordinates and metadata
hashes are in `.foundry/logs/metadata-inspection.json`; JVM coordinates match the
unfiltered audit and Native dependencies match audited family versions. Exact
Native binary analysis and Apple runtime validation remain pending. Existing
unrelated compiler/Dokka/Gradle warnings remain visible in complete logs. Earlier
validation iterations are retained with explicit `before-header-refinement` and
failure labels, rather than presented as final evidence.

## Streaming assertion evidence

`StreamingRecoveryHttpTest` and `StreamingRecoveryLifecycleTest` use the default real HTTP engine through both public
adapters and both streaming APIs. Its assertions cover:

| Test | Evidence |
| --- | --- |
| `admittedRetriesPreserveExactPayloadCaptureAndIdentities` | 503 admission; identical request/capture bytes; distinct attempt IDs and stable logical ID; ordered lifecycle; private payload/credential exclusion |
| `retryAfterAndBoundedGatewayTimeout`, `retryAfterDatesInvalidAndMinimumRefusalAndHealthyGenerationBudget` | numeric statuses/history; 429 seconds/date/invalid delay; minimum refusal; bounded 504; first-failure budgets do not time out successful generation |
| `admissionWaitRejectAndRecoveryBudgetDoNotSend` | pending decision holds one wire request; rejection and exhausted budget prevent resend |
| `allObservedSemanticChannelsInterruptWithoutReplay` | reasoning, content and tool fragments each block replay, including tool-free reasoning; UTF-8 byte counts and explicit interruption |
| `keepaliveOnlyClosureRequiresAdmissionAndCanRecover` | raw bytes without semantic output; unknown acceptance; explicit admission permits retry |
| `truncatedPermanentStatusesRemainPermanent` | 400/401 remain permanent after truncated bodies; exact partial private evidence and original IOException |
| `captureFailureIsTerminalBeforeDeliveryThroughBothAdapters` | capture fails before delivery with original hook object identity; no resend |
| `validLengthHasProgressThenMetricsThenFailureAndMalformedHasNoTelemetry` | validated Ollama Progress then Metrics then Failed; original token counts/durations, absent fields stay absent; no fabricated malformed-frame metrics |
| `pausedConsumerCancellationRecordsFailedAttemptBeforeOneTerminalCancellation` | consumer paused on final content; server observes socket closure; Failed before exactly one Cancelled; available status/progress retained; no success |
| `pausedTerminalCompletionClosesOwnedResponseBeforeCancellation` | terminal-only completion is delivered after the attempt finalizes and response closes; later consumer cancellation adds no attempt lifecycle |
| `finalizationObserverFailureProducesOnlyPrivateErrorThroughGatewayAndBroker` | throwing success observer emits one Error and no Completed through both public boundaries; original private cause identity and privacy retained |
| `terminalOnlyMetricsCancellationCannotSucceed`, `cancellationFromProgressObserverRetainsZeroDeliveredBytes` | observer cancellation at telemetry prevents success; zero delivered bytes when consumer was never invoked |
| `cancellationDuringAdmissionAndBackoffNeverResends` | cancellable admission/backoff; no later request or duplicate terminal cancellation |
| `invalidUtf8AndProviderErrorsCannotManufactureSuccessOrTelemetry`, `quotedTelemetryAndTerminationAreMalformedBeforeProgress` | exact invalid wire input and primitive-type validation; no invented telemetry or success; private provider echo excluded |
| `brokerToolRunsOnceAndSessionRestoresSnapshotAfterFollowupFails` | completed tool executes exactly once; follow-up failures recover only that completion with identical tool history; session snapshot restored; successful stream commits history |

Opt-in flows have no internal output buffer. Synchronous lifecycle telemetry
cannot run ahead of a suspended direct collector. Caller-added Flow buffers have
normal upstream/downstream semantics and are outside that guarantee.

Remaining mission gaps: OpenAI/Anthropic recovery, validated provider codes/request
IDs, native reasoning-history representation, provider ownership/remote
termination/idempotency facilities, Native/Apple execution and exact Native
artifact analysis. This packet adds no continuation, tool/agent replay, sibling
port changes or harness experiment. Existing security exclusions remain intact.

## Preserved streaming validation (prior run)

All six configured Linux gates passed together (`ktlintCheck detekt build
allTests apiCheck dokkaGenerate`). All 384 JVM tests passed, including the
20 streaming transport/lifecycle tests. The API snapshot only adds signatures;
existing constructor/default/copy descriptors remain. Independent read-only
review verified source, actual proof outcomes, gate logs and assertion results.

The unfiltered `dependencyCheckAggregate --no-parallel` audit passed with 337
entries and zero unsuppressed vulnerabilities. Existing exclusions and
suppression rules are unchanged. Missing OSS Index credentials and the .NET
assembly analyzer warning remain visible in the retained complete logs. The NVD
modified feed timestamp was checked before the audit.

Isolated `publishToMavenLocal` passed with a disposable signing key and output
under `/tmp`. Consumer inspection retained hashes for 36 POMs and 36 module
files. Of 54 external coordinates, 42 match exact audited artifacts and 12
Native coordinates match audited family versions. The existing Android logging
false-positive finding was checked against the advisory subject and actual AAR
classes; no rule was edited. Exact Native binary analysis and Apple runtime
validation remain pending.

Actual commands, exit codes, artifact paths and limits are in
`.foundry/validation.json`. Complete passing/failing command logs, source/log
hashes, exact reference revision, consumer metadata and independent-review
evidence are retained in `.foundry/logs/`. Foundry owns finalization; no commit,
push or ref modification was performed.

## Semantic replay correction

The current behavioral proof is
[.foundry/proof.json](.foundry/proof.json). With unchanged source,
`escapedSemanticKeysPreventReplayAtHttpBoundary` failed because escaped
content was delivered and EOF triggered a second request that produced
`ok-é` and successful completion. The same probe passed after correction
through Ollama and oMLX, each using public `stream` and `streamEvents`.
It asserts exactly `private-é`, one recorded wire request, zero admission,
exact observed UTF-8/raw byte counts, actual capture/failure identity, complete
single-failure history, original typed EOF cause, and terminal interruption
without success.

`escapedChannelsSurviveFragmentedReadsAndCaptureFailure` extends that matrix
to content, reasoning and tool fragments. In the non-failing capture cases,
a semaphore makes the HTTP server wait for boundary capture after each byte,
including escaped keys and split UTF-8. Reasoning on the tool-free API and tools on both APIs can remain
undelivered while still vetoing replay. The capture-failure cases throw as
soon as the complete body is observed, before its line can be delivered. The
hook retains its original private cause and exact observed counts with zero delivered counts
and output. Tool-free tool fragments produce the existing private protocol
cause rather than an EOF cause.

`deliveredProgressIndependentlyPreventsHttpReplay` uses the real HTTP boundary
with a keepalive body and independently delivered content, reasoning or
completed tools. It asserts an unchanged original cause, one wire, complete
history and Interrupted even when semanticObserved is false.
`escapedInterruptionReachesBrokerAndRestoresSession` verifies exact broker
and session output, broker tool-free Content then Error, no Completed,
one request and session history rollback.
`StreamingProgressTest` covers escaped incomplete frames, empty fields,
quoted lookalikes, numeric-only progress and completed-tool evidence retained
across subsequent reads. The keepalive admitted control retains identical
request bytes, stable logical ID and distinct numbered attempt IDs.

Read-only comparison against Rust
`4ca1ed279c02eab37827a1ed07c30e961155ecf3`:
`engine.rs` classifies and interrupts when either observed.any() or
delivered.any() is true; `frames.rs` decodes JSON before accumulating UTF-8
content/reasoning and tool fragments. Kotlin now independently uses both
evidence channels and decodes escaped keys before capture. Its incomplete
frame prefixes are conservative; exact semantic byte counts require complete
JSON frames. No claim is made about remote inference termination, continuation,
OpenAI/Anthropic expansion or whole-mission alignment.

Current correction gates, audit, consumer inspection and independent review
are recorded separately in `.foundry/validation.json` when available.
Prior-run counts above are historical, not validation of this correction.
Linux cannot run iOS tests or Apple framework linking; metadata and klib
processing can still run. Apple runtime execution and exact Native binary
vulnerability validation remain pending. No disabled-target warning is suppressed.

Correction validation completed on Linux: all six configured gates passed
together, 544 tasks. All 392 JVM tests passed (65 suites, zero failures/errors/
skips), including the 12 HTTP recovery tests and four new progress tests.
The unfiltered `dependencyCheckAggregate --no-parallel` audit passed with 337
entries and zero unsuppressed findings. Existing suppression rules are unchanged;
the Android logging/GitHub Enterprise false-positive was rechecked against
actual AAR classes and the advisory subject. The missing .NET assembly analyzer
warning remains visible; OSS Index was unavailable because credentials were
not configured. No audit configuration or scope was narrowed.

Isolated `publishToMavenLocal` passed with a disposable local signing key.
Consumer inspection read and hashed 36 POMs and 36 Gradle module files; all
published coordinates retain the configured group and version. Of 54 external
coordinates, 42 exactly match audited artifacts and 12 match audited family
versions. Family matches do not prove exact Native binary safety. Source/test
review independently approved the correction and reconciled all JVM XML results.
Final evidence review, command outcomes, retained failure iterations, source and
log hashes are recorded in `.foundry/validation.json` and `.foundry/logs/`.
No Git finalization or remote publication was performed.

## Validated provider metadata correction

This slice starts at delivered Kotlin
`b8139728d77cabd02f3b58e99252e4381d308236`. It implements the public error/privacy
contract and the task-supplied October 10 supplement; provenance is recorded in
[.foundry/OCTOBER-10-SUPPLEMENT.md](.foundry/OCTOBER-10-SUPPLEMENT.md).
No separate external supplement was present. Git finalization belongs to Foundry;
no fetch, rebase, commit, push, tag, release, dependency or version mutation occurred.

The early public HTTP proof is [.foundry/proof.json](.foundry/proof.json): the
unchanged transport failed with expected `server_error`, actual null. After the
source correction the same production OpenAI boundary retained that exact code
and `req_123e4567-e89b-12d3-a456-426614174000`, with one request despite a queued
success sentinel. This preceded expanded fixtures, docs and the full gates.
The actual exit codes are 1 and 0; complete capture logs and the bounded command
logs are retained. Initial read-only cache failures are separate evidence and
are not counted as the behavioral rejection.

`RecoveryMetadataHttpTest` now has seven passing tests and 156 scripted HTTP
cases through production transports. Each applicable case uses all three
adapters and all four operations: ordinary, structured, stream and streamEvents.
Assertions cover:

- Exact distinct 503/429 codes and UUID request IDs, ordered failure histories,
  stable logical identity, distinct numbered attempt identities, identical
  encoded request bytes, exact captured response bytes and capture identities.
- Unknown, malformed, numeric and credential/payload-echoed values omitted from
  safe getters and summaries, including UUID-shaped bearer credentials and
  `req_` echoes; permanent statuses never call admission or consume the success
  sentinel. Raw headers/body and original typed causes remain inspectable.
- Safe formatting, report/lifecycle JSON, metadata-preserving legacy copy and
  JSON round trips; received codes in HTTP-200 JSON/NDJSON/SSE provider errors.
- Admitted success with exactly two requests and the original failure metadata
  retained in the success history; broker ordinary/stream/event and session
  send/stream preserve the same failure object, metadata, raw evidence and
  rollback behavior.

Existing recovery tests also remain in the full suite: cancellation, default
one attempt, permanent classification, semantic replay vetoes, immutable request
bytes and tool safety. This correction does not add retry eligibility, timeouts,
Anthropic recovery, realtime/embedding recovery or an inference termination claim.

Read-only Rust comparison is exactly
`4ca1ed279c02eab37827a1ed07c30e961155ecf3`; relevant pinned source slices are
retained as `.foundry/logs/rust-metadata-types.rs` and `rust-metadata-engine.rs`.
Kotlin uses the same seven-code vocabulary and UUID spelling family with optional
`req_`, preserving received spelling. It checks encoded request and bearer echoes;
it additionally checks decoded JSON request strings, UUID wrapper contents and
code echoes, and omits ambiguous header values or conflicting codes. These are
conservative privacy limits, not claims of broader provider capabilities.

All six discovered gates passed together on Linux (544 Gradle tasks), including
API compatibility and Dokka. The JVM XML totals and the seven new test results
are retained in `.foundry/logs/jvm-results-summary.json` and
`metadata-test-results.xml`. Existing compiler/Dokka warnings remain visible.
iOS runtime tests and Apple framework linking cannot run on Linux; Apple
validation and exact Native binary vulnerability validation remain pending.
No disabled-target warning is suppressed.

The configured full-scope dependency audit passed with 335 entries, zero
unsuppressed findings and 68 existing suppressed findings. A second scan with
suppression disabled via a temporary init script failed: 337 entries, 68 findings,
zero suppressions, including CVSS >= 7.0. Both reports and full logs are retained.
The fresh NVD modified feed timestamp was `2026-10-10T18:00:07-04:00`, matching
scanner data. Existing shaded build-tool findings and CPE matches remain visible;
this correction does not certify a clean unfiltered security audit. No dependency,
threshold, audit scope, suppression or allowlist was changed.

A separate read-only correction review checked the behavioral proof schema and
actual exit codes, retained every original JVM API descriptor, and verified that
coordinates, dependencies, audit rules and AGENTS.md are unchanged. Its provenance
is explicitly the implementation agent; an external independent review remains
pending Foundry. This evidence makes no whole-mission alignment claim.

Serial isolated `publishToMavenLocal` passed after the retained first attempt
hit a concurrent metadata-output race with the build. Publication used a disposable
local signing key and `/tmp/mojentic-metadata-m2`; nothing was remotely published.
Inspection hashed 36 POMs and 36 module files and found 54 external coordinates:
42 match audited artifacts exactly, while 12 Native variants require exact artifact
validation. The unfiltered consumer matching retains the Android logging
`CVE-2012-2055` CPE finding rather than silently removing it. Its actual class
inventory is recorded in `.foundry/logs/logging-android-classes.json`; it is a
logging facade. See the [NVD advisory](https://nvd.nist.gov/vuln/detail/CVE-2012-2055)
for applicability review. No clean unfiltered consumer-security claim is made.
Consumer hashes, coordinates and findings are in `.foundry/consumer-inspection.json`;
final command outcomes and evidence/source hashes are in `.foundry/validation.json`.
