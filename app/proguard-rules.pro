# yt-dlp / python / ffmpeg are loaded reflectively by the youtubedl-android wrapper.
-keep class com.yausername.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

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
