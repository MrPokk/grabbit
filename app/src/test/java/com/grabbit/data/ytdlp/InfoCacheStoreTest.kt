package com.grabbit.data.ytdlp

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class InfoCacheStoreTest {
    private lateinit var root: File
    private var now = 1_000_000L

    @Before
    fun setUp() {
        root = Files.createTempDirectory("info-cache").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun store(
        ttlMs: Long = 1_000L,
        retentionMs: Long = 10_000L,
    ) = InfoCacheStore(root = root, ttlMs = ttlMs, retentionMs = retentionMs, clock = { now })

    @Test
    fun savedFileIsFreshAndReadable() {
        val saved = store().save("https://youtu.be/abc", """{"id":"abc"}""")
        assertNotNull(saved)
        val file = File(saved!!.path)
        assertTrue(file.exists())
        assertEquals("""{"id":"abc"}""", file.readText())
        assertEquals(now, saved.fetchedAtMs)
        assertTrue(store().isFresh(saved.path, saved.fetchedAtMs))
    }

    @Test
    fun entryExpiresAfterTtl() {
        val saved = store(ttlMs = 1_000L).save("https://youtu.be/abc", "{}")!!
        now += 1_001L
        assertFalse(store(ttlMs = 1_000L).isFresh(saved.path, saved.fetchedAtMs))
    }

    @Test
    fun missingFileIsNotFresh() {
        assertFalse(store().isFresh(File(root, "missing.json").absolutePath, now))
        assertFalse(store().isFresh(null, now))
        assertFalse(store().isFresh(File(root, "missing.json").absolutePath, 0L))
    }

    @Test
    fun discardDeletesTheFile() {
        val saved = store().save("https://youtu.be/abc", "{}")!!
        store().discard(saved.path)
        assertFalse(File(saved.path).exists())
    }

    @Test
    fun oldFilesAreEvictedOnSave() {
        val old = store(retentionMs = 10_000L).save("https://youtu.be/old", "{}")!!
        now += 10_001L
        store(retentionMs = 10_000L).save("https://youtu.be/new", "{}")
        assertFalse(File(old.path).exists())
    }

    @Test
    fun sameKeyOverwritesTheSameFile() {
        val first = store().save("https://youtu.be/abc", "{\"v\":1}")!!
        now += 10L
        val second = store().save("https://youtu.be/abc", "{\"v\":2}")!!
        assertEquals(first.path, second.path)
        assertEquals("{\"v\":2}", File(second.path).readText())
    }
}
