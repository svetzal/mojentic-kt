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
        name.set("Mojentic Core")
        description.set("Vendor-neutral types and core abstractions for the Mojentic Kotlin Multiplatform agentic framework.")
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
        namespace = "com.mojentic.core"
        compileSdk = 36
        minSdk = 24

        withHostTest {}
    }

    val xcf = XCFramework("MojenticCore")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "MojenticCore"
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
                api(libs.kotlin.logging)
                implementation(libs.okio)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.okio.fakefilesystem)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.slf4j.simple)
            }
        }
        // Android host-test set (created by `withHostTest {}`) runs commonTest on the
        // host JVM and needs an SLF4J binding for kotlin-logging.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.slf4j.simple)
            }
        }
    }
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

// The KMP detekt plugin (1.23.x) creates per-target detekt tasks (detektJvmMain,
// detektIosArm64Main, …) but the umbrella `detekt` task doesn't depend on them
// out of the box — it reports NO-SOURCE. Wire them up so `./gradlew detekt`
// actually scans every source set.
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
