package com.grabbit.data.ytdlp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Parses `yt-dlp --dump-single-json` output. We parse the raw JSON instead of
 * the library's `VideoInfo` mapper because we need playlist entries and
 * per-format codec data, neither of which the mapper exposes.
 */
object InfoParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String, sourceUrl: String): FetchedMedia {
        val root = json.parseToJsonElement(raw.trim()).jsonObject
        val entries = root.array("entries")
        return if (!entries.isNullOrEmpty()) {
            parseCollection(root, entries, sourceUrl)
        } else {
            parseSingle(root, sourceUrl)
        }
    }

    private fun parseSingle(root: JsonObject, sourceUrl: String): FetchedMedia.Single {
        val id = root.string("id") ?: sourceUrl.hashCode().toString()
        val title = root.string("title") ?: root.string("fulltitle") ?: id
        val formats = root.array("formats").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { parseFormat(it) }
        }
        return FetchedMedia.Single(
            MediaInfo(
                id = id,
                url = root.string("webpage_url") ?: sourceUrl,
                title = title,
                uploader = root.string("uploader") ?: root.string("channel") ?: root.string("creator"),
                durationSeconds = root.seconds("duration"),
                thumbnail = thumbnail(root),
                site = root.string("extractor_key") ?: root.string("extractor"),
                formats = formats,
            ),
        )
    }

    private fun parseCollection(root: JsonObject, entries: JsonArray, sourceUrl: String): FetchedMedia.Collection {
        val seen = mutableSetOf<Int>()
        var nextIndex = 1
        val items = entries.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            var index = obj.int("playlist_index") ?: obj.int("index") ?: 0
            if (index <= 0) index = nextIndex
            // Dynamic playlists (YouTube Mix / radio) can report the same
            // playlist_index for several entries. Duplicate keys in the
            // LazyColumn crash the app, so make every index unique and
            // increasing.
            while (index in seen) index++
            seen.add(index)
            nextIndex = maxOf(nextIndex, index + 1)
            PlaylistItem(
                index = index,
                id = id,
                title = obj.string("title") ?: id,
                url = entryUrl(obj, sourceUrl, id),
                durationSeconds = obj.seconds("duration"),
                thumbnail = thumbnail(obj),
            )
        }
        val declaredCount = root.int("playlist_count") ?: root.int("n_entries")
        return FetchedMedia.Collection(
            title = root.string("title") ?: root.string("id") ?: sourceUrl,
            uploader = root.string("uploader") ?: root.string("channel"),
            thumbnail = thumbnail(root),
            sourceUrl = sourceUrl,
            items = items,
            truncated = declaredCount != null && declaredCount > items.size,
        )
    }

    private fun parseFormat(obj: JsonObject): FormatInfo? {
        val ext = obj.string("ext") ?: return null
        if (ext == "mhtml" || obj.string("protocol") == "mhtml") return null
        val vcodec = obj.string("vcodec")
        val acodec = obj.string("acodec")
        val isVideo = !vcodec.isNullOrBlank() && vcodec != "none"
        val isAudio = !acodec.isNullOrBlank() && acodec != "none"
        if (!isVideo && !isAudio) return null
        return FormatInfo(
            id = obj.string("format_id") ?: return null,
            ext = ext,
            height = obj.int("height") ?: 0,
            fps = obj.int("fps") ?: 0,
            vcodec = vcodec,
            acodec = acodec,
            abr = obj.double("abr") ?: 0.0,
            tbr = obj.double("tbr") ?: 0.0,
            fileSize = obj.long("filesize") ?: obj.long("filesize_approx") ?: 0L,
            note = obj.string("format_note"),
        )
    }

    private fun thumbnail(obj: JsonObject): String? {
        obj.string("thumbnail")?.let { return it }
        return obj.array("thumbnails")?.lastOrNull()?.let { element ->
            (element as? JsonObject)?.string("url")
        }
    }

    private fun entryUrl(entry: JsonObject, sourceUrl: String, id: String): String {
        entry.string("webpage_url")?.takeIf { it.startsWith("http") }?.let { return it }
        entry.string("url")?.takeIf { it.startsWith("http") }?.let { return it }
        val ieKey = entry.string("ie_key") ?: entry.string("extractor_key") ?: ""
        return if (ieKey.contains("Youtube", ignoreCase = true)) {
            "https://www.youtube.com/watch?v=$id"
        } else {
            sourceUrl
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.seconds(key: String): Long =
        ((this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0).toLong()

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
}
