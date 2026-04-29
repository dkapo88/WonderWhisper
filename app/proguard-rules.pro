# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep accessibility service classes
-keep class * extends android.accessibilityservice.AccessibilityService
-keep class com.slumdog88.dictationkeyboardai.DictationAccessibilityService { *; }

# Keep service classes
-keep class com.slumdog88.dictationkeyboardai.BubbleOverlayService { *; }

# Keep data classes used with Gson
-keep class com.slumdog88.dictationkeyboardai.Note { *; }
-keep class com.slumdog88.dictationkeyboardai.ReformatPrompt { *; }

# Gson specific rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp specific rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Keep reflection-based classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}