package com.grabbit.data.search

import java.net.URLEncoder

/**
 * Fetches result pages from Brave's HTML search.
 *
 * DuckDuckGo answers HTML clients with an anomaly challenge, so Brave is the only
 * engine that has reliably served result pages to the app.
 */
internal object WebSearch {
    private const val BRAVE = "https://search.brave.com/search?q="

    suspend fun html(query: String): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return SearchHttp.get(BRAVE + encoded)
    }
}
