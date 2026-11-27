package com.grabbit.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorSignalsTest {
    @Test
    fun detectsExpiredFormatUrls() {
        assertTrue(ErrorSignals.isExpiredFormatUrl("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertTrue(ErrorSignals.isExpiredFormatUrl("URL signature expired"))
        assertTrue(ErrorSignals.isExpiredFormatUrl("ERROR: 403 Forbidden"))
    }

    @Test
    fun ignoresUnrelatedFailures() {
        assertFalse(ErrorSignals.isExpiredFormatUrl(null))
        assertFalse(ErrorSignals.isExpiredFormatUrl(""))
        assertFalse(ErrorSignals.isExpiredFormatUrl("Video unavailable"))
        assertFalse(ErrorSignals.isExpiredFormatUrl("Requested format is not available"))
    }
}
