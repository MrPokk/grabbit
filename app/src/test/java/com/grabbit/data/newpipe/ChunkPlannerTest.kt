package com.grabbit.data.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkPlannerTest {
    @Test
    fun emptyForNonPositiveInputs() {
        assertTrue(ChunkPlanner.plan(0, 1024).isEmpty())
        assertTrue(ChunkPlanner.plan(-5, 1024).isEmpty())
        assertTrue(ChunkPlanner.plan(1024, 0).isEmpty())
        assertTrue(ChunkPlanner.plan(1024, -1).isEmpty())
    }

    @Test
    fun exactMultipleChunks() {
        val ranges = ChunkPlanner.plan(totalSizeBytes = 4096, chunkSizeBytes = 1024)
        assertEquals(4, ranges.size)
        assertEquals(ByteRange(0, 0, 1023), ranges[0])
        assertEquals(ByteRange(1, 1024, 2047), ranges[1])
        assertEquals(ByteRange(3, 3072, 4095), ranges[3])
        assertEquals(4096, ranges.sumOf { it.length })
    }

    @Test
    fun lastChunkIsShort() {
        val ranges = ChunkPlanner.plan(totalSizeBytes = 2500, chunkSizeBytes = 1024)
        assertEquals(3, ranges.size)
        assertEquals(ByteRange(2, 2048, 2499), ranges[2])
        assertEquals(452, ranges.last().length)
        assertEquals(2500, ranges.sumOf { it.length })
    }

    @Test
    fun smallerThanChunkIsSingleRange() {
        val ranges = ChunkPlanner.plan(totalSizeBytes = 10, chunkSizeBytes = 1024)
        assertEquals(listOf(ByteRange(0, 0, 9)), ranges)
    }

    @Test
    fun contiguousNoGapsNoOverlaps() {
        val ranges = ChunkPlanner.plan(totalSizeBytes = 10_000_000, chunkSizeBytes = 4L * 1024L * 1024L)
        var expectedStart = 0L
        for (r in ranges) {
            assertEquals(expectedStart, r.startInclusive)
            assertEquals(r.startInclusive + r.length - 1, r.endInclusive)
            expectedStart = r.endInclusive + 1
        }
        assertEquals(10_000_000, expectedStart)
    }

    // ---- resume ----

    @Test
    fun remainingSkipsCompletedIndices() {
        val planned = ChunkPlanner.plan(4096, 1024)
        val remaining = ChunkPlanner.remaining(planned, completedIndices = setOf(0, 1, 3))
        assertEquals(listOf(ByteRange(2, 2048, 3071)), remaining)
    }

    @Test
    fun remainingWithNothingCompletedIsAll() {
        val planned = ChunkPlanner.plan(4096, 1024)
        assertEquals(planned, ChunkPlanner.remaining(planned, emptySet()))
    }

    @Test
    fun remainingWithAllCompletedIsEmpty() {
        val planned = ChunkPlanner.plan(4096, 1024)
        assertTrue(ChunkPlanner.remaining(planned, planned.map { it.index }.toSet()).isEmpty())
    }
}
