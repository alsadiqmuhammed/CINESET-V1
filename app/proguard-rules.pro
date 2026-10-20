-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.madar.launcher.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# MediaPipe GenAI: compile-time AutoValue annotations aren't shipped; the JNI layer reflects on these classes.
-dontwarn com.google.auto.value.**
# Vision inputs belong to the separate framework image artifact; Madar only sends text.
-dontwarn com.google.mediapipe.framework.image.**
-keep class com.google.mediapipe.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
