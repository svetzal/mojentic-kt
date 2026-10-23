# Dependency audit

The [audit policy and standard command](../AGENTS.md#dependency-audit) use
Dependency-Check 13.0.0 with its native public NVD feed updater and database
cache. There is no project downloader, feed mirror, custom cache or audit
harness. OSS Index and .NET analyzers are disabled; Java JAR/POM and archive
analysis remain enabled. NVD does not provide equivalent coverage to OSS Index.

## Correction and scope

Reviewed trunk `596af352031ccd670a952677e659fa56a0d89b51` and preserved work
`418c0b3be9a39409b8a13ee0d166e82e6db899e7`
(`wi_5357230d1037c6d271c7d9a5`). Local and remote main matched the trunk commit
before editing. Only bounded native configuration was recovered. The preserved
`tools/NvdFeedCache.java`, `tests/audit/NvdFeedCacheTest.java`, feed preparation,
marker handling, inventory task and gate wiring were not adopted.

Library behavior, dependencies and public APIs are unchanged. Aggregate scanning
retains the six JVM/Android library modules and root build-tool configurations,
with no configuration allowlist or new exclusions. Transitives remain included;
test groups are explicitly included. Existing demonstration-project exclusions
are unchanged. The CVSS failure threshold is still 7.0 and scanner errors fail
the task. Suppressions were not edited; their SHA-256 remains
`7cee0a8efe513e2b45d3d01e723c4c93355b7e6e9c020e0a8f9adfae59106db1`.

## Verification

On 2026-10-23, on the trunk commit above plus this uncommitted correction:

```text
./gradlew ktlintCheck detekt build allTests apiCheck dokkaGenerate
exit 0 — BUILD SUCCESSFUL in 29s
422 actionable tasks: 256 executed, 166 from cache
```

All six required native gates passed. Gradle reported deprecated features
that will be incompatible with Gradle 10; this correction does not resolve them.
No application or repository boundary changed, so no new executable contract set
or counterpart verification is required by this configuration correction.

The standard audit was run exactly once:

```text
./gradlew dependencyCheckAggregate --no-parallel
exit 1 — BUILD FAILED in 1m 30s
Execution failed for task ':dependencyCheckAggregate'.
Analysis failed.
```

Dependency-Check's download of
`https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-modified.json.gz`
failed with `Premature end of Content-Length delimited message body
(expected: 12137484; received: 424443)`. It then warned:

```text
Unable to update 1 or more Cached Web DataSource, using local data instead. Results may not include recent vulnerabilities.
```

The report directory contains no new HTML or JSON vulnerability report. A fresh
audit is **unavailable, not passed or clean**. This run does not establish that
dependencies are free of known vulnerabilities. No retry, alternate scanner,
custom feed recovery or stale-data success claim was used. The security workflow
uses the same native configuration with its ordinary database cache and report
upload; hosted CI was not run as part of this task.
