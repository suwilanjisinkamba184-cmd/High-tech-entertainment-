pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiveKit's AudioSwitch transitive dependency is hosted on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "HighTechEntertainment"
include(":app")
