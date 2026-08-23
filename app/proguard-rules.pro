# kotlinx.serialization keeps its generated serializers on the annotated classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.sacredtxd.connectionchecker.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.sacredtxd.connectionchecker.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
