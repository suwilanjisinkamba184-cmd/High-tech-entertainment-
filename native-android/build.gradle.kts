plugins {
    id("com.android.application") version "9.5.0" apply false
    // AGP 9 provides built-in Kotlin support; only the Compose compiler plugin is applied.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
