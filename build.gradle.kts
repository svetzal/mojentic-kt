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

// Pin the ktlint engine. The Gradle plugin otherwise falls back to its own,
// older default (1.5.0 for plugin 14.2.0).
subprojects {
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(libs.versions.ktlint.engine)
        }
    }
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

// Build-tool classpath security floors. None of these configurations reaches
// a published artifact: they resolve linters, Dokka's HTML generator and
// Kotlin's own build tooling. Each floor lifts a dependency past a known CVE
// until the owning tool ships the newer version itself. A floor matches the
// named group and its sub-groups (so `org.jetbrains.kotlin` does not touch
// `org.jetbrains.kotlinx`), and only on configurations whose name starts with
// one of the listed prefixes. See AGENTS.md, "Dependency audit".
class SecurityFloor(
    val configurationPrefixes: List<String>,
    val group: String,
    val version: String,
    val exceptModules: Set<String> = emptySet(),
) {
    fun appliesTo(configuration: String, requested: ModuleVersionSelector): Boolean =
        configurationPrefixes.any { configuration.startsWith(it) } &&
            (requested.group == group || requested.group.startsWith("$group.")) &&
            requested.name !in exceptModules
}

val kotlinVersion: String = libs.versions.kotlin.asProvider().get()

val buildToolSecurityFloors: List<SecurityFloor> = listOf(
    // CVE-2026-54512, CVE-2026-54513: fixed in 2.18.8 (Dokka 2.2.0 brings 2.15.3).
    SecurityFloor(listOf("dokka"), "com.fasterxml.jackson", "2.18.11"),
    // CVE-2026-84939: fixed in 2.3.35 (Dokka 2.2.0 brings 2.3.32).
    SecurityFloor(listOf("dokka"), "org.freemarker", "2.3.35"),
    // CVE-2026-39883 names OpenTelemetry-Go 1.15.0 to 1.42.0; the Java API
    // 1.41.0 matches the same CPE. Kotlin's Swift-export tooling brings 1.41.0.
    SecurityFloor(listOf("swiftExport"), "io.opentelemetry", "1.66.0"),
    // CVE-2026-53914 CPE match, fixed in Kotlin 2.4.20: Dokka's generator
    // runtime resolves kotlin-stdlib and kotlin-reflect 2.0.21 and the 1.8.20
    // stdlib-jdk7/jdk8 shims. The standard library is backward compatible.
    SecurityFloor(listOf("dokka"), "org.jetbrains.kotlin", kotlinVersion),
    // CVE-2026-53914, fixed in Kotlin 2.4.20: KGP's ABI-validation compat
    // classpath asks for the 2.4.0 build tools while every other Kotlin tool
    // classpath uses the project's Kotlin version. Lift it to match.
    // kotlin-reflect stays at the 1.6.10 that JetBrains pins for its compiler.
    SecurityFloor(
        listOf("kotlinAbiValidation"),
        "org.jetbrains.kotlin",
        kotlinVersion,
        exceptModules = setOf("kotlin-reflect"),
    ),
)

allprojects {
    configurations.configureEach {
        val configurationName = name
        val floors = buildToolSecurityFloors.filter { floor ->
            floor.configurationPrefixes.any { configurationName.startsWith(it) }
        }
        if (floors.isNotEmpty()) {
            resolutionStrategy.eachDependency {
                val floor = floors.firstOrNull { it.appliesTo(configurationName, requested) }?.version
                val version = requested.version
                if (floor != null && version != null && isOlder(version, floor)) {
                    useVersion(floor)
                    because("security floor for a build-tool classpath")
                }
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
