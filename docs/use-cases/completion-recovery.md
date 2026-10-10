# Opt-in completion recovery

Ordinary and structured Ollama/oMLX completions can recover one provider request.
Configure the existing `CompletionConfig`; `LlmBroker` and `ChatSession` pass it
through. Streaming, OpenAI, Anthropic, embeddings and realtime do not use this policy.

```kotlin
val gateway = OllamaGateway()
val policy = RecoveryPolicy(
    maxAttempts = 3,
    budgetMillis = 60_000,
    admission = { failure ->
        // Your own suspending ownership/termination check, or an explicit decision.
        // Returning true authorizes another local inference; socket closure does not.
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

Use `OmlxGateway(host = "http://localhost:8000")` for oMLX. For structured calls,
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
when reading its body fails. Malformed responses and every partial successful body
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
Do not serialize explicit inspection results into ordinary application logs.

Default JVM/Android recovery clients disable OkHttp connection retries and all
redirects. Caller-supplied engines own their internal retry/redirect settings and
must meet the same contract. Native transport conformance is pending Apple
controller validation; see [RECOVERY-CONFORMANCE.md](../../RECOVERY-CONFORMANCE.md).
The legacy oMLX timeout still applies outside the dedicated recovery client.
No provider ownership, termination or idempotency facility is claimed by this slice.
