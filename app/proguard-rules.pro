# ==============================================================================
# BooxBook Proguard & R8 Optimization Rules
# ==============================================================================

# --- Room Database ---
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void clearAllTables();
    <methods>;
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# --- BooxBook Core Models ---
-keep class com.booxbook.core.model.** { *; }
-keepclassmembers class com.booxbook.core.model.** { *; }

# --- Kotlinx Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# --- JNI & C++ Libmobi Bridge ---
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.booxbook.core.engine.azw3.Azw3Converter { *; }
-keepclassmembers class com.booxbook.core.engine.azw3.Azw3Converter {
    <methods>;
}

# --- Readium Kotlin Toolkit ---
-keep class org.readium.r2.** { *; }
-keepclassmembers class org.readium.r2.** { *; }
-dontwarn org.readium.r2.**
-dontwarn com.mcxiaoke.koi.**
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Coil 3 Image Loading ---
-keep class coil3.** { *; }
-dontwarn coil3.**

# --- TTS & Media ---
-keep class com.booxbook.core.tts.** { *; }
-keepclassmembers class com.booxbook.core.tts.** { *; }
-keep class androidx.media.** { *; }

# --- Compose & Material 3 Expressive ---
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.UnsafeCasts { *; }
-dontwarn dagger.hilt.**
