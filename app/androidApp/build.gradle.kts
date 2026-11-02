import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.kotlinSerialization)
}

val appVersionName = providers.gradleProperty("APP_VERSION_NAME").get()
val appVersionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(project(":ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.runtimeKtx)
    implementation(libs.datastore.preferences)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.snowballons.tukiwatch"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Same package as the Expo release (com.tukiwatch.app) so the KMP build
        // upgrades it in place. Release builds must be signed with the old
        // keystore for Android to accept the update.
        applicationId = "com.tukiwatch.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Must stay above the Expo app's last versionCode (12) to install as
        // an upgrade rather than a downgrade. Bump APP_VERSION_CODE in
        // app/gradle.properties for each release.
        versionCode = appVersionCode
        versionName = appVersionName
        ndk {
            // Python 3.11 supports 32-bit + 64-bit. arm64 + armeabi-v7a cover
            // physical devices (no emulators per project decision).
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            val storeFilePath = project.findProperty("TUKIWATCH_KEYSTORE_FILE") as? String
                ?: System.getenv("TUKIWATCH_KEYSTORE_FILE")
            val storePasswordProp = project.findProperty("TUKIWATCH_KEYSTORE_PASSWORD") as? String
                ?: System.getenv("TUKIWATCH_KEYSTORE_PASSWORD")
            val keyAliasProp = project.findProperty("TUKIWATCH_KEY_ALIAS") as? String
                ?: System.getenv("TUKIWATCH_KEY_ALIAS")
            val keyPasswordProp = project.findProperty("TUKIWATCH_KEY_PASSWORD") as? String
                ?: System.getenv("TUKIWATCH_KEY_PASSWORD")

            if (!storeFilePath.isNullOrBlank() && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            } else {
                // Fallback to debug keystore for development & unconfigured CI
                initWith(getByName("debug"))
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Chaquopy: Python 3.11 for armeabi-v7a support, Streamlink pinned.
// The build defaults to `python3.11`; override with
// -Pchaquopy.buildPython=/path/to/python3.11 or CHAQUOPY_BUILD_PYTHON.
chaquopy {
    defaultConfig {
        version = "3.11"
        val configuredPython = (project.findProperty("chaquopy.buildPython") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: System.getenv("CHAQUOPY_BUILD_PYTHON")?.takeIf { it.isNotBlank() }
        buildPython(configuredPython ?: "python3.11")
        pip {
            install("streamlink==8.6.2")
        }
    }
}
