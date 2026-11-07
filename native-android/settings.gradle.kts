pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

// Kotlin 2.2.21 is required for full compatibility with AGP 8.11.1.
// Kotlin 2.2.10 supports AGP only through 8.10.0 and fails during plugin
// application with NoClassDefFoundError: com/android/build/gradle/api/BaseVariant.
// Keep Gradle 8.14.3, JDK 17, and the Flutter-generated integration unchanged.
plugins {
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
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
    "Flutter module bootstrap is missing. Run flutter pub get in ../flutter-ui first."
}
apply(from = flutterInclude)
