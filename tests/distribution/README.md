This independent JVM consumer verifies freshly built local Maven artifacts. Run
`tools/verify-distribution.sh` from the repository root with JDK 17/21 and the
Android SDK. Both Gradle invocations run offline; tool, plugin and transitive
caches must already be populated. Verification makes no registry calls.

The script publishes into a fresh local file repository, inspects all 18
JVM/Android/root coordinates, then copies only this consumer build and its test
sources into a separate directory. Mojentic inputs are Maven coordinates, with
no project/composite dependencies, source directories or substitutions. The
exclusive file repository and `verifyCandidateResolution` check enforce that all
six resolved JVM module files come from that fresh repository and match the
candidate version from `gradle.properties`.

The consumer derives in-process transport replies from the same versioned JSON
contracts as library/provider verification, checking pinned SHA-256 identities
before executing behavior. It uses public gateways/broker/session APIs, Ktor
MockEngine and controlled clocks. It retains native reasoning/tool association,
remote retry payload/omission, context discovery/error/optional-field,
incomplete-stream/no-replay and exact first-dispatch timing assertions.
`FirstDispatchTimingProofTest` includes delayed collection, admitted retry and
mixed-frame precedence without sockets or real sleeps. Broader privacy,
cancellation and compatibility matrices remain in the library gates.

Local sign tasks are disabled only for this verification. No registry upload,
release change or ref mutation occurs. Each candidate directory contains
`artifacts.sha256`, independent test reports, and the consumer's
`build/reports/candidate-resolution.txt` with actual resolved file paths/hashes.
Reviewed current evidence is maintained in `docs/release-readiness/`; see
`docs/testing.md` for the migration inventory and `CONFORMANCE-3.0.0.md` for
binding decision coverage.
