# Grabbit

Native Android downloader for YouTube, TikTok and the rest of the yt-dlp ecosystem.

**Open source, MIT licensed.** See [`LICENSE`](LICENSE).

Kotlin + Jetpack Compose (Material 3), fully offline: the download engine is bundled
[yt-dlp](https://github.com/yt-dlp/yt-dlp) plus FFmpeg, running on-device. No server, no account, no telemetry.

## Features

**Links**
- Share sheet target for plain text and `VIEW` intents (`youtube.com`, `youtu.be`, `tiktok.com`, `instagram.com`, …)
- Clipboard detection on app start
- Metadata preview (title, uploader, duration, thumbnail) before downloading
- Playlists and channels: full listing with per-item selection, select all / none

**Formats**
- Video: MP4 / MKV / WebM, 144p → 8K (whatever the source offers)
- Audio: MP3 / M4A / Opus / FLAC at 128 / 192 / 256 / 320 kbps
- Embedded cover art and metadata tags
- Subtitles (auto-generated included), SponsorBlock segment removal
- TikTok: no-watermark stream selection

**Queue**
- Persistent queue with progress, speed, ETA, pause / resume / cancel / retry
- Configurable parallel downloads (1–5)
- Foreground service with ongoing notification, per-item actions and completion alerts
- The engine lives in the application scope, so downloads continue while the UI is closed
  (the queue itself is in memory and is lost if the process is killed)

**Output**
- Saves to Music / Movies via MediaStore, or to a folder picked with SAF
- Custom filename template (default `%(title)s - %(uploader)s`)

**Library & tools**
- Room-backed history: search, open, share, delete, re-download
- Post-download FFmpeg conversion, plus a standalone convert screen for any local file
- Settings: theme (system / light / dark), Material You dynamic color, download defaults, concurrency, cookies import, proxy, yt-dlp updater
- Human-readable error messages instead of raw yt-dlp output
- English and Russian UI

## Build

Requirements: JDK 17+ (JDK 21 used here), Android SDK with platform 36 and build-tools 36.0.0.

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Alternatively put the SDK path in `local.properties`:

```properties
sdk.dir=/path/to/android-sdk
```

Unit tests (link parsing, yt-dlp request building, metadata parsing):

```bash
./gradlew :app:testDebugUnitTest
```

`minSdk 26`, `targetSdk 36`. The APK ships yt-dlp, Python and FFmpeg native binaries for
`arm64-v8a`, `armeabi-v7a` and `x86_64`, which makes the universal debug APK about 170 MB —
that is the price of working without a backend. Ship per-ABI splits (`splits { abi { ... } }`)
for distribution to cut it to roughly a third.

Verified with AGP 8.13.2, Gradle 8.14.3, Kotlin 2.3.21, KSP 2.3.14, Hilt 2.58.
The Kotlin version is pinned on purpose: Hilt 2.59 and older cannot read Kotlin 2.4 class
metadata (`Provided Metadata instance has version 2.4.0, while maximum supported version is
2.3.0`), while Hilt 2.60+ requires AGP 9. Move both together if you upgrade.

## Updating yt-dlp

Extractors break whenever a site changes. Grabbit updates the binary at runtime:

- **Settings → yt-dlp → Check for updates** downloads the latest stable release into app storage.
- **Automatic check** (on by default) runs at most once a day when the app starts; the
  current version is shown in Settings.
- The bundled binary inside the APK is only the starting point — after the first update the
  app never needs a new APK just because YouTube changed something.

## Architecture

Single `:app` module, MVVM with unidirectional state.

```
core/          link parsing, formatting, error mapping, mime types
data/ytdlp/    YoutubeDL wrapper, yt-dlp request builder, metadata parser
data/download/ queue engine (state machine), repository, models
data/media/    MediaStore / legacy / SAF writers
data/convert/  FFmpeg execution
data/db/       Room history
data/settings/ DataStore preferences
service/       foreground service + notifications
ui/            Compose screens (home, queue, history, settings, convert) + theme
```

- **Hilt** for DI, **Room** for history, **DataStore** for settings, **Coil 3** for thumbnails.
- Downloads are performed into the app cache (`cache/downloads/<taskId>`), then copied to the
  final destination. yt-dlp reports the resulting path via `--print-to-file after_move:filepath`,
  so post-processing (merge, embed, remux) never confuses the pipeline.
- FFmpeg is invoked directly through `nativeLibraryDir/libffmpeg.so`; the shared libraries
  are unpacked on first run.
- `DownloadService` is only a foreground anchor for notifications — the actual engine runs in
  the application scope, so downloads keep going while the UI is gone.
- Material 3 with dynamic color when the platform supports it, static palette otherwise,
  edge-to-edge, predictive back.

## Why no Rust

yt-dlp and FFmpeg already do the heavy lifting (parsing, format selection, merging,
remuxing). A Rust/JNI layer would add build complexity and native surface area without
moving any work off the existing binaries, so the whole app is pure Kotlin.

## Limitations

- Verified by compilation and unit tests; no on-device run in this environment.
- Downloading content you do not have the rights to may violate a site's terms of service.
  Use it for content you own or that is explicitly licensed for download.
- DRM-protected and login-walled content is not supported; import cookies in Settings for
  age-restricted or member-only videos.
