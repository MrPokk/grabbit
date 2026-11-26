package com.grabbit.data.search

import com.grabbit.core.AppLog
import com.grabbit.core.Limits
import com.grabbit.core.MediaPlatform
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

private const val TAG = "TikTokSearch"

/**
 * TikTok has no public search API usable without request signing, so the provider
 * resolves video pages through a web search and reads metadata from each page.
 */
class TikTokMediaSearchProvider
    @Inject
    constructor() : MediaSearchProvider {
        override fun supports(platform: MediaPlatform): Boolean = platform == MediaPlatform.TIKTOK

        override suspend fun search(
            platform: MediaPlatform,
            query: String,
            limit: Int,
        ): List<MediaSearchResult> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return emptyList()
            val capped = minOf(limit, Limits.MAX_SEARCH_RESULTS)
            val html = WebSearch.html("site:tiktok.com video $trimmed")
            val results = TikTokSearchParser.parse(html, capped)
            AppLog.d(TAG) { "query=$trimmed html=${html.length} results=${results.size}" }
            return coroutineScope {
                results.map { result -> async { enrich(result) } }.awaitAll()
            }
        }

        private suspend fun enrich(result: MediaSearchResult): MediaSearchResult {
            val meta =
                try {
                    TikTokVideoMetaReader.read(result.url)
                } catch (e: SearchException) {
                    AppLog.d(TAG) { "metadata failed for ${result.url}: ${e.message}" }
                    null
                } ?: return result
            return result.copy(
                title = meta.title?.takeIf { it.isNotBlank() } ?: result.title,
                thumbnail = meta.thumbnail ?: result.thumbnail,
                uploader = meta.uploader ?: result.uploader,
                durationSeconds = meta.durationSeconds ?: result.durationSeconds,
            )
        }
    }
