plugins {
    // Flutter 3.47.9 requires AGP 8.11.1 or newer.
    id("com.android.application") version "8.11.1" apply false
    // Keep KGP beside AGP in the root project classloader. KGP's AGP
    // compatibility check needs access to com.android.build.gradle APIs.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    // Add-to-app plugins using Kotlin DSL (for example video_player_android)
    // compile against FlutterExtension properties such as flutter.compileSdkVersion.
    // Expose the Flutter Gradle plugin on the host build classpath without
    // applying it to the host app; the included Flutter module owns its plugin.
    id("dev.flutter.flutter-gradle-plugin") version "1.0.0" apply false
}
