# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to the ones
# in the Android Gradle plugin.
# You can keep or remove the rules below.

# WebView often needs to keep classes that are accessed via reflection.
-keepclassmembers class * extends android.webkit.WebChromeClient {
    *;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    *;
}
