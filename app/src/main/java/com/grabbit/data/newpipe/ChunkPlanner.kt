package com.grabbit.data.newpipe

/** A single inclusive byte range to download. */
data class ByteRange(
    val index: Int,
    val startInclusive: Long,
    val endInclusive: Long,
) {
    val length: Long get() = endInclusive - startInclusive + 1
}

/** Pure chunking / resume math for [ChunkedDownloader]; no I/O. */
object ChunkPlanner {
    /**
     * Split [totalSizeBytes] into inclusive ranges of at most [chunkSizeBytes], in order.
     * Returns empty for non-positive size or chunk size.
     */
    fun plan(
        totalSizeBytes: Long,
        chunkSizeBytes: Long,
    ): List<ByteRange> {
        if (totalSizeBytes <= 0L || chunkSizeBytes <= 0L) return emptyList()
        val ranges = ArrayList<ByteRange>()
        var start = 0L
        var index = 0
        while (start < totalSizeBytes) {
            val end = minOf(start + chunkSizeBytes - 1, totalSizeBytes - 1)
            ranges.add(ByteRange(index, start, end))
            start = end + 1
            index++
        }
        return ranges
    }

    /** Ranges not yet in [completedIndices], preserving order. */
    fun remaining(
        planned: List<ByteRange>,
        completedIndices: Set<Int>,
    ): List<ByteRange> = planned.filterNot { it.index in completedIndices }
}
