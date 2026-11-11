pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

// Keep AGP and KGP declarations in the root project plugins block so their
// implementation classes share the project plugin classloader. Putting KGP
// in the settings plugins block isolates it from AGP's BaseExtension API.
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
rootProject.name = "HTEUniverse"
include(":app")

val flutterInclude = settingsDir.parentFile.resolve("flutter-ui/.android/include_flutter.groovy")
require(flutterInclude.isFile) {
    "Flutter module bootstrap is missing. Run flutter pub get in ../flutter-ui first."
}
apply(from = flutterInclude)
