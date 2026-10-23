# Dependency audit verification

The owner policy and normal command are in [AGENTS.md](../AGENTS.md#dependency-audit).
The audit uses Dependency-Check 13.0.0, public NVD feeds and JVM tooling only.
OSS Index and .NET analyzers are deliberately excluded. Java and archive
analysis, the CVSS 7.0 failure threshold and failure on scanner errors remain
active. No suppression was changed.

Verification base: `596af352031ccd670a952677e659fa56a0d89b51`, with the current
uncommitted audit changes, on `mojility-ops-01`. Remote main matched that commit
before editing; no refs were changed.

Direct NVD transfer reproduced the earlier failure. With a 600-second read
timeout, Dependency-Check received 1,487,563 of the advertised 12,137,484 bytes
before premature EOF. A separate JDK 21 HTTP client received only 522,603 bytes.
The bounded JDK range/retry experiment recovered the entire feed and verified
133,161,185 decompressed bytes against NVD's metadata SHA-256:
`012f04c571da4988d3c2ba0eb28ad653cab50bce377ea1b4a1914af53d0929ae`.
This is the transport strategy used by `tools/NvdFeedCache.java`; it does not
perform vulnerability matching or replace Dependency-Check.

The inventory covers 395 resolvable configurations: 65 in each of the six
library modules and five root tooling configurations. It contains 252 distinct
external components and 258 distinct artifact hashes, including transitives.
Its SHA-256 is
`2ae15e7dbd58b674e1ee250c7f697dc6bc386645f7a597c08fb00c68cf0db639`.
A separate cached-data diagnostic scan represented all 258 hashes in its JSON
report (308 entries including nested dependencies), with none missing. That
scan's NVD data was last modified October 16; it is coverage evidence, not a
fresh vulnerability assessment.

The cold-database, account-free CI-equivalent audit is being verified. Its
HTML/JSON reports and inventory are produced under
`build/reports/dependency-check/` and uploaded by the security workflow.
Full execution logs, transport probes and inventory comparisons are retained
as execution evidence. Six required Gradle gates also run the in-process
transport checks through `build`; these checks use scripted HTTP responses and
fixed timestamps and never open sockets or wait for real time.

The existing ten suppressions retain their reasons and December 23 expiry.
They concern Java artifacts and shaded metadata, including cross-ecosystem
false matches; disabling .NET does not make those entries irrelevant. Their
unchanged file SHA-256 is
`7cee0a8efe513e2b45d3d01e723c4c93355b7e6e9c020e0a8f9adfae59106db1`.
NVD coverage is different from Sonatype coverage; a clean CVE report is not a
proof of complete supply-chain safety.
