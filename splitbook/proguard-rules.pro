# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent {}
-keep class * extends dagger.hilt.internal.GeneratedComponentManager {}
-keep class * extends dagger.hilt.internal.UnsafeCasts {}

# Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep models used by JSON or Bundles (none yet)

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
