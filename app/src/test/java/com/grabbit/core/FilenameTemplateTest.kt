package com.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameTemplateTest {
    // ---- outputTemplate (shared with yt-dlp RequestFactory) ----

    @Test
    fun appendsExtOnce() {
        assertEquals("%(title)s.%(ext)s", FilenameTemplate.outputTemplate("%(title)s"))
        assertEquals("a - b.%(ext)s", FilenameTemplate.outputTemplate("a - b"))
        assertEquals("%(title)s.%(ext)s", FilenameTemplate.outputTemplate("%(title)s.%(ext)s"))
    }

    @Test
    fun customFileNameWins() {
        assertEquals("myfile.%(ext)s", FilenameTemplate.outputTemplate("%(title)s", fileName = "myfile"))
        assertEquals("myfile.%(ext)s", FilenameTemplate.outputTemplate("%(title)s", fileName = "myfile"))
        assertEquals("%(title)s.%(ext)s", FilenameTemplate.outputTemplate("%(title)s", fileName = "  "))
    }

    @Test
    fun slashesCannotCreateSubdirectories() {
        assertEquals("a-b.%(ext)s", FilenameTemplate.outputTemplate("a/b"))
        assertEquals("a-b.%(ext)s", FilenameTemplate.outputTemplate("a\\b"))
    }

    @Test
    fun blankTemplateFallsBackToTitle() {
        assertEquals("%(title)s.%(ext)s", FilenameTemplate.outputTemplate(""))
        assertEquals("%(title)s.%(ext)s", FilenameTemplate.outputTemplate("   "))
    }

    @Test
    fun playlistIndexPrefix() {
        assertEquals("04 - %(title)s.%(ext)s", FilenameTemplate.outputTemplate("%(title)s", playlistIndex = 4))
        assertEquals("04 - x.%(ext)s", FilenameTemplate.outputTemplate("%(title)s", playlistIndex = 4, fileName = "x"))
    }

    // ---- expand (engine-side expansion, shared semantics) ----

    @Test
    fun expandsKnownFieldsAndExt() {
        val values = mapOf("title" to "My Video", "uploader" to "Chan", "id" to "abc")
        assertEquals("My Video.mp4", FilenameTemplate.expand("%(title)s.%(ext)s", values, "mp4"))
        assertEquals("My Video - Chan.mp4", FilenameTemplate.expand("%(title)s - %(uploader)s.%(ext)s", values, "mp4"))
        assertEquals("abc.mp3", FilenameTemplate.expand("%(id)s.%(ext)s", values, "mp3"))
    }

    @Test
    fun unknownFieldsBecomeEmptyAndPercentEscaped() {
        assertEquals("x.mp4", FilenameTemplate.expand("%(nope)sx.%(ext)s", emptyMap(), "mp4"))
        assertEquals("100%.mp4", FilenameTemplate.expand("100%%.%(ext)s", emptyMap(), "mp4"))
    }

    @Test
    fun expandSanitizesAndFallsBack() {
        assertEquals("a_b.mp4", FilenameTemplate.expand("%(title)s.%(ext)s", mapOf("title" to "a/b"), "mp4"))
        assertEquals(FileName.FALLBACK_NAME, FilenameTemplate.expand("%(title)s", mapOf("title" to ""), "mp4"))
    }

    @Test
    fun sharedWithYtDlpTemplateThenExpand() {
        // outputTemplate builds the -o pattern; expand resolves it (engine-side).
        val template = FilenameTemplate.outputTemplate("%(title)s - %(uploader)s", playlistIndex = 7)
        val out = FilenameTemplate.expand(template, mapOf("title" to "T", "uploader" to "U"), "mkv")
        assertEquals("07 - T - U.mkv", out)
    }
}
