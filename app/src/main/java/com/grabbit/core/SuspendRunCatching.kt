package com.grabbit.core

import kotlinx.coroutines.CancellationException

suspend inline fun <T> suspendRunCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
