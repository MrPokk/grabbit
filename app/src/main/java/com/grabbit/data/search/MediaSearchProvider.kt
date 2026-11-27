package com.grabbit.data.search

import com.grabbit.core.AppError
import com.grabbit.core.Limits
import com.grabbit.core.MediaPlatform

data class MediaSearchResult(
    val platform: MediaPlatform,
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val durationSeconds: Long,
    val uploader: String?,
)

class SearchException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.technical ?: "search failed", cause)

interface MediaSearchProvider {
    fun supports(platform: MediaPlatform): Boolean

    suspend fun search(
        platform: MediaPlatform,
        query: String,
        limit: Int = Limits.MAX_SEARCH_RESULTS,
    ): List<MediaSearchResult>
}
