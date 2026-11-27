package com.grabbit.data.search

import java.net.URLEncoder

internal object WebSearch {
    private const val BRAVE = "https://search.brave.com/search?q="

    suspend fun html(query: String): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return SearchHttp.get(BRAVE + encoded)
    }
}
