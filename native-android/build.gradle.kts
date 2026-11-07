plugins {
    // AGP 9 removes the legacy BaseVariant API that caused the Kotlin Android
    // plugin to fail during configuration. Flutter 3.47 supports AGP 9 built-in Kotlin.
    id("com.android.application") version "9.0.1" apply false
}
