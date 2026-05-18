# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Major and minor versions are synchronised with the other Mojentic ports
(`mojentic-py`, `mojentic-ts`, `mojentic-ex`, `mojentic-ru`, `mojentic-sw`);
patch versions move independently.

## [Unreleased]

## [0.0.1] - Phase 0

### Added

- Phase 0 skeleton: Kotlin Multiplatform project with JVM, Android, and iOS
  (x64, arm64, simulatorArm64) targets configured via the `kotlin-multiplatform`
  plugin in `mojentic-core`.
- Version catalog (`gradle/libs.versions.toml`) covering Kotlin 2.0+,
  kotlinx-coroutines / serialization / datetime, Ktor 3, Okio, kotlin-logging,
  and Detekt / ktlint / Kover / Dokka tooling.
- Quality-gate plugin wiring: ktlint + Detekt (with `detekt.yml`).
- Smoke-test target (`MojenticTest`) using `kotlin.test` to verify the
  Hello-level surface compiles and runs on all configured platforms.
- Project documentation files: `README.md`, `CHARTER.md`, `AGENTS.md`,
  `CLAUDE.md`, `CHANGELOG.md`, `LICENSE`, `.editorconfig`, `.gitignore`,
  `detekt.yml`.
- CI workflow (GitHub Actions) running ktlint, Detekt, build, and tests on
  Linux (JVM/Android targets) and macOS (iOS targets).
