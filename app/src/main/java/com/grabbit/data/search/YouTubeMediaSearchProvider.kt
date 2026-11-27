package com.grabbit.data.search

import com.grabbit.core.AppLog
import com.grabbit.core.ErrorMapper
import com.grabbit.core.Limits
import com.grabbit.core.MediaPlatform
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

private const val TAG = "YouTubeSearch"

object SearchTargets {
    fun youtube(
        query: String,
        limit: Int,
    ): String = "ytsearch${limit.coerceIn(1, Limits.MAX_SEARCH_RESULTS)}:${query.trim()}"
}

class YouTubeMediaSearchProvider
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
            val target = SearchTargets.youtube(trimmed, limit)
            return try {
                ytDlp
                    .fetch(
                        target,
                        loadMixAsPlaylist = false,
                        processId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        bypassCache = false,
                        trace = null,
                    ).toSearchResults(platform)
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

internal fun FetchedMedia.toSearchResults(platform: MediaPlatform): List<MediaSearchResult> =
    when (this) {
        is FetchedMedia.Single ->
            listOf(
                MediaSearchResult(
                    platform = platform,
                    id = info.id,
                    title = info.title,
                    url = info.url,
                    thumbnail = info.thumbnail,
                    durationSeconds = info.durationSeconds,
                    uploader = info.uploader,
                ),
            )

        is FetchedMedia.Collection ->
            items.map { item ->
                MediaSearchResult(
                    platform = platform,
                    id = item.id,
                    title = item.title,
                    url = item.url,
                    thumbnail = item.thumbnail ?: thumbnail,
                    durationSeconds = item.durationSeconds,
                    uploader = uploader,
                )
            }
    }
