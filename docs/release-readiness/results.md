# Kotlin distribution candidate verification

Assessed source: `f6913fc1c3f8cc35e110e4a47e7dfbe6ddc6db78` plus the worktree
identified by `source.sha256`. This is local prerelease validation, not a
published 3.0.0 package. The package version stays `2.1.0`; Maven coordinates
below refer exclusively to the fresh candidate repository, never Maven Central's
existing 2.1.0 release.

## Reproduction

Install the JDK 17 compiler toolchain, JDK 21 daemon and Android SDK 37 as
specified in AGENTS.md. This run used Linux `mojility-ops-01`, Gradle 9.8.0,
Kotlin 2.4.21, `ANDROID_HOME=/home/svetzal/Android/Sdk`, writable
`GRADLE_USER_HOME=/tmp/mojentic-kt-timing-gradle` and
`ANDROID_USER_HOME=/tmp/mojentic-kt-timing-android`. The Gradle home lists the
installed JDK paths and selects `kotlin.compiler.execution.strategy=in-process`.
No repository dependency or security-suppression version is changed.

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew build
./gradlew allTests
./gradlew apiCheck
./gradlew dokkaGenerate
tools/verify-distribution.sh --offline
./gradlew dependencyCheckAggregate --no-parallel \
  -PdependencyCheckDataDirectory=/tmp/mojentic-kt-timing-gradle/dependency-check-data --offline
```

Omit `--offline` on a host without the complete dependency cache. Initial
consumer plugin resolution downloaded one missing error-prone annotation jar;
subsequent candidate checks resolve from cached external dependencies and a
fresh Maven candidate directory. Every verbose command is captured by Foundry;
execution logs and behavioral proof are archived separately from source.

The script uses `publishToMavenLocal` with a fresh `maven.repo.local`, disabling
PGP Sign tasks in a local-only init script because no signatory is available in
the sandbox. Release signing stays enabled in the project. It copies only the
standalone consumer's own build/test inputs to a fresh build directory, then
runs consumer lint, public API tests and a dependency report. No library sources,
project dependencies, composite builds or dependency substitution are used.
An exclusive Maven repository rule for `com.vetzal.mojentic` prevents resolution
of historical registry packages. All six modules are resolved; core is also
resolved transitively. Native, context and OpenAI HTTP replies derive from the versioned JSON contracts;
Anthropic Messages replies are local extensions in kt-distribution-readiness-v1.
Both sets execute through production HTTP adapters. JSON contract identities are
recorded in CONFORMANCE-3.0.0.md and asserted before execution.

## Behavior and negative control

Before documentation expansion or full gates, an incomplete SSE response was
sent through a production oMLX adapter loaded from the freshly packaged Maven
candidate. The same consumer expected Completed and received Error (exit 1).
Restoring finish_reason=stop and [DONE] passed (exit 0), with exact 77 ms from
10 response + 7 admission + 40 delay + 20 response. Request bytes stayed equal,
delayed collection sent nothing and thinking won mixed-frame precedence.

A subsequent contract audit demonstrated an actual omission: the Anthropic
HTTP Retry-After test expected 3000 ms for obsolete HTTP dates but observed
5 ms jitter (exit 1). Shared normalization before Ktor parsing corrected it;
the identical HTTP test command passed across Anthropic/OpenAI/Ollama/oMLX
(exit 0). The final behavioral proof records this production correction.
Common tests additionally verify RFC 850 century handling, including a date
one day beyond the fifty-year boundary. Packaged tests assert exact 3000 ms
minimums and byte-identical retries through every adapter, preventing an
in-repository classpath from hiding a packaging omission.

## CI identity

GitHub was queried read-only for exact source identity. Hosted runs for the base
`f6913fc1c3f8cc35e110e4a47e7dfbe6ddc6db78` succeeded:

- [Validate, Test, and Publish — 39061490982](https://github.com/svetzal/mojentic-kt/actions/runs/39061490982)
- [Security — 39061491101](https://github.com/svetzal/mojentic-kt/actions/runs/39061491101)

Those runs do not test these uncommitted changes. The local six-gate and fresh
consumer results are CI-equivalent checks of this candidate. The updated build
workflow explicitly runs the six gates and fresh Maven consumer on future
hosted runs. No hosted run is claimed for the worktree, and none was triggered.
Foundry must review/finalize and obtain the new hosted results as part of landing.
Publication, signing credentials, coordinated approval and a fresh registry
installation are reserved for the separate release.

## Final candidate results

Source manifest: **94ecf5ed06fbc9a8b73a5115797c101d77614018f44a479ac21e0d2c45233f6b** (324 files). Validate from the repository root with `sha256sum -c docs/release-readiness/source.sha256`. The manifest covers code, tests, APIs, contracts, build/security configuration, verification tools and workflows; this narrative and other documentation are excluded to avoid circular evidence hashes.

| Required gate | Exit | Captured stdout SHA-256 |
| --- | --- | --- |
| `./gradlew ktlintCheck` | 0 | `5dee9732e170850721322c10d84b8ed33fe6a2c1f57282c1eb83119fed0d26b4` |
| `./gradlew detekt` | 0 | `abc42852f078db8ba54b05bb5334aaf02132e12dc74b54a440b0ab78d3087008` |
| `./gradlew build` | 0 | `917e82e594c011122021b3868723bb9ae381ab5749ef883268148c77b84c9338` |
| `./gradlew allTests` | 0 | `d84c2c28d9bfbec1a8cefab196d046c838337f2ae202836cadcf59d0be686dcf` |
| `./gradlew apiCheck` | 0 | `6187d341d39b522b9ba659f122af30751d2ede358346e92a3e365f1e2a5bb2c7` |
| `./gradlew dokkaGenerate` | 0 | `95f94951a9490013b76ca78a9c292f3ea2c254e4aa8c6f3515d77efb3c61a7e1` |

Final reports contain **540 JVM tests in 100 suites and 382 Android host tests in 67 suites**, with zero failures, errors or skips. The final gate repeat reused successful same-source outputs where Gradle marked tasks up-to-date. Coverage is not wired; no percentage is claimed. Existing Gradle deprecation, Android analytics/.NET analyzer availability and Dokka link notices remain visible; no warning suppression was added.

The fresh independent JVM consumer passed lint and **7 tests**, including all contract context rows with exact method/path/body, optional authentication/modelNumCtx, malformed/absent fields, all four adapters’ obsolete-date minima, native tool retention, request fidelity and stream terminal/timing behavior. Android AARs are built and inspected and Android host tests pass; no emulator/device or separate Android consumer installation is claimed.

### Artifact coordinates and inspection

All coordinates use `com.vetzal.mojentic` and unchanged candidate version `2.1.0`. Each module below has root, `-jvm` and `-android` coordinates: **18 total**, with no iOS coordinate or metadata variant.

| Root artifact | Fresh JVM jar SHA-256 |
| --- | --- |
| `mojentic-core` | `4160a883dd964a049accf6fb8de4002f519f12b9086e45bbd5ae9db613dddea0` |
| `mojentic-openai` | `3522bec91a2e1834b74731c702e0a700f24b971a0efb2131699414d3c5833695` |
| `mojentic-ollama` | `d3ae5b82a33fd3e9eb4bab46d7b3f420ce3da95152e6ee5a58462f29ddc662ec` |
| `mojentic-anthropic` | `19bfd0778777b70f54fdca33ec92b2baa7d22a3b6df82ba1d82aca756faf011a` |
| `mojentic-realtime-openai` | `d7066e9af730db930c2def891bb7a1ec6acb48d2ab4b9b60172cbab36414f3ca` |
| `mojentic-websearch-serpapi` | `034177291246286c73ae14cea7217417d2b20101d66059f05837cf6e6d106ed9` |

[artifacts.sha256](artifacts.sha256) identifies all **90** POM, module, jar and AAR files; manifest SHA-256: `7561426afcf7a20442fdffd730b4bb74da9f3b72ab485735103de100bc3e9abb`. POM/module inspection confirms exact group/version, versioned dependencies and no removed iOS variants. Root Gradle metadata selects the published JVM variants. Runtime dependencies include Kotlin 2.4.21, Ktor 3.6.0, coroutines/serialization 1.11.0, datetime 0.8.0, okio 3.18.2, kotlin-logging 8.0.4 and jtokkit 1.1.0; no build-tool dependencies are added to publication metadata. The separately resolved [consumer-runtime.txt](consumer-runtime.txt) records transitive selection with no project components or unresolved runtime dependency.

Fresh consumer command: `tools/verify-distribution.sh --offline`, exit **0**, stdout SHA-256 `a1d988b70a517fb87484b41e105fec2897740f65d7f2d9608c194b6d01b4df7a`. Consumer build/test inputs were checked byte-for-byte against the retained source. Consumer source is the same base-plus-manifest candidate; no external repository commit or execution is required by the library self-validation decision.

### Security outcome and limits

The configured full-scope `dependencyCheckAggregate --no-parallel` audit exited **0**: **305 dependencies, zero unsuppressed findings, 47 inherited suppressed findings**. Build-tool scope and CVSS 7.0 threshold are unchanged. POM/module inspection and the independent dependency graph verify what the consumer resolves, rather than relying only on in-project configuration. Existing suppression bytes are unchanged (`7cee0a8efe513e2b45d3d01e723c4c93355b7e6e9c020e0a8f9adfae59106db1`); this assessment adds no advisory exception and does not newly revalidate inherited suppressions.

Before the audit, the public NVD modified-feed metadata reported `lastModifiedDate:2026-10-16T18:00:09-04:00`. The audit reports its NVD check at `2026-10-17T23:52:06Z`, with last modified `2026-10-16T18:00:09-04`. This records the actual data freshness, not a claim that the feed is newer than the API. JSON report SHA-256: `40933b316da3b8bace596d691ef8e194adab185db77e2baca4deace0410fb43e`; audit stdout SHA-256: `3200ef17609350351d5c34f72ae6ede812815d30a72c40942a5fdc14ec5560a6`.

Machine-readable outcomes and hashes are in [verification.json](verification.json); base hosted evidence is in [ci-base.json](ci-base.json). Local readiness for Foundry review and coordinated release approval is established for these declared cases. Foundry still owns finalization and hosted validation of the finalized source. No release bump, signing, remote publication, dependent migration or post-release registry installation is performed here.
