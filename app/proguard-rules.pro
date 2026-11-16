# ProGuard / R8 configuration for Ideal Self (com.aistudio.idealself.pxlcmp)

# Preserve line numbers for production crash reporting & diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Firestore @IgnoreExtraProperties and serialized data models
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** {
    public <init>();
    public <methods>;
    public <fields>;
}

# Keep Firebase AI Logic & Google Identity / Credential Manager classes
-keep class com.google.firebase.ai.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.firebase.**
