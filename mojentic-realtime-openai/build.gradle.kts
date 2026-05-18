import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()
    coordinates(project.group.toString(), project.name, project.version.toString())
    pom {
        name.set("Mojentic Realtime OpenAI Gateway")
        description.set("OpenAI Realtime (WebSocket) gateway for the Mojentic Kotlin Multiplatform agentic framework.")
        url.set("https://github.com/svetzal/mojentic-kt")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("svetzal")
                name.set("Stacey Vetzal")
                email.set("stacey@vetzal.com")
                url.set("https://github.com/svetzal")
            }
        }
        scm {
            url.set("https://github.com/svetzal/mojentic-kt")
            connection.set("scm:git:git://github.com/svetzal/mojentic-kt.git")
            developerConnection.set("scm:git:ssh://git@github.com/svetzal/mojentic-kt.git")
        }
        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/svetzal/mojentic-kt/issues")
        }
    }
}

kotlin {
    jvmToolchain(17)

    jvm()

    android {
        namespace = "com.mojentic.realtime.openai"
        compileSdk = 36
        minSdk = 24

        withHostTest {}
    }

    val xcf = XCFramework("MojenticRealtimeOpenAi")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "MojenticRealtimeOpenAi"
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":mojentic-core"))
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
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
