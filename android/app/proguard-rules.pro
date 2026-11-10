# kotlinx.serialization keeps its generated serializers via annotations.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.peptideadvisor.core.CorpusRecord {
    *** Companion;
}
-keepclasseswithmembers class com.peptideadvisor.core.CorpusRecord {
    kotlinx.serialization.KSerializer serializer(...);
}

# MediaPipe loads native code and model metadata reflectively.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }

# OkHttp platform shims reference optional deps that aren't on the classpath.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
