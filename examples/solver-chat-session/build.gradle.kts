plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application { mainClass.set("com.mojentic.examples.SolverChatSessionExampleKt") }

dependencies {
    implementation(project(":mojentic-core"))
    implementation(project(":mojentic-ollama"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.simple)
}
