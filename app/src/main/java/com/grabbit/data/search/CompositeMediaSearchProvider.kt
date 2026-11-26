package com.grabbit.data.search

import com.grabbit.core.MediaPlatform
import javax.inject.Inject

/** Routes a search to the provider that owns the selected platform. */
class CompositeMediaSearchProvider
    @Inject
    constructor(
        youtube: YouTubeMediaSearchProvider,
        tiktok: TikTokMediaSearchProvider,
        instagram: InstagramMediaSearchProvider,
    ) : MediaSearchProvider {
        private val providers: List<MediaSearchProvider> = listOf(youtube, tiktok, instagram)

        override fun supports(platform: MediaPlatform): Boolean = providers.any { it.supports(platform) }

        override suspend fun search(
            platform: MediaPlatform,
            query: String,
            limit: Int,
        ): List<MediaSearchResult> {
            val provider = providers.firstOrNull { it.supports(platform) } ?: return emptyList()
            return provider.search(platform, query, limit)
        }
    }
