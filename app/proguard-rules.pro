# Rezona Proguard / R8 Optimization & Keep Rules

# Keep Rezona Models & Serializable classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class com.nayama.music.model.** { *; }
-keep class com.nayama.music.data.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# AndroidX Media3 / ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# Chris Banes Haze & Hardware Blur
-keep class dev.chrisbanes.haze.** { *; }
-dontwarn dev.chrisbanes.haze.**

# Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomOpenHelper

# OkHttp & Retrofit
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# TeamNewPipe / NewPipeExtractor & Rhino Keep Rules
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**

# Firebase, Firestore, Credential Manager & WorkManager
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
