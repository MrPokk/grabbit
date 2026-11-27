package com.grabbit.data.preview

import com.grabbit.core.AppLog
import com.grabbit.core.Links
import com.grabbit.core.Net
import com.grabbit.core.Timing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OEmbed"
private const val HTTP_OK = 200
private const val HTTP_MAX_SUCCESS = 299
private const val NANOS_PER_MILLI = 1_000_000.0

data class LinkPreview(
    val title: String,
    val author: String?,
    val thumbnailUrl: String?,
)

object OEmbedEndpoints {
    private const val YOUTUBE_ENDPOINT = "https://www.youtube.com/oembed?format=json&url="
    private const val TIKTOK_ENDPOINT = "https://www.tiktok.com/oembed?url="

    fun forUrl(url: String): String? =
        when {
            Links.isYouTube(url) -> YOUTUBE_ENDPOINT + encode(url)
            Links.isTikTok(url) -> TIKTOK_ENDPOINT + encode(url)
            else -> null
        }

    private fun encode(url: String): String = URLEncoder.encode(url, Charsets.UTF_8.name())
}

@Singleton
class OEmbedPreviewProvider
    @Inject
    constructor() {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        suspend fun fetch(url: String): LinkPreview? =
            withContext(Dispatchers.IO) {
                val endpoint = OEmbedEndpoints.forUrl(url) ?: return@withContext null
                val startedAt = System.nanoTime()
                try {
                    val body = request(endpoint) ?: return@withContext null
                    val preview = parse(body)
                    val elapsedMs = ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).toLong()
                    AppLog.d(TAG) { "oEmbed ${if (preview != null) "hit" else "empty"} in ${elapsedMs}ms" }
                    preview
                } catch (e: IOException) {
                    logFetchError("oEmbed request failed", e)
                    null
                } catch (e: SerializationException) {
                    logFetchError("oEmbed response is not JSON", e)
                    null
                } catch (e: IllegalArgumentException) {
                    logFetchError("oEmbed request is invalid", e)
                    null
                }
            }

        private fun logFetchError(
            message: String,
            e: Exception,
        ) {
            AppLog.d(TAG) { "$message: ${e.message}" }
        }

        private fun request(endpoint: String): String? {
            val connection = URL(endpoint).openConnection()
            check(connection is HttpURLConnection) { "Unexpected connection type: ${connection::class.simpleName}" }
            connection.apply {
                connectTimeout = Timing.OEMBED_TIMEOUT_MS
                readTimeout = Timing.OEMBED_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Net.USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            return try {
                val code = connection.responseCode
                if (code !in HTTP_OK..HTTP_MAX_SUCCESS) return null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

        private fun parse(body: String): LinkPreview? {
            val root = json.parseToJsonElement(body).jsonObject
            val title = root.string("title") ?: return null
            return LinkPreview(
                title = title,
                author = root.string("author_name"),
                thumbnailUrl = root.string("thumbnail_url"),
            )
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
