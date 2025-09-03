# Proguard rules placeholder.

# Keep Google Sign-In / Play Services auth classes used for token acquisition
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**
