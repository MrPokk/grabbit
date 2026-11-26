package com.grabbit.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramSearchQueryTest {
    @Test
    fun `normalizes spaces and the hash prefix into one tag`() {
        assertEquals("funnycats", InstagramSearchQuery.hashtag("  #Funny Cats "))
    }

    @Test
    fun `keeps underscores and drops punctuation`() {
        assertEquals("cat_lovers", InstagramSearchQuery.hashtag("cat_lovers!!"))
    }

    @Test
    fun `returns null for an empty query`() {
        assertNull(InstagramSearchQuery.hashtag("  # !! "))
    }
}
