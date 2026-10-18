plugins {
    id("com.android.application") version "9.4.1" apply false
    // Only puts Kotlin 2.4 on the classpath; AGP 9 compiles Kotlin itself, so the app does not apply it.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("io.github.takahirom.roborazzi") version "1.76.0" apply false
}
