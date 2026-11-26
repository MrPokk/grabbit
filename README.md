# Grabbit

Android downloader for YouTube, TikTok and the rest of the yt-dlp ecosystem. Paste a link, pick format and quality — the file lands in your Music or Movies folder. Playlists, channels, subtitles, SponsorBlock, cover art and tags, persistent download queue with several parallel downloads.

Built with Kotlin + Jetpack Compose (Material 3), English and Russian UI.

Fully offline: bundled yt-dlp + FFmpeg running on the device, no servers and no telemetry. yt-dlp updates itself at runtime, so you don't need a new APK every time YouTube changes something.

Open source, MIT license — see [`LICENSE`](LICENSE).

## Build

```bash
./gradlew :app:assembleDebug
```

Requires JDK 17+ and Android SDK (platform 36). APK lands in `app/build/outputs/apk/debug/`. Details in [`AGENTS.md`](AGENTS.md).

## Limitations

DRM-protected content is not supported. Download only content you own or are licensed to download.
