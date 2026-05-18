plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application { mainClass.set("com.mojentic.examples.WebSearchExampleKt") }

dependencies {
    implementation(project(":mojentic-core"))
    implementation(project(":mojentic-ollama"))
    implementation(project(":mojentic-websearch-serpapi"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.simple)
}
