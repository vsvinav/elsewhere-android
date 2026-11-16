# kotlinx-serialization: the plugin-generated serializers are reached by reflection from Json.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> { static <1>$Companion Companion; }
-if @kotlinx.serialization.Serializable class ** { static **$* *; }
-keepclassmembers class <2>$<3> { kotlinx.serialization.KSerializer serializer(...); }
-if @kotlinx.serialization.Serializable class ** { public static ** INSTANCE; }
-keepclassmembers class <1> { public static <1> INSTANCE; kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.elsewhere.app.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.elsewhere.app.** { *** Companion; }

# Tink picks key managers and primitives up by name.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# Room entities: the processor emits the DAOs, but the columns map to field names.
-keep @androidx.room.Entity class * { *; }

# OkHttp / Retrofit and their optional platform hooks.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
