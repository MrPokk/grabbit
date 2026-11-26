package com.grabbit.data.search

import com.grabbit.core.AppError
import com.grabbit.core.Limits
import com.grabbit.core.MediaPlatform

/** One item returned by a platform search; [url] is fed back into the normal metadata pipeline. */
data class MediaSearchResult(
    val platform: MediaPlatform,
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val durationSeconds: Long,
    val uploader: String?,
)

/** Search failed; [error] is safe to show in the UI. */
class SearchException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.technical ?: "search failed", cause)

/** Platform-aware search abstraction; implementations may support only a subset of [MediaPlatform]. */
interface MediaSearchProvider {
    /** Whether [platform] can currently be searched by this provider. */
    fun supports(platform: MediaPlatform): Boolean

    /** Returns up to [limit] results for [query] on [platform]. */
    suspend fun search(
        platform: MediaPlatform,
        query: String,
        limit: Int = Limits.MAX_SEARCH_RESULTS,
    ): List<MediaSearchResult>
}
