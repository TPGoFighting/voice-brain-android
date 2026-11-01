# Proguard rules for TP Voice Brain
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.jetbrains.annotations.* <fields>;
    @org.jetbrains.annotations.* <methods>;
}
-keepattributes Signature
-keepattributes InnerClasses
-dontwarn okio.**
-dontwarn okhttp3.**
