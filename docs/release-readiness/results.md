# Current verification results

The tested implementation is Kotlin `6d0de37f6520a77fb5f81b7f03e8bc263b97ba1b` plus the uncommitted test/build/docs
changes identified by [source.sha256](source.sha256), SHA-256
`56fd0cee163515e0f91e0aa78c418e753f91ccdc977bc6c2ff2d196d6cb84fdb` (337 files). The authority is read-only mojentic-unify
`4ae2f73a0a5465b0f6509b87e1148d254f3f64cd`. Production source, public API/dumps, version and release
state are unchanged. The existing candidate version is **3.0.0**; this task did
not bump, tag, commit, push or publish remotely.

## Library/provider verification

All six required commands passed on this worktree, with Gradle offline:

| Actual command | Exit |
| --- | --- |
| `./gradlew ktlintCheck --offline` | 0 |
| `./gradlew detekt --offline` | 0 |
| `./gradlew build --offline` | 0 |
| `./gradlew allTests --offline` | 0 |
| `./gradlew apiCheck --offline` | 0 |
| `./gradlew dokkaGenerate --offline` | 0 |

Fresh XML contains **926 executions in 169 suites**,
zero failures/errors/skips, across JVM and Android host tests. These are execution
counts, not unique tests or a behavioral coverage argument. Concrete assertions
are mapped decision by decision in [recovery conformance](../../RECOVERY-CONFORMANCE.md)
and [parts 1–3 conformance](../../CONFORMANCE-3.0.0.md). The reviewed dependency
replacement inventory is [testing.md](../testing.md).

Before expanding the fixture migration or running the full suite, a public
OpenAIGateway probe rejected an in-process response containing `incorrect`
instead of `ok` (exit **1**), then passed after correcting that response (exit
**0**). It also verifies exact retry payloads, stable logical/distinct attempt
IDs, original failure bytes, ordered lifecycle, injected backoff and privacy.
Foundry retains the actual rejecting/corrected logs and validated behavioral
proof separately; [verification.json](verification.json) records their hashes.

## Independent packaged consumer

`tools/verify-distribution.sh` passed, exit **0**, publishing to a fresh local
file Maven directory and running a separate consumer with **7 tests**, zero
failures/errors/skips. Both Gradle invocations were offline. One missing cached
plugin jar was populated beforehand with a separate `help` configuration run;
that preparation was not verification. No registry calls occur in the
verification script. No project/composite/source substitution is used.

The consumer's copied source has SHA-256 `b97fee40c8408ca8ec1472589448b9c52af0ea4efed608ac0a66763fa3bb0135`; every copied file
was compared with the worktree source. It tested the same implementation commit
`6d0de37f6520a77fb5f81b7f03e8bc263b97ba1b` packaged from the worktree identified above.

| Shared executable contract | SHA-256 | Library/provider | Packaged consumer |
| --- | --- | --- | --- |
| context-window-v1 | `9327502962112a020bafb46c6a677366df732f5a29cd44c92f17e8f632281395` | passed | passed |
| native-reasoning-v1 | `d5539eb334b69e0f8b698e8cd2c1402772dfabd09af92c652e1d1dc35f34e127` | passed | passed |
| request-fidelity-v1 | `6f02088311f50707b9c12154d888f844928c23d359043cb2e5d5654bf7e5eaea` | passed | passed |

Both sides check these pinned identities and derive responses from them. The
consumer checks native reasoning/history/tool association, exact retry payloads
and parameter omission, discovery errors/optional fields, incomplete-stream
termination without replay, obsolete Retry-After forms and exact dispatch timing.
The broader cancellation, privacy, lifecycle and compatibility matrices remain
in the library gates. The contract JSON is unchanged by this migration.

All **18 root/JVM/Android coordinates**, represented by **90 artifact files**,
were inspected. [artifacts.sha256](artifacts.sha256) has SHA-256
`ec849511992c980d20d8a2e957ae9c7aabf6f6c422725db19b01db8d4f3df91d`; [consumer-runtime.txt](consumer-runtime.txt)
has SHA-256 `1d49e3ebb064cdf4c11490542f584f163f268e1f91de9c4e33323caa39c48101`. The consumer's
`verifyCandidateResolution` checks the actual files lie under the fresh repository
and have the expected version. Actual absolute execution paths are retained as
resolution provenance in `verification.json`; the build artifacts are disposable.

| Resolved JVM artifact | SHA-256 |
| --- | --- |
| `com.vetzal.mojentic:mojentic-anthropic-jvm:3.0.0` | `19bfd0778777b70f54fdca33ec92b2baa7d22a3b6df82ba1d82aca756faf011a` |
| `com.vetzal.mojentic:mojentic-core-jvm:3.0.0` | `fb61a2d026f9f9452fa4b8963151dcd473b7a32ab9417db034881455c8897919` |
| `com.vetzal.mojentic:mojentic-ollama-jvm:3.0.0` | `d3ae5b82a33fd3e9eb4bab46d7b3f420ce3da95152e6ee5a58462f29ddc662ec` |
| `com.vetzal.mojentic:mojentic-openai-jvm:3.0.0` | `3522bec91a2e1834b74731c702e0a700f24b971a0efb2131699414d3c5833695` |
| `com.vetzal.mojentic:mojentic-realtime-openai-jvm:3.0.0` | `d7066e9af730db930c2def891bb7a1ec6acb48d2ab4b9b60172cbab36414f3ca` |
| `com.vetzal.mojentic:mojentic-websearch-serpapi-jvm:3.0.0` | `034177291246286c73ae14cea7217417d2b20101d66059f05837cf6e6d106ed9` |

## Audit and limits

The unchanged configured `dependencyCheckAggregate --no-parallel --offline`
audit passed, exit **0**: **305 dependencies**, **0 active
findings**, **47 inherited suppressed
findings**. No suppression or audit-scope changes were made. The report hash and
actual NVD checked/modified timestamps are in `verification.json`; the NVD data
reports last modification `2026-10-16T18:00:09-04`.

The audit warned that its .NET assembly analyzer could not initialize without
dotnet 8. Existing Gradle deprecation, Android analytics and Dokka warnings also
remain. This result is the configured audit's outcome, not a claim that every
optional analyzer ran. No live upstream provider, Android device or hosted CI
verification is claimed. `ci-base.json` is historical hosted evidence and does
not validate this modified worktree. No unresolved task acceptance gaps remain.
