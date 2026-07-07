# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Moshi
-keep class com.example.data.** { *; }
-keep @com.squareup.moshi.JsonClass class *

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Удаляем логи в релизе (опционально)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}