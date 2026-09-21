# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.legwork.**$$serializer { *; }
-keepclassmembers class app.legwork.** { *** Companion; }
-keepclasseswithmembers class app.legwork.** { kotlinx.serialization.KSerializer serializer(...); }

# Mobile Wallet Adapter and web3
-keep class com.solana.** { *; }
-keep class com.funkatronics.** { *; }
-dontwarn com.solana.**

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.ditchoom.buffer.BufferFactoryJvm
