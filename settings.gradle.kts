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
include(":mojentic-openai")
include(":mojentic-websearch-serpapi")

// Phase 1 examples (JVM-only Gradle subprojects)
include(":examples:simple-llm")
include(":examples:list-models")
include(":examples:simple-structured")
include(":examples:simple-tool")
include(":examples:streaming")

// Phase 2 examples (JVM-only Gradle subprojects)
include(":examples:broker-examples")
include(":examples:chat-session")
include(":examples:chat-session-with-tool")
include(":examples:image-analysis")
include(":examples:embeddings")

// Phase 3 examples (JVM-only Gradle subprojects)
include(":examples:tracer-demo")
include(":examples:ask-user")
include(":examples:tell-user")
include(":examples:ephemeral-task-manager")
include(":examples:file-tool")
include(":examples:web-search")

// Phase 4 examples (JVM-only Gradle subprojects)
include(":examples:agent-dispatcher")
include(":examples:iterative-solver")
