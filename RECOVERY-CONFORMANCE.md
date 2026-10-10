# Completion recovery conformance — bounded Kotlin slice

This repairs the default production transport under the preserved ordinary and
structured recovery slice for public Ollama/oMLX adapters, including existing
broker/session delegation. It does not claim whole-mission alignment. Intent
references are `TRANSIENT-RECOVERY-2026-10.md` and
`RECOVERY-REQUEST-2026-10.txt`, with the current Foundry correction requiring one
HTTP request per policy attempt and preserved provider connect/socket settings
without a total generation timeout. The Rust comparator is exactly
`4ca1ed279c02eab37827a1ed07c30e961155ecf3`, inspected read-only; no sibling source
or harness pin changed.

Preserved Kotlin revision: `ec491f458c812651cfc4cb07d64cb32f39bed124`,
whose parent is fetched `origin/main` at
`e313f32030b0613dc954912b1b4920493f3b45e6`. Working-source and comparator
SHA-256 hashes are recorded in `.foundry/logs/source-hashes.json`.
They describe the reviewed working implementation, not a released revision.

## Assertion-backed acceptance

The matrices in `CompletionRecoveryHttpTest` and `RecoveryTransportHttpTest` exercise ordinary/structured cases
against both public adapters using their default real JVM HTTP engine and a
scripted loopback HTTP/1.1 server. No LlmGateway mocks or private retry helper tests
are used. The preserved `OllamaRecoveryProofTest` retains permanent-status truncation coverage.
For this repair, the early proof changed the existing 503 input to carry
`Retry-After: 0`, before expanding fixtures or documentation. The original
production client sent two server requests while capture recorded only attempt 1
(expected `[1, 2]`, actual `[1]`). The corrected same public-entrypoint matrix
passed. Actual exits are 1 and 0 in [.foundry/proof.json](.foundry/proof.json);
complete logs are [.foundry/logs/rejecting.log](.foundry/logs/rejecting.log) and
[.foundry/logs/corrected.log](.foundry/logs/corrected.log).

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
| Ollama/oMLX streaming (`stream`, `streamEvents`) | existing paths preserved; recovery pending | no added termination proof |
| OpenAI/Anthropic completion/streaming | existing behavior preserved; recovery pending | not investigated here |
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

## Correction provenance and review

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

Current-run final validation outcomes are recorded in `.foundry/validation.json`
and complete command logs under `.foundry/logs/`. Prior-run gate/audit counts are
not evidence for this repair. Exact Native binary vulnerability analysis remains
pending Apple/controller validation.

Final current-run Linux results after the header refinement: the combined six
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
