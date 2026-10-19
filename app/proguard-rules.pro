# RetroBit Agent — R8 rules for the release build.
#
# Compose, Navigation, Lifecycle, Coroutines and OkHttp ship their own consumer
# rules, so only the reflection bridges that live in this app need help.

# Keep annotations: @JavascriptInterface and kotlinx.serialization rely on them.
-keepattributes *Annotation*, InnerClasses, Signature

# --- WebView <-> Kotlin bridge ----------------------------------------------
# Methods are looked up by name from JavaScript, so they cannot be renamed or
# removed even though nothing in Kotlin calls them.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class dev.retrobit.agent.preview.PreviewBridge { *; }

# --- kotlinx.serialization --------------------------------------------------
# Serializers are generated as <Class>$$serializer; R8 may merge or inline the
# companions that the generated code looks up by name.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }

# --- ViewModels -------------------------------------------------------------
# Instantiated through a lambda factory today, but keeping the constructors is
# cheap and protects against a future default factory that reflects.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# OkHttp references optional JSSE providers that are not packaged on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Readable crash reports without leaking the full class map.
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
