# Room and Compose ship their own consumer rules.

# MediaPipe calls into these classes from native code.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
# MediaPipe logs through Flogger, which finds its caller by walking the stack for its own class
# names. Renamed, it throws "no caller found" and every MediaPipe task fails to start.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.mediapipe.proto.**
-dontwarn javax.lang.model.**
