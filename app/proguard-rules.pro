# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.apexaurum.pocket.**$$serializer { *; }
-keepclassmembers class com.apexaurum.pocket.** { *** Companion; }
-keepclasseswithmembers class com.apexaurum.pocket.** { kotlinx.serialization.KSerializer serializer(...); }
