package com.grabbit.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTargetsTest {
    @Test
    fun `youtube prefix carries the query and limit`() {
        assertEquals("ytsearch10:cats", SearchTargets.youtube("cats", 10))
    }

    @Test
    fun `youtube trims the query`() {
        assertEquals("ytsearch5:funny cats", SearchTargets.youtube("  funny cats  ", 5))
    }

    @Test
    fun `youtube clamps the limit`() {
        assertEquals("ytsearch1:cats", SearchTargets.youtube("cats", 0))
    }
}
