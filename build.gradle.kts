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
        providers.gradleProperty("dependencyCheckNvdDatafeedUrl").orNull?.let { datafeedUrl = it }
    }
    // Examples and samples are demonstration code — never published, not part of
    // the published surface — so we don't need to gate them.
    skipProjects = listOf(
        ":examples", // Container project also receives build-tool configurations.
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

// Build-tool classpath security floors. These configurations never reach a
// published artifact: they resolve Dokka's HTML generator and Kotlin's
// Swift-export tooling. Each floor lifts a transitive dependency past a
// known CVE until the owning plugin ships the newer version itself.
val buildToolSecurityFloors: Map<String, String> = mapOf(
    // CVE-2026-54512, CVE-2026-54513: fixed in 2.18.8 (Dokka 2.2.0 brings 2.15.3).
    "com.fasterxml.jackson" to "2.18.11",
    // CVE-2026-84939: fixed in 2.3.35 (Dokka 2.2.0 brings 2.3.32).
    "org.freemarker" to "2.3.35",
    // CVE-2026-39883 names OpenTelemetry-Go 1.15.0 to 1.42.0; the Java API
    // 1.41.0 matches the same CPE. Kotlin's Swift-export tooling brings 1.41.0.
    "io.opentelemetry" to "1.66.0",
)

allprojects {
    configurations
        .matching { it.name.startsWith("dokka") || it.name.startsWith("swiftExport") }
        .configureEach {
            resolutionStrategy.eachDependency {
                val floor = buildToolSecurityFloors.entries
                    .firstOrNull { (group, _) -> requested.group.startsWith(group) }
                    ?.value
                val version = requested.version
                if (floor != null && version != null && isOlder(version, floor)) {
                    useVersion(floor)
                    because("security floor for a build-tool classpath")
                }
            }
        }
}

fun isOlder(version: String, floor: String): Boolean {
    val parts = { v: String -> v.split('.', '-').map { it.toIntOrNull() ?: 0 } }
    val a = parts(version)
    val b = parts(floor)
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff < 0
    }
    return false
}
