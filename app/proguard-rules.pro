# Kotlinx serialization (puzzle JSON + navigation routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.maslarski.crossword.**$$serializer { *; }
-keepclassmembers class com.maslarski.crossword.** { *** Companion; }
-keepclasseswithmembers class com.maslarski.crossword.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep line numbers for readable Crashlytics stack traces; hide original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose/debug/info logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
