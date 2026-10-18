# Caller-selected retry eligibility verification

Tested source: `fa9694903ab083f7d97e331b7f723f65dc2976a9` plus the uncommitted worktree identified by
[source.sha256](source.sha256), SHA-256 `d782d3dfb7693b60291671dd6d4f0f7a5ea8012ac907b405f8090f328c1a575a`
(325 files). Verified at `2026-10-18T00:41:27.784910+00:00`.
This correction closes the remaining eligibility proof gap; it does not change
production behavior, dependencies, versions or the coordinated release scope.

## Executable contract and behavioral proof

**kt-retry-eligibility-v1**, SHA-256 `fcd4793948bfbe010dbd26e3af396db83f38c0cf6c3f171254053d22f5b10ef7`, is
`mojentic-openai/src/jvmTest/kotlin/com/mojentic/omlx/RetryEligibilityHttpTest.kt`.
The manifest also identifies its existing production HTTP server and provider
fixtures. [CONFORMANCE-3.0.0.md](../../CONFORMANCE-3.0.0.md) maps the named tests
to the binding recovery decisions and records the authority hashes.

The two differential tests execute **48 scenarios**: four production adapters
(OpenAI, Anthropic, Ollama, oMLX), three public entrypoints (ordinary, structured,
streaming), two eligibility controls and their enabled/disabled values. Each
pair holds failure, payload and admission policy constant. Transport controls
close the accepted connection before response headers; status controls include
or exclude the same normally retryable HTTP 503.

Enabled cases recover after admission and deterministic 100 ms backoff. Disabled
cases assert typed HTTP_PERMANENT terminal classification, an ineligible retained
failure (TRANSPORT or HTTP category), exact STARTED/FAILED/EXHAUSTED lifecycle,
no admission/backoff, and no second request. Request bytes are compared across
controls and retries, with captured identities, method/path and retry headers.
A success-response callback proves the queued success was used only in enabled
cases. The focused oMLX stream test adds one disabled scenario.

Before expanding the matrix or running the full gates, the focused production
HTTP probe enabled transport retry against disabled-case assertions. It consumed
the queued success and reached the delivery guard, producing PARTIAL_SUCCESS
instead of the expected HTTP_PERMANENT (exit **1**). Changing only
`retryTransport` to false passed the same command (exit **0**):

```bash
./gradlew :mojentic-openai:jvmTest --tests com.mojentic.omlx.RetryEligibilityHttpTest --offline
```

Foundry retains the actual rejecting/corrected logs and validated behavioral
proof JSON separately from repository evidence. The final matrix passes with
production behavior unchanged.

## Required gates

All six `.hone-gates.json` commands pass on the final test source:

| Command | Exit | Captured stdout SHA-256 |
| --- | --- | --- |
| `./gradlew ktlintCheck` | 0 | `7e3d020b637cfc2b535d5b2e221dd7150018b434e7d52be912a8e10f39b77c8b` |
| `./gradlew detekt` | 0 | `46742e4a9fe0c543dc36ab1af6a8cf8448688fa7d9a1ad6cc71c8c17aba804a2` |
| `./gradlew build` | 0 | `063c7cf8070281c2ec18051b67ac2875314a68b4d10a4f5d063f0991f6cc03b4` |
| `./gradlew allTests` | 0 | `990990b9035aa3312dc544d4849cefb306ba8c5acdd7bcd5adf79677aa6096ce` |
| `./gradlew apiCheck` | 0 | `b882835431c709e4ed3d744e6bc77e0ff0a016b2593174b758976c620ff16101` |
| `./gradlew dokkaGenerate` | 0 | `c595b7df32edaeb0028b8bc627acc710f5b3b7cb261097c403b718ec7cf2edd6` |

Reports contain **543 JVM tests in 101 suites** and
**382 Android host tests in 67 suites**, with zero
failures, errors or skips. API and Dokka checks pass. Coverage reporting remains
unwired; no coverage percentage is claimed. Existing tooling warnings remain
visible, with no new suppression or baseline.

Reproduction uses JDK 17 for compilation, JDK 21 for Gradle, Android SDK 37,
`GRADLE_USER_HOME=/tmp/mojentic-kt-timing-gradle` and
`ANDROID_USER_HOME=/tmp/mojentic-kt-timing-android`. The writable Gradle home lists
both toolchains and selects in-process compilation. Verbose commands are run
through Foundry capture. Omit `--offline` on a host without cached dependencies.

## Fresh candidate and separate consumer

`tools/verify-distribution.sh --offline` exits **0**. It freshly packages the
candidate into an isolated Maven repository and copies the standalone consumer
build inputs into a fresh directory. Consumer lint, **7 public API
tests** and dependency resolution pass against those Maven artifacts; there are
no project dependencies, source inclusion or composite substitutions. The
existing executable set **kt-distribution-readiness-v1** is identified by the
same source manifest's `tests/distribution/` and `tools/` entries. It consumes the
versioned JSON contract fixtures and covers public recovery, reasoning, request
fidelity, metadata and first-dispatch timing. The new eligibility matrix runs in
the library's JVM gate; the separate consumer verifies the packaged public API.
Both use this base plus manifest, without counterpart repository verification.

The candidate retains version **2.1.0** for this prerelease check. It contains
**18 root/JVM/Android coordinates** and no iOS variant;
publication metadata inspection passes. Local PGP sign tasks alone are disabled
by the script; release signing configuration is unchanged. No registry upload or
remote publication occurs.

[artifacts.sha256](artifacts.sha256) records every inspected candidate artifact,
manifest SHA-256 `7561426afcf7a20442fdffd730b4bb74da9f3b72ab485735103de100bc3e9abb`.
[consumer-runtime.txt](consumer-runtime.txt) records the separately resolved
runtime graph, SHA-256 `1d4cad3c3846ff8d4c9331d91e15e9cf1abd02f87eba0fdbcc2387035811fbe6`.
Consumer stdout SHA-256: `2ff43176a0c21fab9b7f71c73bcf9ca1e38d3e18c51b48ac94999e7b12301006`.

## Security and CI scope

The full configured audit exits **0**:

```bash
./gradlew dependencyCheckAggregate --no-parallel \
  -PdependencyCheckDataDirectory=/tmp/mojentic-kt-timing-gradle/dependency-check-data --offline
```

It scans **305 dependencies**, with **0
unsuppressed findings** and **47 inherited
suppressed findings**. No dependency or suppression changes were made; inherited
suppressions were not newly revalidated. Suppression SHA-256 remains
`7cee0a8efe513e2b45d3d01e723c4c93355b7e6e9c020e0a8f9adfae59106db1`. The offline audit uses cached advisory
data; its actual source timestamps are retained in [verification.json](verification.json).
Report SHA-256: `45cd4de5189f73e4dcf425a2f3cb929ebf80dbae6e50f0c79210d0ce83ccaf7f`.

[verification.json](verification.json) records all actual commands, exits,
contract/source/artifact identities and report hashes. [ci-base.json](ci-base.json)
is historical hosted evidence for f6913fc, not hosted verification of this
worktree. This run establishes local gate and package verification for the
focused correction; Foundry owns Git finalization and hosted validation of the
finalized source. Release approval, version bump, signing, publication and fresh
registry installation remain separate coordinated work.
