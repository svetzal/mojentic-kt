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

val auditDataDirectory = providers.gradleProperty("dependencyCheckDataDirectory")
    .getOrElse("${System.getProperty("user.home")}/.gradle/dependency-check-data")
val auditFeedDirectory = file("$auditDataDirectory/nvd-feed")
// The legacy host setting for this same public feed must also use range recovery.
val auditFeedOverride = providers.gradleProperty("dependencyCheckNvdDatafeedUrl").filter {
    it.isNotBlank() && it != "https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz"
}
val auditJava = "${System.getProperty("java.home")}/bin/java"
val auditJavac = "${System.getProperty("java.home")}/bin/javac"

dependencyCheck {
    // Fail the build on findings with CVSS >= 7.0 (High / Critical). Mediums and
    // below are reported but not fatal — they tend to require triage, not action.
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    failOnError = true
    scanDependencies = true
    // Include test and build-tool graphs as well as all published JVM/Android graphs.
    skipTestGroups = false
    suppressionFile = rootProject.file("dependency-check-suppressions.xml").absolutePath
    // The CVE database download lives outside the project tree so it survives
    // `./gradlew clean`. Override via -PdependencyCheckDataDirectory=... in CI.
    data {
        directory = auditDataDirectory
    }
    // Account-free public NVD bulk feeds; no NVD or Sonatype credentials required.
    nvd {
        // Always import the freshly verified local feed before advancing its marker.
        validForHours = 0
        datafeedUrl = auditFeedOverride.getOrElse(
            auditFeedDirectory.toURI().toString().trimEnd('/') + "/nvdcve-2.0-{0}.json.gz",
        )
    }
    analyzers {
        // Owner policy: JVM tooling only. These are deliberate scope exclusions,
        // not prerequisites to provision on CI or an audit host.
        ossIndex { enabled = false }
        assemblyEnabled = false
        nuspecEnabled = false
        nugetconfEnabled = false
        msbuildEnabled = false
        // Keep Java bytecode/POM and nested archive analysis explicit.
        jarEnabled = true
        archiveEnabled = true
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
        // oMLX examples
        ":examples:omlx-simple",
    )
}

val compileAuditTransportTest = tasks.register<Exec>("compileAuditTransportTest") {
    val classes = layout.buildDirectory.dir("audit-transport-test")
    inputs.files("tools/NvdFeedCache.java", "tests/audit/NvdFeedCacheTest.java")
    outputs.dir(classes)
    doFirst { classes.get().asFile.mkdirs() }
    commandLine(auditJavac, "-d", classes.get().asFile, "tools/NvdFeedCache.java", "tests/audit/NvdFeedCacheTest.java")
}
val testAuditTransport = tasks.register<Exec>("testAuditTransport") {
    dependsOn(compileAuditTransportTest)
    commandLine(auditJava, "-cp", layout.buildDirectory.dir("audit-transport-test").get().asFile, "NvdFeedCacheTest")
}
val prepareNvdFeed = tasks.register<Exec>("prepareNvdFeed") {
    dependsOn(testAuditTransport)
    onlyIf { !auditFeedOverride.isPresent }
    commandLine(auditJava, "tools/NvdFeedCache.java", "prepare", auditFeedDirectory, libs.versions.dependency.check.get())
}

// Audit feeds and suppression rules change independently of resolved dependencies.
// A release audit must not reuse a report produced before those changes.
val auditDependencyInventory = tasks.register("auditDependencyInventory") {
    description = "Record every resolved audit configuration and its transitive artifacts."
    group = "verification"
    val inventory = layout.buildDirectory.file("reports/dependency-check/dependency-inventory.tsv")
    outputs.file(inventory)
    outputs.upToDateWhen { false }
    doLast {
        val hashes = mutableMapOf<java.io.File, String>()
        val lines = mutableListOf("project\tconfiguration\tcomponent\tsha256\tfile")
        allprojects.sortedBy { it.path }
            .filterNot { it.path in dependencyCheck.skipProjects.get() }
            .forEach { project ->
                project.configurations.filter { it.isCanBeResolved }.sortedBy { it.name }.forEach { configuration ->
                    // Keep empty configurations identifiable too; resolution failures fail the task.
                    lines += "${project.path}\t${configuration.name}\t\t\t"
                    // Each library project is inventoried separately. Excluding project
                    // outputs avoids ambiguous AGP artifact variants, not their transitives.
                    val artifacts = configuration.incoming.artifactView {
                        componentFilter { it is org.gradle.api.artifacts.component.ModuleComponentIdentifier }
                    }.artifacts.artifacts
                    artifacts.sortedBy { it.id.displayName }.forEach { artifact ->
                        val hash = if (artifact.file.isDirectory) "directory" else hashes.getOrPut(artifact.file) {
                            val digest = java.security.MessageDigest.getInstance("SHA-256")
                            artifact.file.inputStream().use { input ->
                                val buffer = ByteArray(8192)
                                var count = input.read(buffer)
                                while (count != -1) {
                                    digest.update(buffer, 0, count)
                                    count = input.read(buffer)
                                }
                            }
                            digest.digest().joinToString("") { "%02x".format(it) }
                        }
                        lines += listOf(
                            project.path, configuration.name, artifact.id.componentIdentifier.displayName,
                            hash, artifact.file.name,
                        ).joinToString("\t")
                    }
                }
            }
        inventory.get().asFile.apply {
            parentFile.mkdirs()
            writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }
}

tasks.named("dependencyCheckAggregate") {
    dependsOn(auditDependencyInventory, prepareNvdFeed)
    inputs.file("dependency-check-suppressions.xml")
    outputs.upToDateWhen { false }
    doLast {
        if (!auditFeedOverride.isPresent) {
            providers.exec {
                commandLine(auditJava, "tools/NvdFeedCache.java", "mark", auditFeedDirectory, libs.versions.dependency.check.get())
            }.result.get().assertNormalExitValue()
        }
    }
}

// Pin the ktlint engine. The Gradle plugin otherwise falls back to its own,
// older default (1.5.0 for plugin 14.2.0).
subprojects {
    tasks.matching { it.name == "check" }.configureEach { dependsOn(testAuditTransport) }
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
            // oMLX examples
            "omlx-simple",
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
    val onlyModules: Set<String> = emptySet(),
) {
    fun appliesTo(configuration: String, requested: ModuleVersionSelector): Boolean =
        configurationPrefixes.any { configuration.startsWith(it) } &&
            (requested.group == group || requested.group.startsWith("$group.")) &&
            requested.name !in exceptModules &&
            (onlyModules.isEmpty() || requested.name in onlyModules)
}

val kotlinVersion: String = libs.versions.kotlin.asProvider().get()

val buildToolSecurityFloors: List<SecurityFloor> = listOf(
    // CVE-2026-53914 audit: align lint-tool runtime artifacts with patched KGP.
    // Compiler replacement is blocked by ktlint's PSI initialization and
    // detekt's compiler-version requirement; see RECOVERY-CONFORMANCE.md.
    SecurityFloor(
        listOf("ktlint", "detekt", "androidLint"),
        "org.jetbrains.kotlin",
        kotlinVersion,
        onlyModules = setOf("kotlin-stdlib", "kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8", "kotlin-reflect"),
    ),
    // CVE-2026-54512, CVE-2026-54513: fixed in 2.18.8 (Dokka 2.2.0 brings 2.15.3).
    SecurityFloor(listOf("dokka"), "com.fasterxml.jackson", "2.18.11"),
    // CVE-2026-84939: fixed in 2.3.35 (Dokka 2.2.0 brings 2.3.32).
    SecurityFloor(listOf("dokka"), "org.freemarker", "2.3.35"),
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
    // Android lint's tool classpath (AGP 9.4.1). Medium findings, but cheap to
    // clear: CVE-2020-13956 is fixed in httpclient 4.5.13 (lint brings 4.5.6),
    // CVE-2025-48924 in commons-lang3 3.18.0 (lint brings 3.16.0).
    SecurityFloor(
        listOf("androidLint"),
        "org.apache.httpcomponents",
        "4.5.14",
        onlyModules = setOf("httpclient"),
    ),
    SecurityFloor(listOf("androidLint"), "org.apache.commons", "3.20.0", onlyModules = setOf("commons-lang3")),
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
