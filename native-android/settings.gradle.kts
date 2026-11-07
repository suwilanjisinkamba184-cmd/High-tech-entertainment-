pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

// Keep AGP and KGP on a mutually supported toolchain. KGP 2.3.0 supports
// Gradle 8.14.3 and AGP 8.13.0, avoiding the legacy BaseVariant linkage
// failure observed with the previous 2.2.20 / 8.11.1 pairing.
plugins {
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    val storageUrl = System.getenv("FLUTTER_STORAGE_BASE_URL") ?: "https://storage.googleapis.com"
    repositories {
        google()
        mavenCentral()
        maven("$storageUrl/download.flutter.io")
        // LiveKit's AudioSwitch transitive dependency is hosted on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "HighTechEntertainment"
include(":app")

// Flutter's generated integration script registers the :flutter module and
// its plugin subprojects. The generated .android directory is not committed.
val flutterInclude = settingsDir.parentFile.resolve("flutter-ui/.android/include_flutter.groovy")
require(flutterInclude.isFile) {
    "Flutter module bootstrap is missing. Run 'flutter pub get' in ../flutter-ui first."
}
apply(from = flutterInclude)
