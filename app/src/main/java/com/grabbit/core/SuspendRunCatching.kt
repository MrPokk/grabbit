package com.grabbit.core

import kotlinx.coroutines.CancellationException

/**
 * Result wrapper for suspend code. Unlike [runCatching] it lets cancellation
 * through instead of turning it into a failed result.
 */
suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
