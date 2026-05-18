plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
}

kotlin {
    jvmToolchain(17)

    jvm()

    android {
        namespace = "com.mojentic.anthropic"
        compileSdk = 36
        minSdk = 24

        withHostTest {}
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":mojentic-core"))
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.ktor.client.mock)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.slf4j.simple)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.slf4j.simple)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        iosMain {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

tasks.named("detekt").configure {
    dependsOn(
        tasks.matching { task ->
            task.name.startsWith("detekt") &&
                task.name != "detekt" &&
                !task.name.startsWith("detektBaseline") &&
                !task.name.startsWith("detektGenerateConfig")
        },
    )
}
