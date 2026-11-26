package com.grabbit.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TikTokSearchParserTest {
    @Test
    fun `reads metadata from brave result records`() {
        val html =
            record(
                title = "Cats being cats",
                description = "TikTok video from Cat Lady (@catlady): cats",
                thumbnail = "https://imgs.search.brave.com/thumb",
                url = "https://www.tiktok.com/@catlady/video/111",
            )
        val result = TikTokSearchParser.parse(html, limit = 10).single()
        assertEquals("111", result.id)
        assertEquals("Cats being cats", result.title)
        assertEquals("https://www.tiktok.com/@catlady/video/111", result.url)
        assertEquals("catlady", result.uploader)
        assertEquals(0L, result.durationSeconds)
    }

    @Test
    fun `unescapes javascript string escapes`() {
        val html =
            record(
                title = "A \\u002F B \\\"quoted\\\"",
                description = "desc",
                thumbnail = "",
                url = "https://www.tiktok.com/@user/video/222",
            )
        val result = TikTokSearchParser.parse(html, limit = 10).single()
        assertEquals("A / B \"quoted\"", result.title)
        assertNull(result.thumbnail)
    }

    @Test
    fun `strips the page suffix and deduplicates`() {
        val html =
            record("Cat Videos | TikTok", "desc", "thumb", "https://www.tiktok.com/@a/video/333") +
                record("Cat Videos | TikTok", "desc", "thumb", "https://www.tiktok.com/@a/video/333")
        val results = TikTokSearchParser.parse(html, limit = 10)
        assertEquals(1, results.size)
        assertEquals("Cat Videos", results.single().title)
    }

    @Test
    fun `keeps video links without a parsed record`() {
        val html = """<a href="https://www.tiktok.com/@plain/video/444">x</a>"""
        val result = TikTokSearchParser.parse(html, limit = 10).single()
        assertEquals("444", result.id)
        assertEquals("plain", result.uploader)
    }

    @Test
    fun `respects the limit`() {
        val html =
            (1..5).joinToString("\n") { id ->
                record("Video $id", "desc", "thumb", "https://www.tiktok.com/@a/video/$id")
            }
        assertEquals(2, TikTokSearchParser.parse(html, limit = 2).size)
    }

    private fun record(
        title: String,
        description: String,
        thumbnail: String,
        url: String,
    ): String =
        """{title:"$title",url:"/go/v1/token",full_title:void 0,""" +
            """description:"$description",page_age:void 0,""" +
            """thumbnail:{src:"$thumbnail",original:"$url"},profile:{name:"TikTok"}}"""
}
