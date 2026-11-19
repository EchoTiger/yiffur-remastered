# Keep kotlinx.serialization serializers & models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ugf.yiffur1.data.api.**$$serializer { *; }
-keepclassmembers class com.ugf.yiffur1.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class com.ugf.yiffur1.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ugf.yiffur1.data.repository.**$$serializer { *; }

# Retrofit & OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
