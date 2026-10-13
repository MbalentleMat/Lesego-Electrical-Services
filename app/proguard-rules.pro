# ─────────────────────────────────────────────────────────────────────────────
#  Lesego Electrical Services — ProGuard / R8 Rules
#  Applied to release builds to minify and obfuscate the APK.
# ─────────────────────────────────────────────────────────────────────────────

# Keep line numbers in stack traces so crash reports are readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Kotlin ──────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# ─── Firebase ────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ─── Retrofit + OkHttp ───────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson model classes (used with Retrofit GsonConverterFactory)
-keep class com.lesego.electrical.data.models.** { *; }
-keepclassmembers class com.lesego.electrical.data.models.** {
    <fields>;
    <init>(...);
}

# ─── Room ────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# ─── Hilt ────────────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# ─── Glide ───────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ─── Credential Manager / Google Identity ────────────────────────────────────
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.** { *; }
-dontwarn androidx.credentials.**

# ─── Navigation Safe Args ────────────────────────────────────────────────────
-keep class com.lesego.electrical.ui.**.Args { *; }
-keep class com.lesego.electrical.ui.**.Directions { *; }

# ─── ViewBinding ─────────────────────────────────────────────────────────────
-keep class com.lesego.electrical.databinding.** { *; }

# ─── App-specific ────────────────────────────────────────────────────────────
-keep class com.lesego.electrical.LesesoApp
-keep class com.lesego.electrical.MainActivity
-keep class com.lesego.electrical.SplashActivity
-keep class com.lesego.electrical.auth.** { *; }
