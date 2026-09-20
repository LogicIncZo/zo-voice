pluginManagement {
    repositories {
        google()
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
rootProject.name = "zo-voice"

// Consume the zo-kotlin SDK from source when checked out next to this repo
// (../zo-kotlin); otherwise fall back to the published Maven artifact.
try {
    includeBuild("../zo-kotlin")
} catch (e: Exception) {
    logger.lifecycle("zo-kotlin source build not found; using Maven artifact dev.zocomputer:ask")
}
include(":app")
