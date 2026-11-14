import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("io.github.takahirom.roborazzi")
}

android {
    lint {
        // Release has no 32-bit x86 on purpose (see buildTypes.release.ndk).
        disable += "ChromeOsAbiSupport"
    }

    namespace = "de.ictbaden.industrialkioskbrowser"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.ictbaden.industrialkioskbrowser"
        minSdk = 26
        targetSdk = 36
        // The CI passes -PversionCode (run number) and -PversionName (e.g. 0.6.2-ci.12).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 9
        versionName = findProperty("versionName") as String? ?: "0.7.1"
        buildConfigField("String", "COMMIT", "\"${System.getenv("GITHUB_SHA")?.take(7) ?: "local"}\"")
    }

    // Release key from the CI secrets (richtlinien/05-signieren.md); without it the release APK stays unsigned.
    val keystore = System.getenv("ANDROID_KEYSTORE_FILE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            // Rugged Android devices are ARM; x86_64 is for Chromebooks (without it Play drops nearly
            // all of them). 32-bit x86 is left out: the OCR library of ML Kit is about 11 MB per ABI in
            // the universal APK, Play delivers only the device's ABI from the bundle. Same as Industrial
            // Keyboard. Debug builds keep all four for the emulator.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    // The language can be switched in the app and by the MDM (richtlinien/13): an app bundle from
    // Play must therefore contain every language, not only the one of the device.
    bundle {
        language {
            enableSplit = false
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Screenshot tests (docs images) only run on request: ./gradlew recordRoborazziDebug -Pscreenshots
                if (!project.hasProperty("screenshots")) it.exclude("**/screenshots/**")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("com.google.android.material:material:1.14.0")
    // Camera scanning on devices without a scan engine (issue #48): CameraX preview and analysis,
    // decoding with zxing-cpp (same engine as Lupe). 1.5.x on purpose: from 1.6
    // camera-camera2 brings Dagger. camera-view without camera-video (media3, Guava).
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3") {
        // Only PreviewView is used; video recording (with media3 and Guava) is not needed.
        exclude(group = "androidx.camera", module = "camera-video")
        exclude(group = "androidx.media3")
    }
    implementation("io.github.zxing-cpp:android:3.1.1")
    // Text recognition (OCR, issue #49): Google ML Kit with the bundled Latin model, runs offline.
    // Not open source (ML Kit Terms of Service) – Till decided for it; see THIRD-PARTY-NOTICES.txt.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // QR code of the configuration for "Export as barcode" (MIT).
    implementation("io.nayuki:qrcodegen:1.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")
}

// Licence texts for the Info section (richtlinien/07): the repo-root files, added to the APK assets.
// AGP 9 only accepts generated asset folders through the variant API.
abstract class CopyNotices : DefaultTask() {
    @get:InputFiles
    abstract val notices: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        notices.forEach { it.copyTo(File(dir, it.name)) }
    }
}

val copyNotices = tasks.register<CopyNotices>("copyNotices") {
    notices.from(rootProject.file("LICENSE"), rootProject.file("THIRD-PARTY-NOTICES.txt"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(copyNotices, CopyNotices::outputDir) }
}
