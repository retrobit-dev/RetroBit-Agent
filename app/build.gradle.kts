import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * Signing configuration.
 *
 * CI provides the keystore as base64 env vars; locally we fall back to the demo
 * key in `signing/keystore.properties` so `./gradlew assembleRelease` works out
 * of the box. If neither exists the release build stays unsigned instead of
 * failing the whole build.
 */
private fun loadSigningConfig(): Properties? {
    val props = Properties()
    val local = rootProject.file("signing/keystore.properties")
    if (local.exists()) {
        local.inputStream().use { props.load(it) }
    }
    val b64 = System.getenv("RELEASE_KEYSTORE_BASE64")
    if (!b64.isNullOrBlank()) {
        val decoded = java.io.File(
            System.getenv("RUNNER_TEMP") ?: System.getProperty("java.io.tmpdir"),
            "retrobit-release.jks",
        )
        decoded.writeBytes(java.util.Base64.getDecoder().decode(b64))
        props.setProperty("storeFile", decoded.absolutePath)
        System.getenv("RELEASE_KEYSTORE_PASSWORD")?.let { props.setProperty("storePassword", it) }
        System.getenv("RELEASE_KEY_ALIAS")?.let { props.setProperty("keyAlias", it) }
        System.getenv("RELEASE_KEY_PASSWORD")?.let { props.setProperty("keyPassword", it) }
    }
    val storePath = props.getProperty("storeFile")?.ifBlank { null } ?: return null
    val store = rootProject.file(storePath)
    if (!store.exists()) return null
    props.setProperty("storeFile", store.absolutePath)
    return props
}

val signingProps = loadSigningConfig()

android {
    namespace = "dev.retrobit.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.retrobit.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Same key as release so `adb install -r` (and Android's updater) accepts
            // swapping a debug build for a release build of the same version.
            signingConfig = signingConfigs.findByName("release")
            applicationIdSuffix = ""
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
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
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
