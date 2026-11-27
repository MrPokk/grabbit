# yt-dlp / python / ffmpeg are loaded reflectively by the youtubedl-android wrapper.
-keep class com.yausername.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

# Python/Zip utilities used by youtubedl-android for extracting bundled binaries.
-keep class com.yausername.youtubedl_common.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.utils.** { *; }
-keep class com.yausername.youtubedl_common.utils.ZipUtils { *; }

# Keep all python-related classes used for bundled python extraction
-keep class org.python.** { *; }
-keep class org.apache.commons.compress.** { *; }

# Jackson is used by youtubedl-android to map --dump-json output.
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
}

-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.**

# Room / Hilt generated code is handled by their own consumer rules.
-dontwarn org.slf4j.**

# Keep kotlin metadata for serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.** <fields>;
    @kotlinx.serialization.** <methods>;
}

# Keep FetchedMedia sealed class hierarchy for polymorphic serialization
-keep class com.grabbit.data.ytdlp.FetchedMedia { *; }
-keep class com.grabbit.data.ytdlp.FetchedMedia$Single { *; }
-keep class com.grabbit.data.ytdlp.FetchedMedia$Collection { *; }
-keep class com.grabbit.data.ytdlp.MediaInfo { *; }
-keep class com.grabbit.data.ytdlp.FormatInfo { *; }
-keep class com.grabbit.data.ytdlp.PlaylistItem { *; }
-keepclassmembers class com.grabbit.data.ytdlp.* {
    @kotlinx.serialization.Serializer <fields>;
    @kotlinx.serialization.Serializer <methods>;
}

# Keep Room entities for fetch cache
-keep class com.grabbit.data.ytdlp.FetchCacheEntity { *; }

# Keep classes used via reflection in youtubedl-android
-keepclassmembers class * {
    native <methods>;
}

# NewPipeExtractor (GPL-3.0) uses reflection for extractors/services and bundled JS (Rhino).
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.rhino.**
-dontwarn com.google.protobuf.**
-keep class com.github.TeamNewPipe.nanojson.** { *; }
-dontwarn com.github.TeamNewPipe.nanojson.**
# OkHttp/Okio are used directly by our NewPipeExtractor Downloader.
-dontwarn okhttp3.**
-dontwarn okio.**
