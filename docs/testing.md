# Deterministic test boundary inventory

Reviewed against mojentic-unify authority `4ae2f73` and preserved Kotlin migration
base `b420c1feb4d305e2df32f7f0d46814ef7a2eece3` plus the corrections recorded in
[the current verification results](release-readiness/results.md).
This inventory covers all former test socket/time dependencies, their callers,
and test scripts. Tests drive the public API with a transport or gateway double;
no test starts a server, opens a socket or calls a live service. Delay-based tests
run under `runTest`; assertions about time use injected clocks or explicit instants.

| Former dependency | Replacement and retained proof |
| --- | --- |
| Three `RecoveryScriptedServer` copies (OpenAI, Anthropic, distribution) | `RecoveryScriptedTransport`: injected Ktor `MockEngine`, exact encoded outgoing bytes/method/path/headers, contract-derived status/entity bytes and concrete response headers. No listening/accept threads. All callers listed below migrated together. |
| Socket body truncation and two `TruncatedResponseEngine` polling loops | An in-process `ByteReadChannel` exposes partial bytes then the original failed EOF. Coroutine release signals establish capture before closure; observers inspect the established IOException directly. No polling, deadline loop or real sleep. Permanent-status matrices retain private cause identity, exact headers and UTF-8 evidence. |
| Byte-at-a-time socket acknowledgements | Scripted channel supplies exactly one byte per read; semantic/capture/delivery assertions stay intact. No semaphore or thread. |
| Socket close latches and worker joins | Coroutine signals observe actual body cancellation and Ktor call-context disposal; pending reads resume on cancellation. Active/admission/backoff and paused/session/terminal collector cases retain lifecycle and rollback assertions. |
| Independently socket-backed `OllamaRecoveryProofTest` | Module-local in-process scripted transport, explicit failed EOF released by capture. Same permanent-status precedence, exact body/private cause, observed-versus-delivered and no-replay assertions. |
| `OmlxRealEngineTest` JDK HTTP server | Renamed `OmlxTransportTest` retains all four model/auth, ordinary response, load and broker stream evidence assertions with recorded fixtures through `OmlxTestServer`. JDK response framing/chunked transfer helpers removed; those helpers had no library decision beyond the retained parsed results and auth assertions. |
| `RecoveryTransportHttpTest` compressed/real-socket cases | Supply the decoded entity/headers at the engine boundary; retain exact 503 body/Retry-After/capture/no-hidden-resend assertions. Synthetic 11,000 ms clock advance proves a healthy generation outlives recovery budget. Injected SocketTimeoutException (aliased as TransportTimeoutException) proves exact cause-instance retention for ordinary and structured completion, admission/no resend. Native compression and socket scheduling belong to the boundary engine, not library decision logic. |
| JVM `runBlocking` fixture callers | `runTest` with coroutine signals and injected recovery clock/sleep/jitter where time affects decisions. No executor, wall-clock threshold or real backoff. |
| Three legacy stream first-chunk handshakes using `Dispatchers.Default` | Writer and collector run on the test scheduler; MockEngine dispatch uses that scheduler. First chunk must reach collector before the terminal bytes are supplied; timeout is virtual. |
| OpenAI/Ollama stream-cancellation writers on `Dispatchers.Default` | Test-scheduler writers and collectors; existing delays are virtual. Body closure and writer completion prove early consumer cancellation. |
| oMLX split-body test on `Dispatchers.Default` | Writer/collector use the test scheduler; split keepalive body still yields the exact incomplete-stream error. |
| `EventStoreTest` wall-clock window | Explicit timestamped events before/on/after boundaries; exact inclusive selection and same-instant boundary asserted. No current-time read. |
| Timing configuration comparison using `TimeSource.Monotonic` | Two injected monotonic sources; exact request/header equality still proves changing timing instrumentation leaves payloads unchanged. |
| `ClientStreamTimingTest`, date resolver and parallel tools | Already use test scheduler/abstract time source/fixed clock. Their delays are virtual, and no wall-clock limits are asserted. |
| Agent dispatcher polling tests | `runTest` drives coroutine poll delays; assertions concern queue/routing/termination, not elapsed wall time. |
| Other provider/core/realtime/websearch tests | Ktor MockEngine, fake gateway, fake filesystem or immutable model tests; endpoint strings are inputs/assertions only. No real service/credential or registry lookup. Constructor-only capability tests do not perform I/O. |
| `tools/verify-distribution.sh` registry resolution | Both Gradle invocations are offline. Fresh local Maven publications only; independent consumer uses coordinates and an exclusive file repository, asserts resolved artifact file provenance and hashes, and uses the same pinned executable contracts. Transitive/plugin inputs must already be cached. |
| `tools/inspect-distribution.py` | Reads local POM/module/JAR/AAR files and computes hashes. POM namespace URI is an XML identifier, not a connection. |
| GitHub Actions jobs | Every build/docs/deploy/security/release job has `timeout-minutes`; no job can wait indefinitely. Build-tool installation/auditing/publication workflows are not test fixtures. |

## Migrated callers of shared fixtures

Each listed caller retains its public behavioral assertions; changing the transport
does not replace them with row counts or schema-only checks.

- `mojentic-anthropic`: `AdmissionAmendmentHttpTest`, `AnthropicRecoveryEvidenceTest`, `AnthropicRecoveryHttpTest`, `AnthropicRecoveryProofTest`, `InvalidRecoveryPolicyHttpTest`, `PermanentHttpBoundaryTest`, `RequestFidelityContractTest`.

- `mojentic-openai`: `AdmissionAmendmentHttpTest`, `ClientStreamTimingHttpTest`, `CompletionRecoveryHttpTest`, `ContextWindowContractTest`, `ContextWindowProofTest`, `ContextWindowRequestFidelityTest`, `FirstDispatchTimingProofTest`, `InvalidRecoveryPolicyHttpTest`, `NativeReasoningCases`, `NativeReasoningContinuityTest`, `NativeReasoningControlsTest`, `NativeReasoningFailureTest`, `NativeReasoningHttpProofTest`, `NativeReasoningRetryTest`, `NativeReasoningStructuredTest`, `OpenAIRecoveryPayloadTest`, `OpenAIRecoveryProofTest`, `OpenAIRecoveryTelemetryTest`, `DeterministicRecoveryProofTest`, `PermanentHttpBoundaryTest`, `RecoveryMetadataHttpTest`, `RecoveryTransportHttpTest`, `RequestFidelityContractTest`, `RetryEligibilityHttpTest`, `StreamingRecoveryHttpTest`.

- `tests/distribution`: `FirstDispatchTimingProofTest`, `PackagedApiTest`.

## Review and execution

Search all `mojentic-*/src/*Test`, `tests/` and test scripts for socket/server
constructors, connection calls, real sleeps, wall-clock reads/thresholds and
non-test dispatchers when reviewing changes. Follow every gateway construction
to verify it injects an engine or performs no I/O. Local tests and packaged tests
must preserve exact identifier relationships; do not mask IDs to make assertions
pass. The decision mappings are in `RECOVERY-CONFORMANCE.md` and
`CONFORMANCE-3.0.0.md`. Fresh execution and remaining gaps are reported in
`docs/release-readiness/results.md`; historic counts do not validate new code.

## Acceptance proof correction

The focused deterministic recovery proof uses the shared OpenAI transport on the
test scheduler. Its injected sleeper must advance virtual time by the requested
100 ms; merely recording the delay is rejected. Success content stays `ok` in
both runs. Exact literal request bytes, per-attempt transport captures, full
identities, original failure/cause retention, lifecycle order and privacy are
asserted through public API results and observers. See the current verification
record for the actual rejection and correction. The previous response-content
toggle is not valid acceptance evidence. Module-local Anthropic/Ollama helpers,
all existing shared-transport callers and the independent consumer are preserved.
