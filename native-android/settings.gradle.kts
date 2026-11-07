pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

// Flutter 3.47.9 requires Kotlin Gradle Plugin 2.2.20 or newer.
// Pin the compatible KGP version at the host build level so the generated
// Flutter Android module cannot fall back to the older 2.2.10 template default.
plugins {
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
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
