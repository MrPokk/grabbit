package com.grabbit.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchMemoryCacheTest {
    private val media = sampleMedia("first")

    @Test
    fun returnsEntryWithinTtl() {
        val cache = FetchMemoryCache(maxSize = 2, ttlMs = 1_000L, clock = { 0L })
        cache.put("a", media)
        assertEquals(media, cache.get("a"))
    }

    @Test
    fun expiresEntryAfterTtl() {
        var now = 0L
        val cache = FetchMemoryCache(maxSize = 2, ttlMs = 1_000L, clock = { now })
        cache.put("a", media)
        now = 1_001L
        assertNull(cache.get("a"))
    }

    @Test
    fun evictsLeastRecentlyUsedEntry() {
        val cache = FetchMemoryCache(maxSize = 2, ttlMs = 10_000L, clock = { 0L })
        val first = sampleMedia("first")
        val second = sampleMedia("second")
        val third = sampleMedia("third")
        cache.put("a", first)
        cache.put("b", second)
        // Touch "a" so "b" becomes the least recently used entry.
        assertEquals(first, cache.get("a"))
        cache.put("c", third)
        assertNull(cache.get("b"))
        assertEquals(first, cache.get("a"))
        assertEquals(third, cache.get("c"))
    }

    private fun sampleMedia(title: String) =
        FetchedMedia.Single(
            MediaInfo(
                id = title,
                url = "https://youtu.be/$title",
                title = title,
                uploader = null,
                durationSeconds = 0L,
                thumbnail = null,
                site = null,
                formats = emptyList(),
            ),
        )
}
