import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "de.ictbaden.enterprisebrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.ictbaden.enterprisebrowser"
        minSdk = 26
        targetSdk = 36
        // The CI passes -PversionCode (run number) and -PversionName (e.g. 0.5.0-ci.12).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 6
        versionName = findProperty("versionName") as String? ?: "0.5.0"
        buildConfigField("String", "COMMIT", "\"${System.getenv("GITHUB_SHA")?.take(7) ?: "local"}\"")
    }

    // Licence texts as assets (richtlinien/07): the same files as in the repo root.
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/notices"))

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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
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
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.13.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")
}

val copyNotices by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"), rootProject.file("THIRD-PARTY-NOTICES.txt"))
    into(layout.buildDirectory.dir("generated/notices"))
}
tasks.named("preBuild") { dependsOn(copyNotices) }
