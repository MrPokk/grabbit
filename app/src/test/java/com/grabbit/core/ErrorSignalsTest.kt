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

    @Test
    fun requiresAuthenticationOnlyForExplicitSignals() {
        assertTrue(ErrorSignals.isAuthenticationRequired("This video is private"))
        assertTrue(ErrorSignals.isAuthenticationRequired("Sign in to confirm you're not a bot"))
        assertFalse(ErrorSignals.isAuthenticationRequired("Use --cookies FILE for authentication"))
        assertFalse(ErrorSignals.isAuthenticationRequired(null))
    }

    @Test
    fun detectsTransientDownloadFailures() {
        assertTrue(ErrorSignals.isTransientDownloadFailure("HTTP Error 403: Forbidden"))
        assertTrue(ErrorSignals.isTransientDownloadFailure("HTTP Error 429: Too Many Requests"))
        assertTrue(ErrorSignals.isTransientDownloadFailure("Connection reset by peer"))
        assertFalse(ErrorSignals.isTransientDownloadFailure("This video is private"))
        assertFalse(ErrorSignals.isTransientDownloadFailure(null))
    }
}
