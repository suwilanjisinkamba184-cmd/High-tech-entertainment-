plugins {
    // Flutter 3.47.9 requires AGP 8.11.1 or newer.
    id("com.android.application") version "8.11.1" apply false
    // Keep KGP beside AGP in the root project classloader. KGP's AGP
    // compatibility check needs access to com.android.build.gradle APIs.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}
