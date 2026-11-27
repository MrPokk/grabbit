package com.grabbit.data.extract

import com.grabbit.core.AppError
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.ytdlp.DownloadCancelledException
import com.grabbit.data.ytdlp.DownloadException
import com.grabbit.data.ytdlp.DownloadSpec
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.FormatInfo
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.MediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class EngineRouterTest {
    // ---- routing (which engine for which URL) ----

    @Test
    fun youtubeUrlsRouteToNewPipeInAuto() {
        val auto = ExtractionEnginePreference.AUTO
        for (url in YOUTUBE_URLS) {
            assertEquals(url, EngineKind.NEWPIPE, EngineRouting.kindFor(url, auto))
        }
    }

    @Test
    fun nonYoutubeUrlsAlwaysRouteToYtDlp() {
        val auto = ExtractionEnginePreference.AUTO
        for (url in NON_YOUTUBE_URLS) {
            assertEquals(url, EngineKind.YTDLP, EngineRouting.kindFor(url, auto))
        }
    }

    @Test
    fun ytdlpOnlyIgnoresYouTube() {
        val only = ExtractionEnginePreference.YTDLP_ONLY
        for (url in YOUTUBE_URLS + NON_YOUTUBE_URLS) {
            assertEquals(url, EngineKind.YTDLP, EngineRouting.kindFor(url, only))
        }
    }

    @Test
    fun trackingParamsDoNotChangeRouting() {
        assertEquals(
            EngineKind.NEWPIPE,
            EngineRouting.kindFor(
                "https://www.youtube.com/watch?v=abc&utm_source=x&t=10",
                ExtractionEnginePreference.AUTO,
            ),
        )
    }

    // ---- fetch fallback ----

    @Test
    fun newPipeSuccessIsReturned() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP)
            val newPipe = FakeEngine(EngineKind.NEWPIPE, fetchResult = usableSingle())
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            val media = router.fetch("https://www.youtube.com/watch?v=abc", loadMixAsPlaylist = false)

            assertTrue(media is FetchedMedia.Single)
            assertEquals(1, newPipe.fetchCalls)
            assertEquals(0, ytDlp.fetchCalls)
        }

    @Test
    fun newPipeThrowFallsBackToYtDlpOnce() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP, fetchResult = usableSingle())
            val newPipe = FakeEngine(EngineKind.NEWPIPE, fetchThrows = InfoException(AppError.unknown("boom")))
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            val media = router.fetch("https://www.youtube.com/watch?v=abc", loadMixAsPlaylist = false)

            assertTrue(media is FetchedMedia.Single)
            assertEquals(1, newPipe.fetchCalls)
            assertEquals(1, ytDlp.fetchCalls)
        }

    @Test
    fun newPipeNoUsableFormatsFallsBackToYtDlp() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP, fetchResult = usableSingle())
            val newPipe = FakeEngine(EngineKind.NEWPIPE, fetchResult = emptySingle())
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            router.fetch("https://www.youtube.com/watch?v=abc", loadMixAsPlaylist = false)

            assertEquals(1, ytDlp.fetchCalls)
        }

    @Test
    fun cancellationIsNotTurnedIntoFallback() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP, fetchResult = usableSingle())
            val newPipe = FakeEngine(EngineKind.NEWPIPE, fetchThrows = CancellationException("cancelled"))
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            try {
                router.fetch("https://www.youtube.com/watch?v=abc", loadMixAsPlaylist = false)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                // expected
            }
            assertEquals(0, ytDlp.fetchCalls)
        }

    @Test
    fun nonYoutubeDoesNotTouchNewPipe() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP, fetchResult = usableSingle())
            val newPipe = FakeEngine(EngineKind.NEWPIPE, fetchResult = usableSingle())
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            router.fetch("https://www.tiktok.com/@u/video/1", loadMixAsPlaylist = false)

            assertEquals(0, newPipe.fetchCalls)
            assertEquals(1, ytDlp.fetchCalls)
        }

    // ---- download fallback ----

    @Test
    fun downloadFallsBackToYtDlpOnEngineFailure() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP)
            val newPipe =
                FakeEngine(EngineKind.NEWPIPE, downloadThrows = DownloadException(AppError.unknown("HTTP 403")))
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            router.download(spec("https://www.youtube.com/watch?v=abc"), { _, _, _ -> })

            assertEquals(1, newPipe.downloadCalls)
            assertEquals(1, ytDlp.downloadCalls)
        }

    @Test
    fun downloadCancellationIsNotFallback() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP)
            val newPipe = FakeEngine(EngineKind.NEWPIPE, downloadThrows = DownloadCancelledException())
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            try {
                router.download(spec("https://www.youtube.com/watch?v=abc"), { _, _, _ -> })
                fail("expected DownloadCancelledException")
            } catch (e: DownloadCancelledException) {
                // expected
            }
            assertEquals(0, ytDlp.downloadCalls)
        }

    @Test
    fun ytdlpOnlySkipsNewPipeDownload() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP)
            val newPipe = FakeEngine(EngineKind.NEWPIPE)
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.YTDLP_ONLY)

            router.download(spec("https://www.youtube.com/watch?v=abc"), { _, _, _ -> })

            assertEquals(1, ytDlp.downloadCalls)
            assertEquals(0, newPipe.downloadCalls)
        }

    @Test
    fun nonYoutubeDownloadNeverUsesNewPipe() =
        runBlocking {
            val ytDlp = FakeEngine(EngineKind.YTDLP)
            val newPipe = FakeEngine(EngineKind.NEWPIPE)
            val router = router(ytDlp, newPipe, ExtractionEnginePreference.AUTO)

            router.download(spec("https://vk.com/video1"), { _, _, _ -> })

            assertEquals(1, ytDlp.downloadCalls)
            assertEquals(0, newPipe.downloadCalls)
        }

    // ---- helpers ----

    private fun router(
        ytDlp: ExtractionEngine,
        newPipe: ExtractionEngine,
        preference: ExtractionEnginePreference,
    ) = EngineRouter(ytDlp, newPipe, { preference })

    private fun spec(url: String) =
        DownloadSpec(
            taskId = "t",
            url = url,
            playlistIndex = null,
            options = DownloadOptions(),
            template = "%(title)s",
            workDir = File("/tmp"),
            cookiesPath = null,
            proxy = null,
            markerFile = File("/tmp/m"),
        )

    private fun usableSingle() =
        FetchedMedia.Single(
            MediaInfo(
                id = "id",
                url = "https://www.youtube.com/watch?v=abc",
                title = "t",
                uploader = null,
                durationSeconds = 1,
                thumbnail = null,
                site = "YouTube",
                formats = listOf(format()),
            ),
        )

    private fun emptySingle() = usableSingle().let { it.copy(info = it.info.copy(formats = emptyList())) }

    private fun format() =
        FormatInfo(
            id = "f",
            ext = "mp4",
            height = 720,
            fps = 30,
            vcodec = "avc1",
            acodec = "mp4a",
            abr = 0.0,
            tbr = 0.0,
            fileSize = 0L,
            note = null,
        )

    private class FakeEngine(
        override val kind: EngineKind,
        var fetchResult: FetchedMedia = FetchedMedia.Collection("t", null, null, "u", emptyList(), false),
        var fetchThrows: Exception? = null,
        var downloadThrows: Exception? = null,
    ) : ExtractionEngine {
        var fetchCalls = 0
        var downloadCalls = 0

        override suspend fun fetch(
            url: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            bypassCache: Boolean,
            trace: com.grabbit.core.FetchTrace?,
        ): FetchedMedia {
            fetchCalls++
            fetchThrows?.let { throw it }
            return fetchResult
        }

        override fun cancelFetch(processId: String) = Unit

        override suspend fun download(
            spec: DownloadSpec,
            onProgress: (Float, Long, String) -> Unit,
        ): File {
            downloadCalls++
            downloadThrows?.let { throw it }
            return File("/tmp/out")
        }

        override fun cancelDownload(taskId: String) = Unit
    }

    private companion object {
        val YOUTUBE_URLS =
            listOf(
                "https://www.youtube.com/watch?v=abc",
                "https://youtu.be/abc",
                "https://music.youtube.com/watch?v=abc",
                "https://www.youtube.com/playlist?list=PLabc",
                "https://www.youtube.com/shorts/abc",
                "https://www.youtube.com/watch?v=abc&list=RDabc&start_radio=1",
            )
        val NON_YOUTUBE_URLS =
            listOf(
                "https://www.tiktok.com/@u/video/1",
                "https://www.instagram.com/reel/abc",
                "https://vk.com/video-1_2",
                "https://example.com/video",
                "https://youtube.com.evil.com/watch?v=abc",
            )
    }
}

@Suppress("unused")
private fun assertFalseUnused(value: Boolean) = assertFalse(value)
