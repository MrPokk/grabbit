package com.grabbit.data.search

import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.ErrorMapper
import com.grabbit.core.Net
import com.grabbit.core.Timing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

private const val TAG = "SearchHttp"
private const val HTTP_OK = 200
private const val HTTP_MAX_SUCCESS = 299

internal object SearchHttp {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection()
            check(connection is HttpURLConnection) { "Unexpected connection type: ${connection::class.simpleName}" }
            connection.apply {
                connectTimeout = Timing.SEARCH_HTTP_TIMEOUT_MS
                readTimeout = Timing.SEARCH_HTTP_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", Net.USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                coroutineContext.ensureActive()
                val code = connection.responseCode
                if (code !in HTTP_OK..HTTP_MAX_SUCCESS) {
                    throw SearchException(AppError(R.string.error_search_failed, "HTTP $code"))
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                AppLog.d(TAG) { "GET $url -> $code (${body.length} chars)" }
                body
            } catch (e: SearchException) {
                throw e
            } catch (e: IOException) {
                AppLog.e(TAG, "search request failed", e)
                throw SearchException(ErrorMapper.fromYtDlp(e.message), e)
            } finally {
                connection.disconnect()
            }
        }
}
