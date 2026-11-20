# ==========================================
# R8 / ProGuard Hardening (matched to LunarTag)
# ==========================================

# Strip all Log.* calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
    public static int println(...);
}

# Flatten package names and obfuscate aggressively
-repackageclasses 'o'
-allowaccessmodification

# Hide original source file names and local variable / parameter names from decompilers
-renamesourcefileattribute 'SourceFile'
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ==========================================
# Required Keep Rules for Txtify2
# ==========================================

# Protect JNI bridge class & methods so C++ libtxtify_native.so links properly
-keep class com.txtify.app.utils.NativeBridge { *; }

# Protect Serializable model passed between ZipContentsActivity and MainActivity
-keep class com.txtify.app.ZipEntryItem { *; }

# Protect Lifecycle observer & AdMob App Open callbacks
-keep class com.txtify.app.AppOpenManager { *; }
-keepclassmembers class * implements androidx.lifecycle.DefaultLifecycleObserver {
    public void on*(androidx.lifecycle.LifecycleOwner);
}

# Preserve PDF class rules (removed -keepresources which R8 does not support)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**