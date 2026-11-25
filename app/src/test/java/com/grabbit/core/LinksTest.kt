package com.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {

    @Test
    fun extractsPlainUrl() {
        assertEquals("https://youtu.be/abc123", Links.extract("https://youtu.be/abc123"))
    }

    @Test
    fun extractsUrlFromShareText() {
        val shared = "Смотри что нашёл: https://www.tiktok.com/@user/video/12345?is_from_webapp=1 — топ!"
        assertEquals(
            "https://www.tiktok.com/@user/video/12345?is_from_webapp=1",
            Links.extract(shared),
        )
    }

    @Test
    fun stripsTrailingPunctuation() {
        assertEquals("https://example.com/video", Links.extract("(https://example.com/video)."))
    }

    @Test
    fun rejectsTextWithoutUrl() {
        assertNull(Links.extract("no link here"))
        assertNull(Links.extract(null))
    }

    @Test
    fun validatesHosts() {
        assertTrue(Links.looksLikeUrl("https://youtube.com/watch?v=1"))
        assertFalse(Links.looksLikeUrl("https://localhost"))
        assertFalse(Links.looksLikeUrl("ftp://example.com"))
    }
}
