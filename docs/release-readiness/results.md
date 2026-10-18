# Current verification results

Tested source: Kotlin `b420c1feb4d305e2df32f7f0d46814ef7a2eece3` plus the uncommitted corrections identified by
[source.sha256](source.sha256), SHA-256 `ae586802575b0d91ff9f5e4f10a61c962c99f376e77d208aec52411245b99a6a` (337 files).
Authority remains mojentic-unify `4ae2f73a0a5465b0f6509b87e1148d254f3f64cd`.
Production source, public API/dumps, shared contract decisions/fixtures,
dependencies, version **3.0.0** and release state are unchanged. No refs were changed.

## Corrected behavioral acceptance

The public `OpenAIGateway.complete` probe uses the preserved shared
`RecoveryScriptedTransport` with `StandardTestDispatcher`, injected epoch and
monotonic clocks, and an injected sleeper. The rejecting run (exit **1**) uses
a sleeper that records the delay without advancing virtual time. It fails:

```kotlin
assertEquals(100L, testScheduler.currentTime, "Recovery backoff must advance the injected clock by 100 ms")
// expected: 100; actual: 0
```

The corrected run (exit **0**) adds `testScheduler.advanceTimeBy(it)` to that
sleeper. Both runs keep exactly the same 503 `private-failure` and 200 `ok`
response bytes. This detects an injected-time fixture defect: a recorded delay
alone does not prove backoff advances the monotonic clock. The earlier
`incorrect`-to-`ok` toggle is withdrawn as acceptance evidence.

The passing assertions verify literal serialized request bytes on both attempts
and every capture; full stable logical IDs and distinct attempt IDs with attempt
numbers; exact lifecycle order; response bytes/status bound to each identity;
original failure bytes, the same failure object retained through success, and
the original cause object; 100 ms virtual backoff; no raw request/failure/token
in formatted events, summaries or wires; no authorization in captured headers.
No identifiers are masked and no library internals are mocked. The actual proof
logs are archived by Foundry; their hashes and command/exit results are in
[verification.json](verification.json).

## Library/provider gates

All six required gates passed with `GRADLE_USER_HOME=/tmp/mojentic-no-network-gradle`:

| Command | Exit |
| --- | --- |
| `./gradlew ktlintCheck --offline` | 0 |
| `./gradlew detekt --offline` | 0 |
| `./gradlew build --offline` | 0 |
| `./gradlew allTests --offline` | 0 |
| `./gradlew apiCheck --offline` | 0 |
| `./gradlew dokkaGenerate --offline` | 0 |

JVM/Android host XML reports **926 executions in 169 suites**,
zero failures/errors/skips. Counts describe execution only. Behavioral mappings
are [recovery conformance](../../RECOVERY-CONFORMANCE.md),
[3.0.0 conformance](../../CONFORMANCE-3.0.0.md) and [testing.md](../testing.md).
All existing shared-transport callers, module-local Anthropic/Ollama helpers and
independent consumer sources retain their assertions and coverage.

## Independent packaged consumer

`tools/verify-distribution.sh` passed (exit **0**) with **7 tests**, zero
failures/errors/skips, for the same tested source identity above. Both Gradle
invocations use `--offline`; candidate artifacts are freshly published into
`build/distribution-verification/candidate.9ibXim/maven`.
The unchanged consumer's `verifyCandidateResolution` verifies each actual resolved
file is under that fresh exclusive local file repository and has version 3.0.0.
No registry resolution or project/composite/source substitution occurs.
Every copied consumer source was compared byte-for-byte with this worktree;
consumer source manifest SHA-256: `b97fee40c8408ca8ec1472589448b9c52af0ea4efed608ac0a66763fa3bb0135`.

| Shared executable contract | SHA-256 | Library/provider | Packaged consumer |
| --- | --- | --- | --- |
| context-window-v1 | `9327502962112a020bafb46c6a677366df732f5a29cd44c92f17e8f632281395` | passed | passed |
| native-reasoning-v1 | `d5539eb334b69e0f8b698e8cd2c1402772dfabd09af92c652e1d1dc35f34e127` | passed | passed |
| request-fidelity-v1 | `6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea` | passed | passed |

Contracts and their pinned identities are unchanged. Both sides verify the same
executable cases, including errors, optional fields, payloads, reasoning/tool
history, stream completion and exact dispatch timing. Broader privacy,
cancellation, lifecycle and compatibility cases remain in the library gates.

All 18 root/JVM/Android coordinates (90 artifact files) were inspected.
[artifacts.sha256](artifacts.sha256) SHA-256: `ec849511992c980d20d8a2e957ae9c7aabf6f6c422725db19b01db8d4f3df91d`.
[consumer-runtime.txt](consumer-runtime.txt) SHA-256: `1d49e3ebb064cdf4c11490542f584f163f268e1f91de9c4e33323caa39c48101`.
Actual resolved file provenance is recorded in `verification.json`.

| Resolved JVM artifact | SHA-256 |
| --- | --- |
| `com.vetzal.mojentic:mojentic-anthropic-jvm:3.0.0` | `19bfd0778777b70f54fdca33ec92b2baa7d22a3b6df82ba1d82aca756faf011a` |
| `com.vetzal.mojentic:mojentic-core-jvm:3.0.0` | `fb61a2d026f9f9452fa4b8963151dcd473b7a32ab9417db034881455c8897919` |
| `com.vetzal.mojentic:mojentic-ollama-jvm:3.0.0` | `d3ae5b82a33fd3e9eb4bab46d7b3f420ce3da95152e6ee5a58462f29ddc662ec` |
| `com.vetzal.mojentic:mojentic-openai-jvm:3.0.0` | `3522bec91a2e1834b74731c702e0a700f24b971a0efb2131699414d3c5833695` |
| `com.vetzal.mojentic:mojentic-realtime-openai-jvm:3.0.0` | `d7066e9af730db930c2def891bb7a1ec6acb48d2ab4b9b60172cbab36414f3ca` |
| `com.vetzal.mojentic:mojentic-websearch-serpapi-jvm:3.0.0` | `034177291246286c73ae14cea7217417d2b20101d66059f05837cf6e6d106ed9` |

## Limits

The dependency audit record is explicitly inherited from the preserved migration;
it was not rerun for this correction, which changes no dependencies, plugins or
suppressions. Existing Gradle/Android/Dokka warnings remain. No live provider,
Android device or hosted CI verification is claimed. `ci-base.json` is historical
and does not validate this worktree. Test scripts remain offline; review found
no socket/server/real-sleep/wall-clock-threshold additions. The socket-timeout
exception in the transport tests is injected evidence, not a socket operation.
