package com.grabbit.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoParserTest {
    @Test
    fun parsesSingleVideo() {
        val json =
            """
            {
              "id": "abc",
              "title": "Test video",
              "uploader": "Channel",
              "duration": 123.4,
              "thumbnail": "https://img.example/t.jpg",
              "extractor_key": "Youtube",
              "formats": [
                {"format_id": "137", "ext": "mp4", "height": 1080, "fps": 30,
                 "vcodec": "avc1.640028", "acodec": "none", "filesize": 123456},
                {"format_id": "140", "ext": "m4a", "vcodec": "none",
                 "acodec": "mp4a.40.2", "abr": 128.0}
              ]
            }
            """.trimIndent()

        val media = InfoParser.parse(json, "https://youtu.be/abc") as FetchedMedia.Single
        assertEquals("Test video", media.title)
        assertEquals(123L, media.info.durationSeconds)
        assertEquals(listOf(1080), media.info.videoHeights)
        assertEquals(2, media.info.formats.size)
        assertTrue(media.info.formats[0].isVideo)
        assertTrue(media.info.formats[1].isAudio)
    }

    @Test
    fun parsesPlaylistAndMarksItTruncated() {
        val json =
            """
            {
              "_type": "playlist",
              "title": "My playlist",
              "uploader": "Uploader",
              "playlist_count": 3,
              "entries": [
                {"id": "a", "title": "First", "url": "https://youtu.be/a", "duration": 10},
                {"id": "b", "title": "Second", "duration": 20, "ie_key": "Youtube"}
              ]
            }
            """.trimIndent()

        val media = InfoParser.parse(json, "https://youtube.com/playlist?list=x") as FetchedMedia.Collection
        assertEquals(2, media.items.size)
        assertTrue(media.truncated)
        assertEquals("https://youtu.be/a", media.items[0].url)
        assertEquals("https://www.youtube.com/watch?v=b", media.items[1].url)
    }

    @Test
    fun skipsStoryboardFormats() {
        val json =
            """
            {
              "id": "x",
              "title": "x",
              "formats": [
                {"format_id": "sb0", "ext": "mhtml", "vcodec": "none", "acodec": "none"},
                {"format_id": "18", "ext": "mp4", "height": 360, "vcodec": "avc1", "acodec": "mp4a"}
              ]
            }
            """.trimIndent()

        val media = InfoParser.parse(json, "https://example.com/x") as FetchedMedia.Single
        assertEquals(1, media.info.formats.size)
    }
}
