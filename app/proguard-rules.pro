# Giữ nguyên các lớp model TensorFlow Lite / ML Kit dùng reflection.
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.mlkit.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}
