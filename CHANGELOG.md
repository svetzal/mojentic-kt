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
  plugin in `mojentic-core`. Android target uses AGP 9.x's KMP-native
  `com.android.kotlin.multiplatform.library` plugin (the legacy
  `com.android.library` plugin is incompatible with KMP from AGP 9.0).
- Version catalog (`gradle/libs.versions.toml`) pinning the May-2026 stable
  toolchain: Kotlin 2.3.21, Gradle 9.5.1, AGP 9.2.0, Ktor 3.4.2,
  kotlinx-coroutines 1.10.2, kotlinx-serialization 1.8.1, kotlinx-datetime
  0.7.1, okio 3.11.0, kotlin-logging 7.0.7, Dokka 2.0.0,
  binary-compatibility-validator 0.18.1, Kover 0.9.1, ktlint plugin 13.0.0,
  Detekt 1.23.8.
- Quality-gate plugin wiring: ktlint + Detekt (with `detekt.yml`).
- Smoke-test target (`MojenticTest`) using `kotlin.test` to verify the
  Hello-level surface compiles and runs on all configured platforms.
- Project documentation files: `README.md`, `CHARTER.md`, `AGENTS.md`,
  `CLAUDE.md`, `CHANGELOG.md`, `LICENSE`, `.editorconfig`, `.gitignore`,
  `detekt.yml`.
- Committed Gradle wrapper (`gradlew`, `gradlew.bat`,
  `gradle/wrapper/gradle-wrapper.jar` + `.properties` at 9.5.1) so fresh
  clones build immediately without a system Gradle install.
- CI workflow (GitHub Actions) running ktlint, Detekt, build, and tests on
  Linux (JVM/Android targets) and macOS (iOS targets).
