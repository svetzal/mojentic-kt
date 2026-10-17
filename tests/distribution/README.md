This is an independent JVM consumer build for the unreleased distribution
candidate. Run `tools/verify-distribution.sh` from the repository root with the
normal JDK 17/21 and Android SDK setup. Gradle options such as `--offline` may be
passed to the script when the complete dependency cache is available.

The script publishes all modules into a fresh local Maven directory, inspects
all 18 JVM/Android/root coordinates, then copies this consumer's build and test
files into a fresh directory. Its only Mojentic inputs are Maven coordinates;
there are no project dependencies, composite builds, substitutions or library
source directories. Maven Central supplies transitive dependencies, and an
exclusive repository rule prevents accidentally testing a previously released
Mojentic package. The package version comes from `gradle.properties`; it remains
2.1.0 until the separately authorized coordinated release.

The consumer reuses the versioned JSON contracts in `contracts/` with pinned
hashes, and a test-only loopback HTTP peer. Production adapters make real HTTP
requests. It checks native controls and completed tool retention, remote retry
payload identity and parameter omission, context discovery including errors and
missing fields, incomplete-stream termination without replay, and exact client
stream timing. `FirstDispatchTimingProofTest` and `RecoveryScriptedServer` were
seeded from the existing public-boundary test infrastructure; no library source
is compiled here. The full library gates retain the broader operation/provider,
privacy, cancellation and old/new serialization matrices.

Local signing is disabled only for this candidate verification. Release signing,
registry publication and post-release installation require the separate release.
`build/distribution-verification/candidate.*/artifacts.sha256` and the consumer's
test reports record the inspected package. They are disposable build outputs;
reviewed evidence for this assessment is in `docs/release-readiness/`.
