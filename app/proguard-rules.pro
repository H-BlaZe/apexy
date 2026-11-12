# Room and Compose ship their own consumer rules.

# MediaPipe calls into these classes from native code.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.proto.**
-dontwarn javax.lang.model.**
