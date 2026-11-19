# Keep Firestore & Moshi serialized models
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class com.example.data.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*
