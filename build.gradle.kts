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
