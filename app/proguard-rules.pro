# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Project keep rules (R8 enabled in release)
# ---------------------------------------------------------------------------

# Keep generic signatures & annotations needed by Gson/Retrofit reflection.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Network DTOs are (de)serialized by Gson via reflection — keep names & fields.
-keep class io.proffi.inventory.network.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-dontwarn com.google.gson.**

# Retrofit / OkHttp / Okio (most rules ship as consumer rules; these are safety nets).
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Koin
-dontwarn org.koin.**

# Urovo SDK is provided as a local AAR (compileOnly) / mocked at android.device.*
-keep class android.device.** { *; }
-dontwarn android.device.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**