# Keep Room entities/DAO metadata
-keep class com.mockpilot.data.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.mockpilot.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# osmdroid
-keep class org.osmdroid.** { *; }

# Reflection into private Location fields (self-test) — keep our own reflection helpers
-keepclassmembers class android.location.Location { *; }
