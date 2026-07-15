# Keep generic kotlin and android attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable,AnnotationDefault,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Maintain Serialized names and keep Serializable classes completely
-keep class * implements java.io.Serializable { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectStreamOutput);
    private void readObject(java.io.ObjectStreamInput);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep our data models
-keep class com.example.data.** { *; }

# Keep our plugin system (interfaces and implementation classes)
-keep class com.example.plugins.** { *; }

# Keep our managers and business logic
-keep class com.example.manager.** { *; }

# Keep view models completely to avoid runtime instantiation and state issues
-keep class com.example.viewmodel.** { *; }

# Keep activities, services, and receivers
-keep class com.example.MainActivity { *; }
-keep class com.example.DownloadService { *; }
-keep class com.example.FloatingVideoService { *; }
-keep class com.example.receiver.** { *; }

# Retrofit rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Moshi rules
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier public @interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Keep Moshi generated adapters
-keep class *JsonAdapter { *; }
-keep class *JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# Room rules
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * {
    @androidx.room.Dao *;
}

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }

# Kotlin Coroutines rules
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keep class kotlinx.coroutines.android.** { *; }

# Media3 / ExoPlayer rules
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Coil rules
-dontwarn coil.**
-keep class coil.** { *; }
