# The FFmpeg renderer is loaded by reflection from DefaultRenderersFactory.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
