-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.madar.launcher.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
