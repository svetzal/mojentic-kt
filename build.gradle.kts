plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compat.validator)
    alias(libs.plugins.dependency.check)
}

dependencyCheck {
    // Fail the build on findings with CVSS >= 7.0 (High / Critical). Mediums and
    // below are reported but not fatal — they tend to require triage, not action.
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    suppressionFile = rootProject.file("dependency-check-suppressions.xml").absolutePath
    // The CVE database download lives outside the project tree so it survives
    // `./gradlew clean`. Override via -PdependencyCheckDataDirectory=... in CI.
    data {
        directory = providers.gradleProperty("dependencyCheckDataDirectory")
            .orNull
            ?: "${System.getProperty("user.home")}/.gradle/dependency-check-data"
    }
    // NVD API key — without it, downloads are heavily rate-limited and the build
    // can stall for hours waiting on the public-tier feed. Set NVD_API_KEY in CI
    // secrets; request a key at https://nvd.nist.gov/developers/request-an-api-key.
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
    // Examples and samples are demonstration code — never published, not part of
    // the published surface — so we don't need to gate them.
    skipProjects = listOf(
        // Phase 1 examples
        ":examples:simple-llm", ":examples:list-models", ":examples:simple-structured",
        ":examples:simple-tool", ":examples:streaming",
        // Phase 2 examples
        ":examples:broker-examples", ":examples:chat-session",
        ":examples:chat-session-with-tool", ":examples:image-analysis", ":examples:embeddings",
        // Phase 3 examples
        ":examples:tracer-demo", ":examples:ask-user", ":examples:tell-user",
        ":examples:ephemeral-task-manager", ":examples:file-tool", ":examples:web-search",
        // Phase 4 examples
        ":examples:agent-dispatcher", ":examples:iterative-solver", ":examples:async-llm",
        ":examples:recursive-agent", ":examples:solver-chat-session", ":examples:react",
        ":examples:working-memory", ":examples:coding-file-tool", ":examples:broker-as-tool",
        // Phase 5 examples
        ":examples:realtime-text",
        // Phase 6 examples
        ":examples:anthropic-simple",
    )
}

apiValidation {
    // Library modules only — examples/samples are demonstration code, not part of the
    // published surface, and the application plugin's generated entry-point classes
    // would otherwise be tracked.
    ignoredProjects.addAll(
        listOf(
            // Phase 1 examples
            "simple-llm", "list-models", "simple-structured", "simple-tool", "streaming",
            // Phase 2 examples
            "broker-examples", "chat-session", "chat-session-with-tool",
            "image-analysis", "embeddings",
            // Phase 3 examples
            "tracer-demo", "ask-user", "tell-user", "ephemeral-task-manager",
            "file-tool", "web-search",
            // Phase 4 examples
            "agent-dispatcher", "iterative-solver", "async-llm", "recursive-agent",
            "solver-chat-session", "react", "working-memory", "coding-file-tool",
            "broker-as-tool",
            // Phase 5 examples
            "realtime-text",
            // Phase 6 examples
            "anthropic-simple",
        ),
    )
    // Internal-only markers; the validator excludes anything annotated this way from
    // the public-API baseline.
    nonPublicMarkers.add("com.mojentic.internal.InternalApi")
}

dokka {
    moduleName.set("Mojentic for Kotlin")
    dokkaPublications.html {
        outputDirectory.set(rootProject.layout.buildDirectory.dir("dokka"))
        includes.from(rootProject.file("docs/index.md"))
    }
}

dependencies {
    // Aggregate every library module into the multi-module Dokka site.
    dokka(project(":mojentic-core"))
    dokka(project(":mojentic-ollama"))
    dokka(project(":mojentic-openai"))
    dokka(project(":mojentic-anthropic"))
    dokka(project(":mojentic-realtime-openai"))
    dokka(project(":mojentic-websearch-serpapi"))
}
