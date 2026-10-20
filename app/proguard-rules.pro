# ==============================================================================
# YaMusic ProGuard / R8 Release Rules
# ==============================================================================

# General Optimization & Code Integrity
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-keepclassmembers enum * { *; }
-dontnote **

# ------------------------------------------------------------------------------
# 1. Strip Debug Logging in Release
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ------------------------------------------------------------------------------
# 2. KotlinX Serialization
# ------------------------------------------------------------------------------
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,allowobfuscation,allowshrinking class * {
    Companion companion;
}
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    <fields>;
    <methods>;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum * {
    @kotlinx.serialization.SerialName <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep all DTO, Domain Models and Updater
-keep class com.korenskoy.yandexmusic.data.dto.** { *; }
-keep class com.korenskoy.yandexmusic.data.cache.PlaybackSnapshot { *; }
-keep class com.korenskoy.yandexmusic.data.updater.** { *; }
-keep class com.korenskoy.yandexmusic.domain.model.** { *; }
-keep class com.korenskoy.yandexmusic.data.radio.** { *; }

# ------------------------------------------------------------------------------
# 3. Hilt & Dagger
# ------------------------------------------------------------------------------
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class androidx.hilt.navigation.compose.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @dagger.Provides <methods>;
}

# ------------------------------------------------------------------------------
# 4. Retrofit & OkHttp
# ------------------------------------------------------------------------------
-keepattributes Signature
-keepattributes Exceptions
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ------------------------------------------------------------------------------
# 5. Media3 & ExoPlayer
# ------------------------------------------------------------------------------
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.exoplayer.hls.** { *; }
-keep class com.korenskoy.yandexmusic.data.player.** { *; }

# ------------------------------------------------------------------------------
# 6. Coil & Palette
# ------------------------------------------------------------------------------
-keep class coil.** { *; }
-dontwarn coil.**
-keep class androidx.palette.graphics.** { *; }

# ------------------------------------------------------------------------------
# 7. AndroidX Security Crypto & Preferences
# ------------------------------------------------------------------------------
-keep class androidx.security.crypto.** { *; }
-keep class androidx.datastore.preferences.** { *; }

# ------------------------------------------------------------------------------
# 8. Coroutines
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-dontwarn kotlinx.coroutines.**
