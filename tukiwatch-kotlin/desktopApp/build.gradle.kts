import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    // `:shared` uses material3 as an implementation dependency, so it is not on
    // this module's compile classpath. Keep the same version as the app.
    implementation(libs.compose.material3)
    implementation(libs.compose.uiToolingPreview)

    // UI tests drive the real Compose tree; engine/DB are injected fakes.
    testImplementation(libs.compose.uiTestJUnit4)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.sqldelight.jdbc)
}

// Desktop UI tests render a real ComposeWindow, so AWT cannot be headless.
// The UI test class probes AWT first and skips itself when no display exists.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "false")
}

compose.desktop {
    application {
        mainClass = "com.snowballons.tukiwatch.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.snowballons.tukiwatch"
            packageVersion = "1.1.5"
        }
    }
}