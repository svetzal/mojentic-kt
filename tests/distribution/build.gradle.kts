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
}

tasks.test {
    systemProperty("candidateVersion", candidateVersion)
    systemProperty("contractDirectory", providers.gradleProperty("contractDirectory").get())
}
