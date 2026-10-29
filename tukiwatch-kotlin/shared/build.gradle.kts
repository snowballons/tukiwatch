import java.time.Duration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    android {
       namespace = "com.snowballons.tukiwatch.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.jdbc)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.android)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.datastore.preferences)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

sqldelight {
    databases {
        create("TukiWatchDatabase") {
            packageName.set("com.snowballons.tukiwatch.db")
        }
    }
}

// A hanging coroutine test must fail the build instead of blocking CI forever.
// The liveness suite deadlocked on a shared single-thread dispatcher before.
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(5))
}
