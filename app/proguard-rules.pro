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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Keep Xposed module classes
-keep class com.hhvvg.ecm.** { *; }
-keep class de.robv.android.xposed.** { *; }

# Keep Android 16 clipboard APIs
-keep class android.content.ClipboardManager { 
    public *; 
}

# Keep ClipData and related classes
-keep class android.content.ClipData { *; }
-keep class android.content.ClipDescription { *; }

# Keep PersistableBundle for sensitive content handling
-keep class android.os.PersistableBundle { *; }

# Don't warn about missing Android 16 APIs
-dontwarn android.content.ClipboardManager$getPrimaryClipSource
-dontwarn android.os.Build$VERSION_CODES
