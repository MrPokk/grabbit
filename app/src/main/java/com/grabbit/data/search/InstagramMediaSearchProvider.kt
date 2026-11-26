package com.grabbit.data.search

import com.grabbit.core.AppLog
import com.grabbit.core.ErrorMapper
import com.grabbit.core.Limits
import com.grabbit.core.MediaPlatform
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import kotlinx.coroutines.CancellationException
import java.util.Locale
import javax.inject.Inject

private const val TAG = "InstagramSearch"

/** Turns a free-form query into the hashtag name used by Instagram's tag feed. */
internal object InstagramSearchQuery {
    fun hashtag(query: String): String? =
        query
            .trim()
            .removePrefix("#")
            .filter { it.isLetterOrDigit() || it == '_' }
            .lowercase(Locale.ROOT)
            .take(Limits.MAX_HASHTAG_LENGTH)
            .ifEmpty { null }
}

/** Searches Instagram through yt-dlp's hashtag extractor. */
class InstagramMediaSearchProvider
    @Inject
    constructor(
        private val ytDlp: YtDlpEngine,
    ) : MediaSearchProvider {
        override fun supports(platform: MediaPlatform): Boolean = platform == MediaPlatform.INSTAGRAM

        override suspend fun search(
            platform: MediaPlatform,
            query: String,
            limit: Int,
        ): List<MediaSearchResult> {
            val tag = InstagramSearchQuery.hashtag(query) ?: return emptyList()
            val url = "https://www.instagram.com/explore/tags/$tag/"
            return try {
                ytDlp
                    .fetch(url, loadMixAsPlaylist = false)
                    .toSearchResults(platform)
                    .take(limit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InfoException) {
                throw SearchException(e.error, e)
            } catch (e: Exception) {
                AppLog.e(TAG, "search failed", e)
                throw SearchException(ErrorMapper.fromThrowable(e), e)
            }
        }
    }
