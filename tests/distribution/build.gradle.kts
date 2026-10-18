import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

repositories {
    exclusiveContent {
        forRepository { maven { url = uri(providers.gradleProperty("candidateRepository").get()) } }
        filter { includeGroup("com.vetzal.mojentic") }
    }
    mavenCentral()
}

kotlin { jvmToolchain(17) }

ktlint { version.set(libs.versions.ktlint.engine) }

val candidateVersion = providers.gradleProperty("candidateVersion").get()

dependencies {
    testImplementation("com.vetzal.mojentic:mojentic-openai:$candidateVersion")
    testImplementation("com.vetzal.mojentic:mojentic-ollama:$candidateVersion")
    testImplementation("com.vetzal.mojentic:mojentic-anthropic:$candidateVersion")
    testImplementation("com.vetzal.mojentic:mojentic-realtime-openai:$candidateVersion")
    testImplementation("com.vetzal.mojentic:mojentic-websearch-serpapi:$candidateVersion")
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    systemProperty("candidateVersion", candidateVersion)
    systemProperty("contractDirectory", providers.gradleProperty("contractDirectory").get())
}

// Verify the actual resolved files, in addition to the exclusive repository declaration.
val verifyCandidateResolution by tasks.registering {
    doLast {
        val repository = file(providers.gradleProperty("candidateRepository").get()).canonicalFile
        val artifacts = configurations.testRuntimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        val candidateArtifacts = artifacts.filter { it.moduleVersion.id.group == "com.vetzal.mojentic" }
        check(candidateArtifacts.size == 6) { "Expected all six JVM candidate modules" }
        val report = layout.buildDirectory.file("reports/candidate-resolution.txt").get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            candidateArtifacts.sortedBy { it.moduleVersion.id.toString() }.joinToString("\n", postfix = "\n") {
                check(it.moduleVersion.id.version == candidateVersion) { "Wrong candidate version: ${it.moduleVersion.id}" }
                check(it.file.canonicalFile.toPath().startsWith(repository.toPath())) {
                    "Candidate resolved outside local repository: ${it.file}"
                }
                val hash = MessageDigest.getInstance("SHA-256").digest(it.file.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
                "$hash  ${it.moduleVersion.id}  ${it.file.canonicalPath}"
            },
        )
    }
}

tasks.test { dependsOn(verifyCandidateResolution) }
