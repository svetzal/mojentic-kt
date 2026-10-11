# Supported targets (2026-10-11)

Mojentic Kotlin supports the JVM and Android only. iOS targets were removed in
`e4462c2`: each Mojentic port serves its own ecosystem, and native Apple apps use
`mojentic-sw`. Recovery conformance is established on JVM and Android. Every
statement below that Apple, macOS/iOS or Kotlin/Native validation is "pending"
or "required" is historical and no longer applies.

# October 11 correction — current controller acceptance

The candidate starts at preserved cycle-11
`a24961de2f1e9b9d7783920d493f6a56c7596c9e`. Its runtime security floors,
active suppressions, audit scope and threshold, target exclusions and disabled
transport retries are retained. Foundry owns landing directly on main; this
worker performs no fetch, rebase, commit, push, tag or other ref mutation.
Security preparation in the preserved commit and this focused recovery
correction remain separately scoped.

Stacey's October 11 audit decision makes the unchanged configured
`dependencyCheckAggregate --no-parallel` authoritative. Unfiltered diagnostic
output is informational. Historical failures and component-fix evidence below
remain preserved; the older unfiltered landing requirements describe historical
assessment and are superseded by this decision. No suppression or dependency
policy is changed or newly authorized here.

The retained Anthropic and oMLX failures are production cause loss, not weakened
fixture expectations: Ktor's `readAvailable` can return negative EOF when a
channel is already closed for reading, without throwing its failure. The shared
reader then generated `IllegalStateException("HTTP failure")`, losing the
truncation `IOException`. The correction checks cancellation first and throws
the channel's failure before accepting EOF. Existing clean EOF and numeric HTTP
classification remain intact.

Before documentation or full gates, the public Anthropic socket matrix rejected
the original reader (exit 1, `IOException` expected / `IllegalStateException`
actual) and passed with the correction (exit 0). A test-only engine observer
inspects the actual post-transformation response channel without replacing
bytes, failures or transport execution. Capture releases the scripted socket
only after every partial UTF-8 byte has been read, then waits for failed closure
before the next boundary read. This establishes the failing timing without
fixed sleeps. Original type and private cause-identity assertions remain intact.
Ktor may copy its closure exception on separate accessor calls; the identity
contract tested is the original boundary failure preserved in inspection,
boundary cause, immutable history and lifecycle events.

The permanent matrix covers ordinary, structured, streaming and supported event
APIs for all four adapters and applicable broker/session callers. It asserts
exact immutable request bytes, numeric 400/401/403, partial UTF-8 bytes/headers,
original typed private cause identity, ordered terminal events, one request,
zero semantic delivery, zero admission/resend/tool execution and rollback.
Complete capture logs, rejecting/corrected source and XML, exact revisions,
hashes and review are retained outside disposable worktrees at
[/home/svetzal/.foundry/tool-logs/mojentic-kt-c12-recovery](/home/svetzal/.foundry/tool-logs/mojentic-kt-c12-recovery).
Cycle-11 failing full-suite XML and logs remain in the separate cycle-11 archive.

Migration and capability limits remain documented in
[completion recovery](docs/use-cases/completion-recovery.md). Supported encoded
message/tool history is frozen across attempts; no provider-native signed
thinking-history representation or broader native-history parity is introduced.
Independent whole-mission review compares the actual public assertions with both
normative files and exact Rust `4ca1ed279c02eab37827a1ed07c30e961155ecf3`.

Apple and Kotlin/Native validation no longer applies: the port has no iOS
targets after `e4462c2`. Conformance is claimed for JVM and Android only.

All six required gates passed together on the corrected candidate (exit 0).
Fresh JVM/Android results contain 765 test executions in 132 suites with zero
failures, errors or skips; these are execution counts, not unique tests.
The deterministic permanent matrices include 138 provider/status/caller cases.

The unchanged configured audit passed (exit 0): 328 dependency entries, zero
active findings and 55 existing suppressed findings. Two earlier attempts
failed because NVD's gzip endpoint returned a truncated feed. Its ZIP copy's
complete JSON matched the origin metadata size and SHA-256. The supported local
feed transport used verified modified and all annual 2002–2026 JSON feeds,
repacked as valid gzip; no CVE filtering, skipped scope or policy change was
introduced. The incomplete mirror setup failure is retained too. Existing OSS
Index credential and .NET runtime limitations remain visible in complete logs.
No clean unfiltered-security claim is made.

Local `publishToMavenLocal` and consumer metadata inspection passed (exit 0).
All 36 POMs and 36 Gradle module files were read, archived and hashed; their
consumer dependencies match the preserved cycle-11 metadata. Group/version
remain `com.vetzal.mojentic` / `2.1.0`, with no audited build-tool/compiler/shaded
dependency published. Nothing was remotely published.

Read-only checks verified historical controller receipt hashes and a clean
canonical clone with matching HEAD, origin/main and remote main
`fa73ac12a89a0a20140943c18cec4bfdf8c57cf9`. The worker retains cycle-11 HEAD;
controller reconciliation was pending at the time of this record.
Complete command results and exact source/report hashes are recorded in the
durable validation manifest.

---

# Historical cycle-8 through cycle-11 reports

The following reports preserve earlier observations and decisions. Their
validation claims, unresolved blockers and disposable scratch artifact names
are historical, not current candidate acceptance or committed artifact links.

# Preserved ordinary broker/session correction — controller reconciliation

The preserved correction is `fb69c7ae17bde5b4675f9e876867f9e3ad2cde91`;
its parent is controller trunk `6379173f8a6eceeb48bd22883bf0f764546937cf`.
The controller synchronization receipt is
[/home/svetzal/.foundry/operations/mojentic-port-alignment-20261010/status-recovery/receipt.json](/home/svetzal/.foundry/operations/mojentic-port-alignment-20261010/status-recovery/receipt.json).
Its Kotlin entry records successful canonical-clone fetch and pull --rebase,
clean status, and matching HEAD, origin/main and remote main. This worker checked
the receipt's log SHA-256 values and retained copies; it performed no ref mutations.
Foundry owns landing. The release coordinator's AGENTS.md remains unchanged.

Fresh durable evidence for this reconciliation is retained outside the disposable
worktree at
[/home/svetzal/.foundry/tool-logs/mojentic-kt-c8-fb69c7-evidence](/home/svetzal/.foundry/tool-logs/mojentic-kt-c8-fb69c7-evidence).
The prior `.foundry` artifacts were absent here. Their historical results below
are not current validation evidence; historical failures and missing-artifact
disclosures remain preserved.

Before documentation or the full suite, the real loopback boundary probe
substituted `broker.stream(...).collect {}` for the ordinary broker call. It
failed with `expected false, actual true` at the request assertion (exit 1).
Restoring the preserved `LlmBroker.complete` and `ChatSession.send` cases passed
(exit 0). The rejecting source snapshot, XML and complete capture logs are
retained, including the Kotlin daemon read-only failure and successful compiler
fallback. The corrected run uses in-process compilation. See
`historical scratch: proof.json` and the durable `proof.json`.
The 12 cases execute production transports for Ollama, oMLX and OpenAI with
permanent 401 or exhausted 503/429 responses through both public callers.
Assertions cover exact submitted messages and controls, reject streaming,
compare immutable retry bytes, preserve provider codes/request IDs/statuses,
original cause identity/private evidence, and ordered attempt histories. They
verify single-send permanent failure without admission or queued sentinel
consumption, zero tool executions and exact prior-session rollback.
The tool assertion checks the submitted function name, rather than the entire
descriptor. The preserved assertions and production implementation were not rebuilt.

`unchanged-inputs.json` verifies 103 production/protected files against both
revisions, including dependencies, runtime pins, versions, APIs, AGENTS.md and
active suppressions. Suppression snapshots exist only in durable
`suppression-provenance/`, with source revision and hashes; none were copied into
worktree evidence. Audit scope and severity thresholds remain unchanged.

The six configured gates passed together (exit 0; 544 tasks: 328 executed,
209 from cache, 7 up-to-date). Retained XML has 743 test executions in 127 suites
across target result directories, with zero failures/errors/skips. This is not
a count of unique tests. Complete warnings and cached outcomes remain in
`quality-gates.log` and `test-results.json`.

The full configured `dependencyCheckAggregate --no-parallel` passed (exit 0):
337 entries, zero reported findings and 68 existing suppressed findings.
The same full-scope audit with suppression filtering disabled via a retained
external diagnostic init script failed (exit 1): 68 findings in 38 artifacts,
including 46 findings at CVSS >= 7.0 in 35 artifacts. Blocking IDs are
CVE-2026-53914 (22), CVE-2026-56740 (10), CVE-2026-56741 (10), CVE-2024-7254 (1),
CVE-2022-3171 (1), CVE-2012-2055 (1) and CVE-2008-0986 (1).
These are current scanner findings, not newly verified advisory applicability
or accepted advisories. No suppression, dependency or policy change was made.
The unfiltered audit failure blocks a claim that all applicable audits pass;
this correction is not security-clean and landing requires controller assessment.
Both JSON/HTML reports and complete capture logs are retained in separate
`configured-audit/` and `unfiltered-audit/` directories. OSS Index credentials
and the .NET Assembly Analyzer runtime were unavailable. The checked NVD feed
metadata timestamp was `2026-10-10T18:00:07-04:00`; the existing feed transport
was retained without changing scope or the CVSS 7.0 threshold.

`publishToMavenLocal` passed (exit 0), using the existing disposable local signing
key and a fresh `/tmp/mojentic-kt-c8-m2` destination. Nothing was remotely
published. Inspection passed as an inventory/coverage probe (exit 0): 36 POMs,
36 Gradle module files and 54 external coordinates; 42 exact audit matches and
12 same-version KMP family matches, with no unmatched coordinates. Group/version
remain `com.vetzal.mojentic` / `2.1.0`. The unfiltered report matches the published
`kotlin-logging-android:8.0.4` coordinate to CVE-2012-2055 and therefore also flags
the Kotlin logging family during conservative family comparison. This is a
scanner match requiring assessment, not an independent applicability conclusion;
consumer security is not established. Exact Native binary coverage remains
pending. Metadata copies, hashes and matches are retained in
`consumer-metadata/` and `consumer-inspection-summary.json`.
An inspection accidentally started before publication completed found no
metadata and exited 1; its log is retained as `premature-consumer-inspection.log`
and is excluded from passing consumer evidence.

Independent focused review is retained as `independent-review.md`. It found no
blocking source defect and verified receipt hashes, unchanged inputs and the
rejecting/passing boundary evidence. The configured gates passed, but the
unfiltered audit findings still block an all-gates landing claim. Current
command results are in `historical scratch: validation.json`,
with durable copies and source hashes in `source-revisions.json` and `hashes.json`.
Foundry must assess the audit blocker before controller landing.
Apple validation remains pending; Linux cannot establish Apple target coverage.
No release, live inference or remote publication was performed.

---

# Historical preserved fb69c7ae report (not current-run evidence)

# Ordinary broker/session metadata proof correction

This correction starts at Kotlin `6379173f8a6eceeb48bd22883bf0f764546937cf`,
with an initially clean working tree and the same locally recorded origin/main.
Foundry owns Git finalization and forbids ref changes in this worktree; no fetch,
pull, rebase, commit or push was performed. Foundry must reconcile current main
before landing. The release coordinator's AGENTS.md is preserved.

The previous `RecoveryMetadataHttpTest` cases named `ordinaryBroker` and
`ordinarySession` both invoked `broker.stream`. They did not establish ordinary
broker completion or session send metadata preservation, or send rollback.
The historical ordinary-path claim below is corrected here. Earlier linked
`.foundry` artifacts were absent from this checkout and are not evidence for
this run; earlier validation/security counts remain historical claims only.

The early rejecting HTTP probe captured `stream=true` for `ordinaryBroker`.
Changing those calls to `LlmBroker.complete` and `ChatSession.send` passed the
public-boundary probe for Ollama, oMLX and OpenAI. OpenAI-compatible encoders
omit the false default; assertions accept omission or explicit false and reject
true. An initial read-only Gradle cache failure and an intermediate assertion
failure requiring an explicit false field are retained separately, not counted
as the corrected passing evidence. See `historical scratch: proof.json`
and its source snapshots, test XML and complete capture logs.

`ordinaryBrokerAndSessionPreserveHttpEvidenceAndRollback` replaces the mislabeled
coverage with 12 production-transport loopback scenarios (three providers, two
public callers, permanent and exhausted two-attempt responses). It checks:

- Ordinary request selection, exact ordered roles/content including the submitted
  user message, model, temperature, token controls, Ollama context/prediction
  controls, tool descriptor, and absence of streaming/structured controls.
- Exact validated provider code/request ID, numeric status, raw response bytes
  and headers, captured request/response identity, original typed HTTP cause
  identity across private evidence and lifecycle records, ordered failure
  histories, stable logical identity and distinct numbered attempt identities.
- A permanent 401 with two attempts configured makes exactly one request and
  never invokes admission, tool execution or the queued distinct success.
  Exhausted 503/429 attempts preserve both failures and admit only the retry.
- Session send first establishes a successful prior turn, submits the next user
  message, and restores the exact prior history after either failure sequence.

Streaming broker, stream-event and session coverage remains in the original
boundary test. No production code, dependencies, suppressions, versions or tool
execution semantics changed. `ChatSession.send` still delegates to
`broker.complete` and restores its snapshot on failure.

All six configured gates passed together (exit 0, 544 tasks: 127 executed,
32 from cache, 385 up-to-date). Retained JVM XML contains 404 tests across 69
suites with zero failures/errors/skips; cached task outcomes are included.
The full-scope `dependencyCheckAggregate --no-parallel` passed (exit 0), with
337 entries, zero reported findings and 68 existing suppressed findings retained.
No dependency or suppression changed. OSS Index lacked credentials; the .NET
Assembly Analyzer lacked its runtime. The checked NVD modified-feed timestamp
was `2026-10-10T18:00:07-04:00`. Existing compiler/deprecation and Dokka link
warnings remain visible in the logs.

Local `publishToMavenLocal` passed (exit 0), using a disposable signing key and
`/tmp/mojentic-kt-c7-m2`. Inspection retained 36 POMs, 36 Gradle module files,
their hashes and 54 external coordinates; group/version remain
`com.vetzal.mojentic` / `2.1.0`. Nothing was remotely published.

Current command outcomes and complete logs are in
`historical scratch: validation.json`; exact source revisions and
hashes are in `historical scratch: source-revisions.json`.
Linux cannot execute iOS simulator/device tests or Apple framework linking;
Apple coverage is unexecuted. Consumer security is not established by Linux
metadata publication or an audit alone. Findings, existing suppressions and
service limitations are retained in `historical scratch: audit-summary.json`.
Earlier absent artifacts are not used to support these current results.

---

# OpenAI recovery expansion — October 10 correction

The current source change starts from delivered Kotlin
`a1cd3e9515e9282c53ec9e9ec2ccb8a5d0263d44`. A read-only `git ls-remote`
confirmed origin/main at that same revision. Foundry prohibits ref mutation, so
fetch/pull/rebase and Git finalization were not performed. The checked-in contract,
request and October 10 requirements in the correction plan govern this slice.
The supplement is present in the controller receipt's Kotlin decision output;
no standalone supplement file was supplied. Package versions and release guidance
are preserved. No sibling or harness files are edited.

The current behavioral proof is `historical scratch: proof.json`.
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
`historical scratch: proof.json`. With unchanged source,
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
`historical scratch: OCTOBER-10-SUPPLEMENT.md`.
The controller receipt's Kotlin decision output contains the supplement;
a standalone external supplement file was not supplied. Git finalization belongs to Foundry;
no fetch, rebase, commit, push, tag, release, dependency or version mutation occurred.

The early public HTTP proof is `historical scratch: proof.json`: the
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
  retained in the success history; broker stream/event and session stream
  preserve the same failure object, metadata, raw evidence and rollback behavior.
  The original ordinaryBroker/ordinarySession labels both called broker.stream;
  ordinary complete/send proof is supplied by the correction at the top of this report.

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

## Anthropic public completion recovery (October 10, 2026)

This slice starts at delivered Kotlin
`241ad4c4179b9e331f4ad759b7f29ecc9bf32b4d` and compares exact Rust
`4ca1ed279c02eab37827a1ed07c30e961155ecf3`. Rust blob hashes and the comparison
are in `.foundry/rust-comparison.json`. Its generic transport design is reused
through Kotlin's existing recovery machinery; ordinary Anthropic calls retain
ordinary Messages HTTP and structured calls retain their forced synthetic tool.
Rust's delegated event-stream path is not a basis for claiming Anthropic event
recovery: Kotlin Anthropic has no separate `streamEvents` API.

The early public-boundary proof is `.foundry/proof.json`. On the delivered source,
an actual scripted production-HTTP stream with escaped Unicode reasoning returned
normally because it ignored recovery/capture. The corrected probe retains the
capture exception and observed reasoning byte count, with zero delivered bytes,
zero admission calls and exactly one wire request. Full red/green logs are durable;
no marker toggle or private-helper substitute is used.

| Assertion-bearing test | What it establishes |
| --- | --- |
| `ordinaryBrokerAndSessionRecoverWithoutStreamingSubstitution` | Actual gateway.complete, broker.complete and session.send each recover 503 in exactly two ordinary requests; exact bytes/controls, API key/version headers, stable logical ID, distinct attempt IDs, ordered lifecycle/history and original usage/model/finish/reasoning |
| `structuredRecoveryKeepsForcedToolSchemaAndDoesNotReplayTools` | Forced respond_in_json tool/schema survives 503; identical bytes and no streaming substitution |
| `defaultAndPermanentTruncatedErrorsNeverResend` | Default one attempt and truncated 400/401/403/504 retain numeric status, headers, IOException and exact partial bytes; permanent statuses veto admission |
| `persistent504HasOrderedBoundedHistory` | Two-attempt exhaustion preserves ordered final history without a third request |
| `retryAfterSecondsDatesAndInvalid` / `retryAfterMinimumRefusesCeilingOrBudget` | Seconds, future/past dates, invalid value, delay ceiling and recovery budget; observed wall clock and deterministic sleeper/jitter |
| `redirectsAndOverflowingRetryAfterNeverCreateHiddenAttempts` | Production recovery transport refuses redirect replay; overflowed minimum is refused, one wire request |
| `pendingAdmissionWaitsForExplicitPermissionAndBudgetBoundsIt` | A pending hook creates no new request; explicit allow resumes; pending recovery budget expires as failure |
| `admissionRejectsWithoutAnotherWireRequest` | Explicit rejection makes exactly one wire request |
| `keepaliveOnlyCanRecoverButContentReasoningAndToolFragmentsInterrupt` | Keepalive-only EOF retains raw progress and may recover; content, thinking and tool fragments each interrupt after one attempt |
| `malformedFramesAndMalformedStructuredRepliesStayIneligible` | Malformed JSON, unknown events/reasons and missing ordinary content are terminal, with no replay |
| `cancellationWinsInRequestAdmissionBackoffAndPausedToolDelivery` | Cancellation wins in active read, pending admission, delay, paused completed-tool delivery and terminal complete capture; failed history precedes one cancellation event, with no success or resend |
| `brokerToolsExecuteOnceAcrossFollowupRecovery` | Actual session.send/broker.complete execute one completed tool once; only followup completion retries; tool results remain in identical ordinary requests |
| `streamingBrokerAndSessionCompleteToolsOnce` | Actual broker.stream and session.stream execute a completed tool once across followup 503 recovery, with exact followup bytes |
| `safeSummariesWithholdCredentialPayloadAndHeaderEchoes` | request-id, error.type, body and x-api-key credential/payload echoes remain absent from safe fields/events; exact evidence requires explicit inspection |
| `providerErrorsKeepTypedCauseAndValidatedMetadata` | Provider error SSE has PROVIDER_RESPONSE category, typed original cause, validated request-id/type, one attempt and no admission |
| `captureFailureRetainsOrdinaryToolTextAndReasoningEvidence` | Ordinary capture failure retains exact Unicode text/thinking counts and completed tool evidence, zero delivery, no replay |
| `interruptedCompletedToolsKeepCumulativeCountsAndActualTelemetry` | Two completed tools remain observed-only on EOF; actual model, max_tokens and input/output usage remain privately inspectable |
| `documentedTerminalReasonsKeepLegacyStreamingResults` | Successful documented stop reasons, including max_tokens, compare identical Unicode events with the actual legacy transport |

The separate read-only review in `.foundry/independent-review.md` approves this
source slice after correcting its findings. Gates are implementation-run evidence,
not independently rerun by that reviewer. Current outcomes and source/evidence
hashes are recorded in `.foundry/validation.json`; historical failed probes,
compilation, formatting and first full-gate failures are retained.

The configured full-scope audit has 335 entries, zero unsuppressed findings and
68 existing suppressed findings. The unfiltered full-scope audit has 335 entries,
68 findings and zero suppressions, and fails the CVSS >= 7 gate. Reports are in
`.foundry/audit-configured/` and `.foundry/audit-unfiltered/`. This does not certify
clean unfiltered security. No dependencies, audit scope, thresholds, suppression
files or allowlists were changed. Earlier historical findings above remain intact.

Linux evidence does not establish Apple execution or Native transport guarantees.
Apple validation remains pending. Supplied engines must independently disable
hidden retries/redirects. Remote termination and idempotency are not established;
client IDs do not make inference idempotent. No new provider generation feature,
embedding/realtime recovery, tool replay, total generation timeout, release,
live inference or coordinated parity claim is included.

The October 10 supplement is in the controller synchronization receipt's Kotlin
decision output. The receipt records ancestor
`6379173f8a6eceeb48bd22883bf0f764546937cf`, not this delivered HEAD. The receipt
and its six Kotlin log hashes were verified read-only again for the correction
below. Worker ref mutations remain prohibited. Foundry owns review/finalization;
no landed SHA is claimed.


# Truncated permanent HTTP public boundaries — October 10 correction

This evidence extends delivered Kotlin `196db84b842bf4a1e4940411fed6c65dd9b7293e`.
Production behavior remains unchanged. The permanent-status exclusion in
`ResponseEvidence.httpReason` already satisfies the October 10 requirement.

`PermanentHttpBoundaryTest.truncatedPermanentAllPublicBoundaries` in the OpenAI
and Anthropic JVM test trees exercises default production transport against a
loopback server with truncated UTF-8 bodies for **400, 401 and 403**. Every policy
explicitly selects these statuses, enables transport retries, and supplies an
allowing admission hook. A distinguishable second response makes an unauthorized
resend observable. The fixtures record request method/path and exact body bytes.

| Adapter | Actual public entrypoints |
| --- | --- |
| Ollama, oMLX, OpenAI | `complete`, `completeJson`, `completeJsonResponse`, `stream`, `streamEvents`; broker `complete`, `generateResponse`, `completeJson<T>`, `stream`, `generateStreamEvents`; session `send`, `stream` |
| Anthropic | `complete`, `completeJson`, inherited `completeJsonResponse`, `stream`; broker `complete`, `generateResponse`, `completeJson<T>`, `stream`; session `send`, `stream` |

The matrix has 138 cases: 108 for the three local/OpenAI adapters and 30 for
Anthropic. Each checks exact operation and frozen expected request bytes, including
structured schema and Anthropic forced-tool encoding, HTTP category, numeric
status, ineligibility, permanent reason, exact received headers, UTF-8 partial
bytes, exact raw progress, and zero semantic observation/delivery. The original
body-read `IOException` remains identical through private boundary inspection,
FAILED and terminal event failures and the single history entry; the public
recovery exception has no cause chain. Safe summaries/events/captures do not
expose the private response. Capture/lifecycle/failure identities agree. There is
one wire attempt, no success/admission scheduling, no admission invocation, no
tool execution, and no second request. Failed sessions roll back history.

An early real-boundary proof substituted Anthropic `stream` for structured
completion and failed on `structured` versus `stream`; restoring `completeJson`
passed with exact forced-tool bytes. A separate deliberate mutation removed
permanent-status exclusion and was rejected after unauthorized resend produced
multiple failures. Restoring the original production source passed. These were
negative controls, not production defects. Historical setup/fixture assertion
failures remain retained separately from those controls.

Rust was inspected read-only at `4ca1ed279c02eab37827a1ed07c30e961155ecf3`,
especially `tests/recovery.rs::truncated_permanent_http_bodies_never_retry_all_public_completion_apis`.
The Kotlin matrix checks its permanent-status, operation, private partial-body,
progress, one-history-entry, no-semantic-delivery and no-resend assertions, and
adds Anthropic, exact headers/request bytes and public broker/session callers.
Kotlin records STARTED, FAILED, EXHAUSTED; Rust records AttemptStarted and
AttemptFailed. This preserves characterized lifecycle differences.

Anthropic does not implement `StreamEventsGateway`, so `streamEvents` and broker
`generateStreamEvents` are unsupported there. Linux does not establish Apple
execution or Native transport guarantees: macOS/iOS validation remains explicitly
pending controller execution. Existing unrelated recovery exclusions remain.

Complete captures, negative-control snapshots/results, proof JSON, receipt/log
verification, exact revisions and source hashes are retained in the external
Foundry evidence archive
[/home/svetzal/.foundry/tool-logs/mojentic-kt-c10-196db84-evidence](/home/svetzal/.foundry/tool-logs/mojentic-kt-c10-196db84-evidence).
The suppression snapshot is outside worktree evidence. Git finalization remains
Foundry's responsibility; no worker ref mutations occurred.


Current Linux validation ran
`./gradlew ktlintCheck detekt build allTests apiCheck dokkaGenerate` with writable
copies of the host Gradle/Kotlin Native caches and in-process Kotlin compilation.
All six gates passed (exit 0); the changed modules ran 218 tests with zero
failures/errors. Existing realtime compiler and Dokka unresolved-link warnings
remain in the complete captures. The initial read-only global-cache failure is
retained as a host setup failure, not a source defect.

| Audit | Actual result |
| --- | --- |
| Configured `dependencyCheckAggregate --no-parallel` | Exit 0; 337 dependency entries; 0 unsuppressed findings; 68 existing suppressed findings |
| Unfiltered same full scope, diagnostic init removing suppressions | Exit 1; 337 dependency entries; 68 findings; 0 suppressions; 46 artifact/advisory findings at CVSS >= 7 across 7 distinct CVEs |

The unfiltered threshold failures are CVE-2008-0986, CVE-2012-2055,
CVE-2022-3171, CVE-2024-7254, CVE-2026-53914, CVE-2026-56740 and
CVE-2026-56741. Both runs also warn that the .NET assembly analyzer could not
initialize because `dotnet` is unavailable for scanned tool assets. Therefore
these reports do not establish clean unfiltered security or complete assembly
analysis. Audit feeds were checked before execution: the NVD modified feed
reported `2026-10-10T20:00:06-04:00`; reports record the same modified timestamp.
No dependencies, suppression rules, audit scope, severity thresholds, runtime
pins, package versions, release files or production behavior were changed.

The independent focused reviewer approved the corrected source and assertions,
verified the behavioral proof and full-gate success, and distinguished that
approval from landing readiness. The failed unfiltered audit and pending Apple
validation remain explicit controller requirements. This is focused evidence,
not a claim that every mission gate or coordinated parity requirement passed.

## Cycle 11: supply-chain correction and remaining owner decisions

This security-only correction starts from clean
`fa73ac12a89a0a20140943c18cec4bfdf8c57cf9`. Read-only `ls-remote` confirmed
remote main at that revision. The six retained controller synchronization log
hashes matched their receipt; its historical Kotlin head was
`6379173f8a6eceeb48bd22883bf0f764546937cf`. No worker fetch, rebase, commit,
push, tag or ref mutation was performed. Release preparation and recovery
alignment remain paused for controller landing of the security change.

The change floors only `kotlin-stdlib`, `kotlin-stdlib-jdk7`,
`kotlin-stdlib-jdk8` and `kotlin-reflect` to the project's stable Kotlin 2.4.21
on `ktlint*`, `detekt*` and `androidLint*` configurations. It uses the existing
build-tool resolution mechanism. Published dependency declarations, production
recovery sources, public API baselines, fixtures, active suppressions, skipped
projects and the CVSS 7.0 threshold are unchanged. Runtime alignment is partial
remediation, not proof that a standard-library CPE match contains vulnerable
build-cache code or that the unfiltered audit is clean.

Before expanding documentation or running the full suite, an actual Gradle
resolution probe required the runtime artifacts in ktlint, Detekt and Android
lint configurations to resolve to 2.4.21 and passed (exit 0). A broader compiler
floor also resolved successfully but failed execution: ktlint 1.8.0 threw
`Extensions storage is not registered` during PSI initialization; Detekt 1.23.8
rejected Kotlin 2.4.21 because it was compiled with 2.0.21. Both rejected
attempts and their stack traces are retained. The final floor excludes compiler
replacement, without disabling compatibility checks. Detekt's
[official dependency guidance](https://detekt.dev/docs/1.23.8/gettingstarted/gradle/)
and [changelog](https://detekt.dev/changelog/) substantiate that coupling.

### Investigation of all seven blocking CVEs

| Advisory | Exact affected or matched artifact | Primary evidence, assessment and required action |
| --- | --- | --- |
| CVE-2026-53914 | `kotlin-compiler-embeddable`, `kotlin-daemon-embeddable`, `kotlin-script-runtime` 2.0.21 (Detekt) and 2.2.21 (ktlint); older Kotlin runtime artifacts | [JetBrains CNA record](https://cveawg.mitre.org/api/cve/CVE-2026-53914) identifies Kotlin before 2.4.20 and unsafe build-cache metadata deserialization. [Maven registry](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/maven-metadata.xml) confirms stable 2.4.21. Runtime floors apply; compiler replacement failed real tool execution. Owner must select compatible patched tooling or authorize the necessary tooling migration. |
| CVE-2026-53914: Dokka identity matches and pinned reflection | Dokka 2.2.0 `all-modules-page-plugin`, `analysis-kotlin-symbols`, `analysis-markdown`, `dokka-base`, `dokka-core`, `templating-plugin`; compiler-pinned `kotlin-reflect` 1.6.10 | Dokka artifacts are `org.jetbrains.dokka`, incorrectly inferred as the Kotlin 2.2.0 product by a LOW-confidence CPE. [Dokka registry](https://repo.maven.apache.org/maven2/org/jetbrains/dokka/dokka-core/maven-metadata.xml) lists stable 2.2.0 followed by 2.3.0-Beta. Separate compiler-pinned reflection matches remain reportable; the existing ABI-validation reflection exception was not expanded. Neither an unrelated Dokka version bump nor a new suppression is applied. Scanner applicability needs owner review. |
| CVE-2022-3171 | `com.android.tools.external.com-intellij:kotlin-compiler:32.4.1`, embedded `protobuf-java:2.6.1` | [Upstream advisory](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-h4h5-3hr4-j3g2) fixes Java parsing denial of service in 3.16.3, 3.19.6, 3.20.3 and 3.21.7, including regenerated code. Actual relocated `org/jetbrains/kotlin/protobuf/CodedInputStream.class` exists in the containing jar. A separate Protobuf constraint cannot replace those bytes. Owner must obtain a supported fixed containing tool or authorize a supported replacement. |
| CVE-2024-7254 | Same Android compiler, embedded `protobuf-java:2.6.1` | [Upstream advisory](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8) fixes nested-group recursion denial of service in 3.25.5, 4.27.5 and 4.28.2. The embedded runtime requires relocation-aware upstream remediation, not a normal dependency floor. Same owner decision as above. |
| CVE-2026-56740 | Same Android compiler, embedded JLine 3.24.1 including `jline-remote-telnet` | [Upstream advisory](https://github.com/jline/jline3/security/advisories/GHSA-47qp-hqvx-6r3f) fixes unlimited Telnet environment variables in 3.30.14, 4.0.16 and 4.2.1. Actual `TelnetIO` and `ConnectionData` classes are present. Other JLine module CPE matches are overbroad, but the Telnet component is present. No project Telnet-server exposure was observed; the affected code is build tooling. Owner must obtain a fixed containing tool or authorize a replacement. |
| CVE-2026-56741 | Same Android compiler and JLine 3.24.1 | [Upstream advisory](https://github.com/jline/jline3/security/advisories/GHSA-2r2c-cx56-8933) fixes unbounded Telnet terminal geometry in 3.30.14, 4.0.16 and 4.2.1. The same embedded-code limitation and owner decision apply. |
| CVE-2008-0986 | `com.android.tools.analytics-library:protos:32.4.1` | LOW-confidence Android SDK CPE identity match. The jar contains analytics Protobuf classes, not native `libsgl.so` BMP parsing. [Google's original SDK fix announcement](https://android-developers.googleblog.com/2008/03/android-sdk-update-m5-rc15-released.html) concerns SDK m5-rc15. This generated analytics jar is unrelated. Documented for scanner/owner review, with no suppression or unrelated upgrade. |
| CVE-2012-2055 | `io.github.oshai:kotlin-logging-android:8.0.4` AAR | GitHub organization metadata was matched to the GitHub product CPE. The [CNA record](https://cveawg.mitre.org/api/cve/CVE-2012-2055) concerns GitHub Enterprise public-key mass assignment before 20120304. The Kotlin logging artifact is unrelated to that web service; no equivalent web model or route exists in the AAR. Documented for scanner/owner review, without suppressing the finding. |

Component fixes exist for Protobuf and JLine. However, the primary Google
[compiler registry](https://dl.google.com/dl/android/maven2/com/android/tools/external/com-intellij/kotlin-compiler/maven-metadata.xml)
and [lint registry](https://dl.google.com/dl/android/maven2/com/android/tools/lint/lint/maven-metadata.xml)
list 32.4.1 as the latest stable containing releases; newer 32.5.0 versions are
alpha. Similarly, [Detekt's stable registry](https://repo.maven.apache.org/maven2/io/gitlab/arturbosch/detekt/detekt-cli/maven-metadata.xml)
ends at 1.23.8, and [ktlint's registry](https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/maven-metadata.xml)
ends at 1.8.0. These are supported-tooling blockers, not claims that the
advisories have no fixed release. No advisory was accepted, ignored or added
to a suppression list. Landing readiness requires owner resolution of these
blockers and a passing unfiltered full-scope audit.

### Audit reproduction and retained failures

| Full configured scope | Exit | Dependencies | Active findings | Existing suppressed findings | CVSS >= 7 matches |
| --- | --- | --- | --- | --- | --- |
| Fresh baseline, configured policy | 0 | 334 | 0 | 68 | 0 active |
| Fresh baseline, same scope with suppressions removed diagnostically | 1 | 334 | 68 | 0 | 46 across seven CVEs |
| Corrected runtime floor, configured policy | 0 | 328 | 0 | 55 | 0 active |
| Corrected runtime floor, same unfiltered diagnostic | 1 | 328 | 55 | 0 | 37 across the same seven CVEs |

Each audit ran `dependencyCheckAggregate --no-parallel`; the diagnostic init
script clears suppressions for that execution only. No audit configuration was
narrowed. The reduced entry count follows consolidation of older runtime
artifacts, not reduced scan scope. The pre-run NVD modified feed metadata was
`2026-10-10T20:00:06-04:00`, matching the retained cycle-10 transport baseline.
Both audit variants warn that .NET assembly analysis could not initialize
because `dotnet` is unavailable. These results establish neither complete
assembly analysis nor clean unfiltered security. The original cycle-10 reports
and all four fresh reports remain retained outside disposable worktrees.

The initial six-gate run with `--rerun-tasks` exited 1: Anthropic's
`PermanentHttpBoundaryTest.truncatedPermanentAllPublicBoundaries` retained the
expected body evidence but encountered an `IllegalStateException` where the
unchanged assertion requires `IOException`. The unchanged isolated real-socket
test subsequently exited 0. That intermittent failure's cause remains
unresolved; its original XML and full logs are preserved, not replaced by the
passing result. No assertion was relaxed and no recovery redesign was applied.

The second full six-gate attempt used `--no-parallel`, in-process Kotlin
compilation and an external init script forcing JVM/Android test execution
without recompiling unchanged Native artifacts. It also exited 1: oMLX's
`PermanentHttpBoundaryTest.truncatedPermanentAllPublicBoundaries` encountered
the same `IOException`/`IllegalStateException` cause-type assertion mismatch.
Both failures remain blockers for `build`/`allTests` acceptance. A passing
focused Anthropic rerun does not establish full-suite success or explain these
failures. No production or test-source change was made to mask them.

`publishToMavenLocal` passed (exit 0), using a writable temporary local Maven
repository because the host's normal home repository is read-only. All 36 POMs
and 36 Gradle module files were inspected and archived with hashes. Coordinates
remain `com.vetzal.mojentic`, version 2.1.0. The consumer metadata includes
Kotlin stdlib 2.4.21 and the unchanged public libraries; it contains no audited
compiler, Dokka, Detekt, Android compiler, Protobuf or JLine tool dependency.
The kotlin-logging scanner identity mismatch remains separately documented.
This consumer inspection does not waive build-tool findings or prove Apple
runtime conformance.

Independent focused review verified the final runtime-only floor's scope,
primary fix evidence, unsupported compiler replacements, and unchanged
production-boundary assertions. Existing acceptance suites collectively retain exact request bytes across
retries, distinct attempt identities, original private causes, lifecycle ordering,
permanent truncated HTTP failures, real ordinary broker/session calls, and
completed tools executing once. Adapter coverage was not expanded or newly
certified. Hash checks confirm all 213 tracked Kotlin source/test/API files and the
active suppression policy are unchanged. Review approval is for this partial
security change, not passing mission gates or controller landing readiness.

Apple validation is explicitly pending: Linux disables simulator execution and
framework linking. Native compilation and local publication do not replace
macOS/iOS runtime validation. No release, PR, sibling edit, live inference,
experiment restart or worker Git finalization occurred.

Original and corrected audit reports, complete capture stdout/stderr, command
and exit-code manifests, resolved artifact/configuration paths, primary upstream
snapshots, controller receipts, source revisions, source/report SHA-256 hashes,
failed and passing test evidence, direct dependency-resolution proof and review
records are retained outside disposable worktrees. Suppression evidence was not
copied into worktree evidence directories. Foundry must resolve the security,
full-suite and Apple blockers before landing the focused security change on
main and resuming recovery alignment.

| Final validation | Actual result |
| --- | --- |
| `ktlintCheck detekt apiCheck dokkaGenerate --no-parallel` | Exit 0 after the final runtime-only floor |
| `ktlintCheck detekt build allTests apiCheck dokkaGenerate --rerun-tasks` | Exit 1, Anthropic permanent-response cause-type assertion |
| Same six gates, serialized with fresh JVM/Android tests | Exit 1, oMLX permanent-response cause-type assertion |
| Runtime dependency-resolution acceptance | Exit 0; actual ktlint/Detekt/Android lint runtime artifacts resolve to 2.4.21 |
| Configured / unfiltered full-scope audit | Exit 0 / exit 1; details above |
| `publishToMavenLocal` and consumer metadata inspection | Exit 0; 36 POMs and 36 module files inspected |

The correction is reviewable but incomplete: unfiltered security, full-suite
success and Apple validation remain required before controller landing.
