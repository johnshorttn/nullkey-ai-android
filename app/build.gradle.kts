import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Release signing is sourced from a git-ignored `keystore.properties` (local) or
// environment variables (CI / smoke). Environment variables win so CI and
// `scripts/smoke-signed-aab.sh` never accidentally use a developer keystore.
// No keystore or password is ever committed. When no keystore is configured,
// `release` stays unsigned so ordinary builds still work.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

fun releaseSigningValue(propKey: String, envKey: String): String? =
    (System.getenv(envKey) ?: keystoreProperties.getProperty(propKey))?.takeIf { it.isNotBlank() }

fun resolveReleaseStoreFile(path: String): File {
    val asFile = File(path)
    return if (asFile.isAbsolute) asFile else rootProject.file(path)
}

val releaseStoreFilePath: String? = releaseSigningValue("storeFile", "KEYSTORE_FILE")
val releaseStoreFileResolved: File? = releaseStoreFilePath?.let { resolveReleaseStoreFile(it) }

// Release R8 is on by default and does not affect debug or unit tests.
// Environment wins over -P so CI can force a value. Unset means minify on.
//   ./gradlew :app:bundleRelease -Pnullkey.releaseMinify=false
//   NULLKEY_RELEASE_MINIFY=false ./gradlew :app:bundleRelease
fun parseReleaseMinifyFlag(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
    null, "" -> null
    "1", "true", "on", "yes" -> true
    "0", "false", "off", "no" -> false
    else -> error(
        "NULLKEY_RELEASE_MINIFY / -Pnullkey.releaseMinify must be true or false (got '$raw')"
    )
}

val minifyRelease: Boolean =
    parseReleaseMinifyFlag(System.getenv("NULLKEY_RELEASE_MINIFY"))
        ?: parseReleaseMinifyFlag(findProperty("nullkey.releaseMinify")?.toString())
        ?: true

android {
    namespace = "com.nullverse.nullkeyai"
    compileSdk = 36

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    defaultConfig {
        applicationId = "com.nullverse.nullkeyai"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (releaseStoreFileResolved != null) {
            create("release") {
                storeFile = releaseStoreFileResolved
                storePassword = releaseSigningValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = releaseSigningValue("keyAlias", "KEY_ALIAS")
                keyPassword = releaseSigningValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Unit tests and the debug APK stay unminified. R8 is release-only.
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = minifyRelease
            // Resource shrinking requires minify. Both follow the same opt-out flag.
            isShrinkResources = minifyRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFileResolved != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

tasks.register("printReleaseSigningStatus") {
    group = "help"
    description = "Prints whether release signing is configured. Never prints secrets."
    doLast {
        val configured = releaseStoreFileResolved != null
        val exists = releaseStoreFileResolved?.isFile == true
        println("applicationId=com.nullverse.nullkeyai")
        println("versionName=1.2")
        println("versionCode=12")
        println("minSdk=24")
        println("compileSdk=36")
        println("targetSdk=36")
        println("minifyRelease=$minifyRelease")
        println("shrinkResourcesRelease=$minifyRelease")
        println("releaseSigningConfigured=$configured")
        println("releaseStoreFileExists=$exists")
        println("playTargetApiNote=compileSdk/targetSdk 36; see docs/PLAY_STORE.md")
    }
}

tasks.register("checkReleaseScaffold") {
    group = "verification"
    description = "Verifies Play/AAB signing placeholders and gitignore. Does not read secrets."
    doLast {
        exec {
            workingDir = rootProject.projectDir
            commandLine("bash", rootProject.file("scripts/check-release-scaffold.sh").absolutePath)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Bundled Latin OCR model (libmlkit_google_ocr_pipeline.so). The Clearcut
    // uploader is excluded so ML Kit telemetry has no network backend. The
    // manifest also strips INTERNET and ACCESS_NETWORK_STATE if a library
    // tries to merge them. Do not switch this to the unbundled Play Services
    // model; that path downloads weights at runtime.
    implementation("com.google.mlkit:text-recognition:16.0.1") {
        exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
    }
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:$room")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
