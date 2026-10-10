# Opt-in completion recovery

Ordinary, structured and streaming OpenAI/Ollama/oMLX completions can recover one provider request.
Configure the existing `CompletionConfig`; `LlmBroker` and `ChatSession` pass it
through. Anthropic, embeddings and realtime do not use this policy.

```kotlin
val gateway = OllamaGateway()
val policy = RecoveryPolicy(
    maxAttempts = 3,
    budgetMillis = 60_000,
    admission = { failure ->
        // Your own suspending ownership/termination check, or an explicit decision.
        // Returning true authorizes another inference; socket closure does not.
        controller.mayResend(failure.identity.logicalId, failure.nextAttemptNumber)
    },
    observer = { event -> safeLifecycleStore.append(event) },
)
val config = CompletionConfig(recovery = policy)
val broker = LlmBroker(gateway)
val session = ChatSession(broker, model = "your-model", config = config)
try {
    session.send("Your prompt")
} catch (failure: RecoveryException) {
    println(failure) // Stable reason and actual wire count; no response/cause text.
    // Explicit inspection is sensitive and belongs only in caller-owned storage.
    controller.inspect(failure.failures.last().inspectCause())
} finally {
    gateway.close()
}
```

Use `OpenAIGateway(apiKey = yourApiKey)` for OpenAI Chat Completions, or
`OmlxGateway(host = "http://localhost:8000")` for oMLX. For structured calls,
pass the same config to `broker.completeJson<MyResult>(model, messages, config)`
or `gateway.completeJsonResponse(model, messages, schema, config)`. Request
encoding happens once per logical completion, preserving existing history,
controls, tools and schema for every admitted attempt. A broker tool call starts
another logical completion; recovery never executes a tool or resets its depth.
IDs identify library attempts and provide no server idempotency guarantee.

Leaving `recovery = null` preserves legacy success/error handling. Setting
`RecoveryPolicy()` opts into safe reports while still allowing only one attempt.
Opt-in failures use `RecoveryException`, independently of the legacy
`LlmGatewayException`. An opt-in cancelled operation throws
`RecoveryCancellationException`, a coroutine `CancellationException`, with the
failed actual attempts available through `failures`. Cancellation must propagate;
do not catch it to restart a session.

A missing admission hook refuses every resend. A suspending hook remains pending
until it returns an explicit Boolean; `false` rejects. Cancellation interrupts
active requests, admission and backoff. Policy duration budgets start at the first
failure and use a monotonic clock. Absolute deadlines use wall time. Both constrain
admission, delay and retry start, and do not impose a timeout on healthy generation.
Admission waiting is bounded when a budget or deadline is supplied.

Full jitter samples from zero through the exponential ceiling, capped without
overflow. Numeric and HTTP-date Retry-After are minimum delays; invalid values use
policy backoff. A minimum outside the delay ceiling or remaining budget refuses
recovery instead of shortening that minimum. A known 400/401/403 stays permanent
when reading its body fails. Malformed responses and every partial successful non-streaming body
are terminal, even with explicit admission. Observed reasoning, content and tool
fields are tracked before capture. Raw bytes, including whitespace, are separate
from semantic evidence; no output is delivered until non-streaming decoding succeeds.

Raw capture is a separate opt-in:

```kotlin
val policy = RecoveryPolicy(
    maxAttempts = 2,
    admission = { controller.mayResend(it.identity.logicalId, it.nextAttemptNumber) },
    capture = { wire ->
        // Sensitive bytes: caller owns retention, encryption and access controls.
        wireStore.append(
            wire.identity,
            wire.inspectRequest(),
            wire.inspectResponse(),
            wire.inspectHeaders(),
            wire.complete,
        )
    },
)
```

Capture snapshots include the encoded request, progressive response bytes and one
terminal response snapshot. A null response denotes pre-send request capture.
`complete` marks the terminal snapshot, including partial failed reads. Request
headers deliberately omit the bearer credential. Response headers and bytes may
contain secrets. Capture failures are terminal and never trigger another request;
request-capture failure has zero wire attempts. `inspectBoundaryCause` preserves an
original request failure when terminal capture also fails. Default formatting and
lifecycle records contain no body text, arbitrary provider metadata or cause chain.
Safe `failure.providerCode` and `failure.providerRequestId` also appear in
`failure.summary()`, `exception.summary()` and `event.summary()` histories.
For example, a received `server_error` with
`X-Request-ID: req_123e4567-e89b-12d3-a456-426614174000` survives unchanged
when neither value occurs in the request or bearer credential. Use the summary
for ordinary logging; use `inspectBytes()`, `inspectHeaders()` and `inspectCause()`
only in an explicitly authorized private evidence store.

Codes are restricted to `rate_limit_exceeded`, `server_error`,
`invalid_request_error`, `invalid_api_key`, `model_not_found`, `insufficient_quota`
and `overloaded_error`, from a JSON error object's string `code`. Request IDs
come only from an unambiguous `X-Request-ID` UUID, optionally prefixed by `req_`.
Unknown strings, malformed values and echoed request/credential values are omitted,
even when the echoed ID has valid UUID syntax. The projection also checks decoded
JSON request strings, so escaping does not make a payload echo safe. No ID is
inferred from a response message; Ollama responses without this header have no ID.
Validation does not prove that the server is trusted or that inference ended.
Do not serialize explicit inspection results into ordinary application logs.

Default JVM/Android recovery clients disable OkHttp connection retries and all
redirects. Caller-supplied engines own their internal retry/redirect settings and
must meet the same contract. Native transport conformance is pending Apple
controller validation; see [RECOVERY-CONFORMANCE.md](../../RECOVERY-CONFORMANCE.md).
The legacy oMLX timeout still applies outside the dedicated recovery client.
No provider ownership, termination or idempotency facility is claimed by this slice.

## Streaming migration

Pass the same config to `gateway.stream`, `broker.stream` or `session.stream`.
These APIs throw `RecoveryException` on opt-in interruption or exhaustion. A
failure after observed reasoning, content or tool fragments is terminal even
when capture prevented delivery. Check `failure.failures.last().progress` and
`failure.reason`; never append a new attempt to an interrupted response.

```kotlin
try {
    session.stream("Your prompt").collect { event -> render(event) }
} catch (failure: RecoveryException) {
    safeFailureStore.append(failure.summary())
    renderInterrupted() // Already-rendered text is partial evidence.
}
```

For tool-free terminal evidence use `broker.generateStreamEvents(model,
messages, config)`. Its terminal `Error(RequestFailed(...))` contains the safe
`RecoveryException`; inspect its cause explicitly to obtain bounded attempt
history. It never executes tools. `Completed` requires Ollama `done: true` with
`done_reason: "stop"`, or OpenAI/oMLX normal finish plus `[DONE]`. EOF, a keepalive or
local socket closure never proves completion or remote inference termination.

A keepalive-only transport failure can recover after explicit caller admission.
The encoded request is identical across admitted attempts, including all history
supported by the existing message model. Native reasoning-history fields remain
unsupported. The broker collects gateway tools before dispatching them; recovery
cannot replay tools. The session commits history only on success and restores
its pre-turn snapshot after failure.

Recovery-enabled flows emit directly, without an internal producer buffer.
A consumer suspended on content or reasoning pauses completion finalization.
Cancellation during the attempt closes the
owned response, records the actual failed attempt and one terminal `CANCELLED`,
and cannot produce `SUCCEEDED` afterward. Tool-free `Completed` is delivered
only after recovery finalizes and closes the response; cancellation of its
collector afterward adds no attempt lifecycle. Caller-added buffering has the normal
Kotlin Flow semantics and can allow upstream completion before downstream work.

Safe `PROGRESS` and `METRICS` lifecycle events contain the wire identity,
one-based validated frame index, cumulative UTF-8 semantic byte counts and
numeric provider counts/durations only. Valid Ollama length termination reports
progress then metrics before failure. Missing metrics remain absent. Malformed
frames produce no fabricated telemetry. Explicit capture retains exact body
bytes, including line separators; capture observes semantic evidence before
calling the hook and any hook failure is terminal.

Escaped JSON keys count as semantic evidence, including when received across
multiple HTTP reads. Either observed semantics or delivered semantics independently
blocks resend after interruption. Observed raw bytes retain their exact wire
length; semantic byte counts use decoded UTF-8 from complete JSON frames.
Incomplete semantic prefixes conservatively block replay without estimating
content or reasoning byte counts. A capture hook that fails before delivery
retains observed progress and its original private cause, with zero delivered
progress. Completed-tool evidence survives later wire reads. Consult
[the conformance packet](../../RECOVERY-CONFORMANCE.md#semantic-replay-correction)
for scripted boundary assertions and pending Apple validation.

## Completion adapter capabilities

| Adapter | Ordinary / structured / stream / streamEvents recovery | Cancellation, status, idempotency |
| --- | --- | --- |
| OpenAI Chat Completions | Opt-in on all four paths; event stream remains tool-free | No endpoint-specific facility established; unknown for custom hosts |
| Ollama | Opt-in on all four paths | Ownership and termination unknown; caller admission required |
| oMLX | Opt-in on all four paths | Ownership and termination unknown; caller admission required |
| Anthropic | Policy not implemented | Not investigated in this slice |
| Embeddings / realtime / model management | Outside completion recovery | Not investigated in this slice |

OpenAI uses its existing model registry: supported reasoning models omit temperature
and use `max_completion_tokens` with `reasoning_effort`; other models retain
`temperature` and `max_tokens`. Unsupported controls are not invented. The semantic
request bytes, schema and adapted messages are frozen before the first attempt.
Recovery streams request `stream_options.include_usage`; validated terminal usage,
provider model and finish reason survive into tool-free completion evidence.
Opt-in structured responses also retain the provider's available reasoning text;
legacy structured responses keep their existing behavior. No additional native
reasoning-history representation is introduced.

The hook is required for OpenAI as well as compatible custom hosts. A local client
ID is not an idempotency key. This implementation neither sends a provider
idempotency key nor calls a provider cancellation/status API. Socket cancellation
closes client-owned resources; the caller must decide whether another request is
safe. No inference status or remote termination claim follows from a 503, 504 or EOF.
Exact capture refers to bytes Ktor receives after the engine's normal HTTP decoding,
not compressed transport frames. Caller-supplied codecs and engines must preserve
these request, capture and retry guarantees.

Interrupted OpenAI event streams retain their last validated completion telemetry
in `failure.failures.last().inspectCompletionEvidence()`. This explicit inspection
can contain provider values; safe failure summaries and lifecycle events continue
to exclude arbitrary provider text. Broker tracing retains reported usage/model/
finish reason even on EOF or length termination, while the terminal Error keeps
the original `RecoveryException` and attempt history. Opt-in cancellation propagates
as coroutine cancellation through both gateway and broker event paths.

Validated metadata uses the shared transport for all three completion adapters.
Provider codes in JSON, NDJSON and SSE error objects are projected conservatively;
missing, ambiguous or unknown values remain absent. Realtime and embeddings are
outside this completion metadata correction. Native execution remains pending
Apple validation; no stronger Linux-only capability claim is made.

Validated metadata uses the shared transport for all three completion adapters.
Provider codes in JSON, NDJSON and SSE error objects are projected conservatively;
missing, ambiguous or unknown values remain absent. Realtime and embeddings are
outside this completion metadata correction. Native execution remains pending
Apple validation; no stronger Linux-only capability claim is made.
