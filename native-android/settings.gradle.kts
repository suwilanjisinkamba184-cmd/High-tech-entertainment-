pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

// AGP 9.0.1 provides built-in Kotlin; do not apply the legacy Kotlin Android plugin.
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
    "Flutter module bootstrap is missing. Run flutter pub get in ../flutter-ui first."
}
apply(from = flutterInclude)
