# =============================================================================
# TukiWatch ProGuard / R8 rules
# =============================================================================
# Applied for release builds alongside proguard-android-optimize.txt.
# Minification is currently disabled (isMinifyEnabled = false in
# androidApp/build.gradle.kts). These rules are retained for a future
# R8-enabled release and do not affect current builds.
# =============================================================================

# ---------------------------------------------------------------------------
# Debugging: keep source file names and line numbers in stack traces
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
# Keep Kotlin metadata so reflection-based libraries (serialization, etc.) work.
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Kotlin coroutines internal machinery
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ---------------------------------------------------------------------------
# Kotlin Serialization
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.snowballons.tukiwatch.**$$serializer { *; }
-keepclassmembers class com.snowballons.tukiwatch.** {
    *** Companion;
}
-keepclasseswithmembers class com.snowballons.tukiwatch.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Jetpack Compose
# ---------------------------------------------------------------------------
# Compose runtime / compiler generates classes that must survive shrinking.
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
# Compose UI state restoration (rememberSaveable).
-keepclassmembers class * implements androidx.compose.runtime.saveable.Saver {
    *;
}

# ---------------------------------------------------------------------------
# AndroidX / Jetpack
# ---------------------------------------------------------------------------
# ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(android.app.Application);
}

# DataStore Preferences
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }

# Activity / ComponentActivity
-keep class androidx.activity.** { *; }

# WindowManager / WindowInsetsController (used for fullscreen immersion)
-keep class androidx.core.view.WindowCompat { *; }
-keep class androidx.core.view.WindowInsetsControllerCompat { *; }

# ---------------------------------------------------------------------------
# Media3 / ExoPlayer
# ---------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
# ExoPlayer extension renderers loaded reflectively
-keepnames class androidx.media3.exoplayer.** { *; }
-keepnames class androidx.media3.common.** { *; }

# ---------------------------------------------------------------------------
# Chaquopy / Python bridge
# ---------------------------------------------------------------------------
# Chaquopy's JNI bridge and generated Python proxy classes must not be
# renamed or removed — the C layer calls them by exact name.
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**
-keepclassmembers class com.chaquo.python.** { *; }

# Python interface classes accessed from Python via JNI
-keep class com.snowballons.tukiwatch.EngineResult { *; }
-keep class com.snowballons.tukiwatch.StreamEngine { *; }

# ---------------------------------------------------------------------------
# SQLDelight / SQLite (used by FavoritesRepository)
# ---------------------------------------------------------------------------
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**
-keep class com.snowballons.tukiwatch.db.** { *; }

# ---------------------------------------------------------------------------
# OkHttp (pulled in by Coil for thumbnail loading)
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.** { *; }
-keepnames class okhttp3.** { *; }

# ---------------------------------------------------------------------------
# Coil (image loading for stream thumbnails)
# ---------------------------------------------------------------------------
-keep class coil3.** { *; }
-dontwarn coil3.**

# ---------------------------------------------------------------------------
# TukiWatch application classes
# ---------------------------------------------------------------------------
# Application entry points and Activities must not be renamed.
-keep class com.snowballons.tukiwatch.TukiWatchApp { *; }
-keep class com.snowballons.tukiwatch.MainActivity { *; }

# Keep all public/internal API surface of the shared module intact so the
# androidApp module can call it after shrinking.
-keep class com.snowballons.tukiwatch.liveness.** { *; }
-keep class com.snowballons.tukiwatch.prefs.** { *; }
-keep class com.snowballons.tukiwatch.ui.state.** { *; }

# ---------------------------------------------------------------------------
# Miscellaneous suppressions
# ---------------------------------------------------------------------------
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
