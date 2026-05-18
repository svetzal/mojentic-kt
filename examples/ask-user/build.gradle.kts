plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application { mainClass.set("com.mojentic.examples.AskUserExampleKt") }

dependencies {
    implementation(project(":mojentic-core"))
    implementation(project(":mojentic-ollama"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.simple)
}
