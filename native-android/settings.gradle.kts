pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    // Kotlin Gradle Plugin 2.4.21 is the current stable release and is compatible with AGP 8.11.1.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}

// AGP 8.11.1 + KGP 2.4.21 are within Kotlin's published compatibility range.
// The generated Flutter module is included below and reads this root plugin version.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    val storageUrl = System.getenv("FLUTTER_STORAGE_BASE_URL") ?: "https://storage.googleapis.com"
    repositories {
        google()
        mavenCentral()
        maven("$storageUrl/download.flutter.io")
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "HighTechEntertainment"
include(":app")

val flutterInclude = settingsDir.parentFile.resolve("flutter-ui/.android/include_flutter.groovy")
require(flutterInclude.isFile) {
    "Flutter module bootstrap is missing. Run flutter pub get in ../flutter-ui first."
}
apply(from = flutterInclude)
