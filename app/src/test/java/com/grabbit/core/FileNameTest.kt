package com.grabbit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameTest {

    @Test
    fun `sanitize replaces illegal characters`() {
        assertEquals("my_file", FileName.sanitize("my/file"))
        assertEquals("my_file", FileName.sanitize("my\\file"))
        assertEquals("my_file", FileName.sanitize("my:file"))
        assertEquals("my_file", FileName.sanitize("my*file"))
        assertEquals("my_file", FileName.sanitize("my\"file"))
        assertEquals("my_file", FileName.sanitize("my<file"))
        assertEquals("my_file", FileName.sanitize("my>file"))
        assertEquals("my_file", FileName.sanitize("my|file"))
        assertEquals("my file", FileName.sanitize("my file")) // space is legal
        assertEquals("normal", FileName.sanitize("normal"))
    }

    @Test
    fun `sanitize trims and falls back for blank names`() {
        assertEquals("download", FileName.sanitize(""))
        assertEquals("download", FileName.sanitize("   "))
        assertEquals("_________", FileName.sanitize("/\\:*?\"<>|")) // all illegal -> underscores
        assertEquals("___", FileName.sanitize("/\\*")) // trimmed only, illegal replaced
    }

    @Test
    fun `unique returns the candidate when it is free`() {
        assertEquals("video.mp4", FileName.unique("video.mp4", setOf("other.mp4")))
        assertEquals("video", FileName.unique("video", setOf("other")))
    }

    @Test
    fun `unique appends suffix before the extension`() {
        assertEquals("video (1).mp4", FileName.unique("video.mp4", setOf("video.mp4")))
        assertEquals("video (2).mp4", FileName.unique("video.mp4", setOf("video.mp4", "video (1).mp4")))
    }

    @Test
    fun `unique handles names without extension`() {
        assertEquals("video (1)", FileName.unique("video", setOf("video")))
        assertEquals("video (2)", FileName.unique("video", setOf("video", "video (1)")))
    }

    @Test
    fun `unique skips over already-taken suffixed names`() {
        assertEquals(
            "video (3).mp4",
            FileName.unique(
                "video.mp4",
                setOf("video.mp4", "video (1).mp4", "video (2).mp4"),
            ),
        )
    }

    @Test
    fun `unique keeps the extension of the original candidate`() {
        assertEquals("archive (1).tar.gz", FileName.unique("archive.tar.gz", setOf("archive.tar.gz")))
    }
}
