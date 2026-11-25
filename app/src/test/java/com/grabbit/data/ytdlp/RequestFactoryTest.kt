package com.grabbit.data.ytdlp

import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RequestFactoryTest {

    private fun spec(options: DownloadOptions, index: Int? = null) = DownloadSpec(
        taskId = "task",
        url = "https://youtu.be/abc",
        playlistIndex = index,
        options = options,
        template = "%(title)s",
        workDir = File("/tmp/work"),
        cookiesPath = null,
        proxy = null,
        markerFile = File("/tmp/work/out.path"),
    )

    @Test
    fun mp4SelectorPrefersMp4StreamsAndHeightLimit() {
        val selector = RequestFactory.formatSelector(
            DownloadOptions(mediaType = MediaType.VIDEO, maxHeight = 720, container = "mp4"),
        )
        assertTrue(selector.contains("height<=720"))
        assertTrue(selector.contains("[ext=mp4]"))
        assertTrue(selector.contains("ba[ext=m4a]"))
    }

    @Test
    fun audioSelectorFallsBackToBest() {
        val selector = RequestFactory.formatSelector(
            DownloadOptions(mediaType = MediaType.AUDIO, audioFormat = "mp3"),
        )
        assertEquals("ba/b", selector)
    }

    @Test
    fun templateGetsExtensionOnce() {
        assertEquals("%(title)s.%(ext)s", RequestFactory.outputTemplate("%(title)s"))
        assertEquals(
            "%(title)s - %(uploader)s.%(ext)s",
            RequestFactory.outputTemplate("%(title)s - %(uploader)s"),
        )
        assertEquals("%(title)s.%(ext)s", RequestFactory.outputTemplate("%(title)s.%(ext)s"))
    }

    @Test
    fun templateCannotCreateSubdirectories() {
        assertEquals("a-b.%(ext)s", RequestFactory.outputTemplate("a/b"))
    }

    @Test
    fun commandContainsAudioConversionFlags() {
        val request = RequestFactory.build(
            spec(DownloadOptions(mediaType = MediaType.AUDIO, audioFormat = "mp3", audioBitrate = 320)),
        )
        val command = request.buildCommand()
        assertTrue(command.contains("-x"))
        assertTrue(command.contains("mp3"))
        // 320 maps to yt-dlp's "best" quality, matching the working manual command.
        assertTrue(command.contains("0"))
        assertEquals(false, command.contains("320K"))
        assertTrue(command.contains("--no-playlist"))
        assertTrue(command.contains("--print-to-file"))
        assertTrue(command.contains("--embed-thumbnail"))
        assertTrue(command.contains("--convert-thumbnails"))
        assertTrue(command.contains("jpg"))

        val lower = RequestFactory.build(
            spec(DownloadOptions(mediaType = MediaType.AUDIO, audioFormat = "mp3", audioBitrate = 192)),
        ).buildCommand()
        assertTrue(lower.contains("192K"))
    }

    @Test
    fun playlistItemUsesItsOwnUrlAndNumberedName() {
        val request = RequestFactory.build(spec(DownloadOptions(), index = 4))
        val command = request.buildCommand()
        assertTrue(command.contains("--no-playlist"))
        assertEquals(false, command.contains("--playlist-items"))
        assertTrue(command.any { it.contains("04 - ") })

        val marker = spec(DownloadOptions(), index = 4).markerFile.absolutePath
        val printToFile = listOf("--print-to-file", "after_move:filepath", marker)
        assertTrue("command=$command", command.windowed(3).any { it == printToFile })
        assertEquals("marker must not be a stray URL argument", 1, command.count { it == marker })
    }

    @Test
    fun fetchRequestKeepsRadioDefaultSingle() {
        val command = RequestFactory.buildFetchRequest(
            "https://www.youtube.com/watch?v=abc&list=RDabc&start_radio=1",
            false,
        ).buildCommand()
        assertTrue(command.contains("--no-playlist"))
        assertTrue(command.contains("--playlist-end"))

        val asMix = RequestFactory.buildFetchRequest(
            "https://www.youtube.com/watch?v=abc&list=RDabc&start_radio=1",
            true,
        ).buildCommand()
        assertEquals(false, asMix.contains("--no-playlist"))
        assertTrue(asMix.any { it.contains("50") })
    }
}
