plugins {
    id("com.android.application") version "9.5.1" apply false
    // Only puts Kotlin 2.4 on the classpath; AGP 9 compiles Kotlin itself, so the app does not apply it.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("io.github.takahirom.roborazzi") version "1.78.0" apply false
}
