package com.grabbit.data.search

import com.grabbit.core.AppLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val REHYDRATION_SCRIPT =
    Regex(
        """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

private const val TAG = "TikTokMeta"
private const val DEFAULT_SCOPE = "__DEFAULT_SCOPE__"
private const val VIDEO_DETAIL = "webapp.video-detail"
private const val ITEM_INFO = "itemInfo"
private const val ITEM_STRUCT = "itemStruct"

internal data class TikTokVideoMeta(
    val title: String?,
    val thumbnail: String?,
    val uploader: String?,
    val durationSeconds: Long?,
)

/** Reads title, cover and duration from a TikTok video page's rehydration JSON. */
internal object TikTokVideoMetaReader {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(url: String): TikTokVideoMeta? {
        val html = SearchHttp.get(url)
        val body = REHYDRATION_SCRIPT.find(html)?.groupValues?.get(1) ?: return null
        return try {
            val item =
                json
                    .parseToJsonElement(body)
                    .jsonObject
                    .child(DEFAULT_SCOPE)
                    ?.child(VIDEO_DETAIL)
                    ?.child(ITEM_INFO)
                    ?.child(ITEM_STRUCT)
                    ?: return null
            val video = item.child("video")
            TikTokVideoMeta(
                title = item.text("desc"),
                thumbnail = video?.text("cover"),
                uploader = item.child("author")?.text("uniqueId"),
                durationSeconds = video?.number("duration"),
            )
        } catch (e: IllegalArgumentException) {
            AppLog.d(TAG) { "rehydration parse failed: ${e.message}" }
            null
        }
    }

    private fun JsonObject.child(key: String): JsonObject? = this[key]?.jsonObject

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
