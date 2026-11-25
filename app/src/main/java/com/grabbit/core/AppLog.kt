package com.grabbit.core

import android.util.Log
import com.grabbit.BuildConfig

/** The only place that reads [BuildConfig.DEBUG] or calls [Log] directly. */
object AppLog {
    /** Logs a debug message; [message] is evaluated only in debug builds. */
    inline fun d(
        tag: String,
        message: () -> String,
    ) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    /** Logs an error that is visible in release builds too. */
    fun e(
        tag: String,
        message: String,
        t: Throwable? = null,
    ) {
        Log.e(tag, message, t)
    }
}
