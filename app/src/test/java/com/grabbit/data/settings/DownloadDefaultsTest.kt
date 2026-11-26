package com.grabbit.data.settings

import com.grabbit.data.download.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDefaultsTest {
    @Test
    fun `video picks the first available height at or below the default`() {
        val options =
            AppSettings(defaultVideoHeight = 720)
                .toDownloadOptions(MediaType.VIDEO, listOf(1080, 720, 480))

        assertEquals(MediaType.VIDEO, options.mediaType)
        assertEquals(720, options.maxHeight)
    }

    @Test
    fun `video falls back to the highest available height`() {
        val options =
            AppSettings(defaultVideoHeight = 480)
                .toDownloadOptions(MediaType.VIDEO, listOf(2160, 1080))

        assertEquals(2160, options.maxHeight)
    }

    @Test
    fun `audio uses the saved format and bitrate without a height`() {
        val options =
            AppSettings(defaultAudioFormat = "flac", defaultAudioBitrate = 320)
                .toDownloadOptions(MediaType.AUDIO, listOf(1080))

        assertEquals(0, options.maxHeight)
        assertEquals("flac", options.audioFormat)
        assertEquals(320, options.audioBitrate)
    }
}
