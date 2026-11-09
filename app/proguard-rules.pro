# R8 rules for the release build (on top of proguard-android-optimize.txt and the rules the
# AndroidX/Material libraries ship themselves). Manifest components, custom views in layouts and
# everything referenced from resources are kept by the build automatically.

# Readable stack traces: keep file and line numbers, hide the real file name behind "SourceFile".
# Crash reports are translated back with the mapping file that every release contains.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The JavaScript bridge uses WebViewCompat.addWebMessageListener (no @JavascriptInterface),
# configuration is parsed with org.json by hand, enums are looked up by their own key fields:
# nothing in the app relies on class, method or field names at run time, so no further keep rules.
