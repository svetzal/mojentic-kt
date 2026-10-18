# Current verification results

Tested source: Kotlin `357a6eab1e0a69b26aa93a1364309d7dbabb3c2a` plus the uncommitted corrections identified by
[source.sha256](source.sha256), SHA-256 `321d38616d1916b34aef0b21c470f85e471eb88c8d12a22d751cb818331e978d` (337 files).
Authority remains mojentic-unify `4ae2f73a0a5465b0f6509b87e1148d254f3f64cd`.
Production source, public API/dumps, shared contract decisions/fixtures,
dependencies, version **3.0.0** and release state are unchanged. No refs were changed.

## Timeout-fixture acceptance

`RecoveryTransportHttpTest.transportTimeoutRetainsCauseWithoutUnauthorizedResend`
passed (exit **0**) through the public gateway with the preserved in-process
`RecoveryScriptedTransport` / Ktor `MockEngine`. The injected exception remains
`java.net.SocketTimeoutException`, imported as `TransportTimeoutException` so its
construction is explicit fixture input. `assertSame` verifies the exact injected
cause instance for both ordinary and structured completion. Existing admission,
single-dispatch, payload, identity and lifecycle assertions remain intact.

This is non-behavioral test cleanup; direct acceptance evidence and log hashes are
recorded in [verification.json](verification.json). The preceding virtual-backoff
behavioral proof is retained there as prior evidence with its original results.

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
failures/errors/skips, for the preceding source `b420c1feb4d305e2df32f7f0d46814ef7a2eece3`, manifest
`ae586802575b0d91ff9f5e4f10a61c962c99f376e77d208aec52411245b99a6a`. It was not rerun for this test-only correction; production,
consumer sources and contracts are unchanged. Both Gradle
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
