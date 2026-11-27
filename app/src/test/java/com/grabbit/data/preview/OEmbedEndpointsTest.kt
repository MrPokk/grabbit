package com.grabbit.data.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OEmbedEndpointsTest {
    @Test
    fun youtubeLinksUseTheYouTubeEndpoint() {
        val endpoint = OEmbedEndpoints.forUrl("https://www.youtube.com/watch?v=abc")
        assertTrue(endpoint!!.startsWith("https://www.youtube.com/oembed?format=json&url="))
        assertTrue(endpoint.contains("https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dabc"))
    }

    @Test
    fun shortAndMusicLinksAreYouTube() {
        assertTrue(OEmbedEndpoints.forUrl("https://youtu.be/abc")!!.contains("youtube.com/oembed"))
        assertTrue(OEmbedEndpoints.forUrl("https://music.youtube.com/watch?v=abc")!!.contains("youtube.com/oembed"))
    }

    @Test
    fun tiktokLinksUseTheTikTokEndpoint() {
        val endpoint = OEmbedEndpoints.forUrl("https://www.tiktok.com/@user/video/123")
        assertEquals(
            "https://www.tiktok.com/oembed?url=https%3A%2F%2Fwww.tiktok.com%2F%40user%2Fvideo%2F123",
            endpoint,
        )
    }

    @Test
    fun unsupportedHostsHaveNoEndpoint() {
        assertNull(OEmbedEndpoints.forUrl("https://example.com/video"))
        assertNull(OEmbedEndpoints.forUrl("https://notyoutube.com/watch?v=abc"))
        assertNull(OEmbedEndpoints.forUrl(""))
    }
}
