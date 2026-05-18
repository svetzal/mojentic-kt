plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":mojentic-core"))
    implementation(project(":mojentic-ollama"))
    implementation(libs.slf4j.simple)
}

application {
    mainClass.set("com.mojentic.examples.StreamingKt")
}
