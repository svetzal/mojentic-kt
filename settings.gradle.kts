pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mojentic-kt"

include(":mojentic-core")
include(":mojentic-ollama")

// Phase 1 examples (JVM-only Gradle subprojects)
include(":examples:simple-llm")
include(":examples:list-models")
include(":examples:simple-structured")
include(":examples:simple-tool")
include(":examples:streaming")
