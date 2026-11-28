package com.grabbit.data.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSelectionTest {
    private fun video(
        height: Int,
        container: String? = "mp4",
        codec: String? = "avc1",
        fps: Int = 30,
        bitrateKbps: Int = 1000,
        videoOnly: Boolean = true,
        url: String = "https://v/$height",
        width: Int = 0,
    ) = VideoStreamInfo(url, container, codec, width, height, fps, bitrateKbps, videoOnly)

    private fun audio(
        bitrateKbps: Int,
        container: String? = "m4a",
        codec: String? = "mp4a",
        url: String = "https://a/$bitrateKbps",
    ) = AudioStreamInfo(url, container, codec, bitrateKbps)

    @Test
    fun picksHighestResolutionWithinHeightCap() {
        val streams = listOf(video(2160), video(1080), video(720), video(480))
        assertEquals(1080, StreamSelection.pickVideo(streams, maxHeight = 1080, preferContainer = null)?.height)
        assertEquals(2160, StreamSelection.pickVideo(streams, maxHeight = 0, preferContainer = null)?.height)
        assertEquals(720, StreamSelection.pickVideo(streams, maxHeight = 720, preferContainer = null)?.height)
    }

    @Test
    fun prefersOutputContainerThenBitrate() {
        val mp4 = video(1080, container = "mp4", bitrateKbps = 1000)
        val webm = video(1080, container = "webm", codec = "vp9", bitrateKbps = 5000)
        assertEquals(
            "mp4",
            StreamSelection.pickVideo(listOf(mp4, webm), maxHeight = 0, preferContainer = "mp4")?.container,
        )
        assertEquals(
            "webm",
            StreamSelection.pickVideo(listOf(mp4, webm), maxHeight = 0, preferContainer = "webm")?.container,
        )
    }

    @Test
    fun prefersVideoOnlyForMerging() {
        val combined = video(1080, videoOnly = false)
        val only = video(1080, videoOnly = true)
        val picked = StreamSelection.pickVideo(listOf(combined, only), maxHeight = 0, preferContainer = null)
        assertTrue(requireNotNull(picked).videoOnly)
    }

    @Test
    fun noVideoReturnsNull() {
        assertNull(StreamSelection.pickVideo(emptyList(), maxHeight = 0, preferContainer = null))
        assertNull(StreamSelection.pickVideo(listOf(video(0)), maxHeight = 0, preferContainer = null))
    }

    @Test
    fun blankUrlsAreIgnored() {
        assertNull(StreamSelection.pickVideo(listOf(video(1080, url = "")), maxHeight = 0, preferContainer = null))
        assertNull(StreamSelection.pickAudio(listOf(audio(128, url = "")), preferContainer = null))
    }

    @Test
    fun picksBestAudioByContainerThenBitrate() {
        val m4a = audio(128, container = "m4a")
        val opus = audio(160, container = "webm", codec = "opus")
        assertEquals("m4a", StreamSelection.pickAudio(listOf(m4a, opus), preferContainer = "mp4")?.container)
        assertEquals("webm", StreamSelection.pickAudio(listOf(m4a, opus), preferContainer = "webm")?.container)
        assertEquals(160, StreamSelection.pickAudio(listOf(m4a, opus), preferContainer = null)?.bitrateKbps)
    }

    @Test
    fun combinedDetection() {
        assertTrue(StreamSelection.isCombined(video(1080, videoOnly = false)))
        assertFalse(StreamSelection.isCombined(video(1080, videoOnly = true)))
    }

    @Test
    fun outputExtMapsContainer() {
        assertEquals("mp4", StreamSelection.outputExt("mp4"))
        assertEquals("mkv", StreamSelection.outputExt("mkv"))
        assertEquals("webm", StreamSelection.outputExt("webm"))
        assertEquals("mp4", StreamSelection.outputExt(""))
    }
}
