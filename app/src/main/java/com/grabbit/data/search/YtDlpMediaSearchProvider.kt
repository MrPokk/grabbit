package com.grabbit.data.search

import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.ErrorMapper
import com.grabbit.core.Limits
import com.grabbit.core.MediaPlatform
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

private const val TAG = "MediaSearch"

/** Platform search URLs understood by yt-dlp. */
object SearchTargets {
    /** yt-dlp's pseudo-URL for a YouTube search. */
    fun youtube(
        query: String,
        limit: Int,
    ): String = "ytsearch${limit.coerceIn(1, Limits.MAX_SEARCH_RESULTS)}:${query.trim()}"
}

/** Searches through yt-dlp's search extractors. Only YouTube exposes a general
 * search extractor; other platforms return a user-facing unsupported error.
 */
class YtDlpMediaSearchProvider
    @Inject
    constructor(
        private val ytDlp: YtDlpEngine,
    ) : MediaSearchProvider {
        override fun supports(platform: MediaPlatform): Boolean = platform == MediaPlatform.YOUTUBE

        override suspend fun search(
            platform: MediaPlatform,
            query: String,
            limit: Int,
        ): List<MediaSearchResult> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return emptyList()
            if (!supports(platform)) {
                throw SearchException(AppError(R.string.error_search_unsupported))
            }
            val target = SearchTargets.youtube(trimmed, limit)
            return try {
                val collection = ytDlp.fetch(target, loadMixAsPlaylist = false) as? FetchedMedia.Collection
                collection
                    ?.items
                    .orEmpty()
                    .map { item ->
                        MediaSearchResult(
                            platform = platform,
                            id = item.id,
                            title = item.title,
                            url = item.url,
                            thumbnail = item.thumbnail ?: collection?.thumbnail,
                            durationSeconds = item.durationSeconds,
                            uploader = collection?.uploader,
                        )
                    }
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
