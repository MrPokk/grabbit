package com.grabbit.data.newpipe

import com.grabbit.core.Net
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

/** Named timeouts for the NewPipeExtractor HTTP layer. */
const val NEWPIPE_CONNECT_TIMEOUT_SECONDS = 20L
const val NEWPIPE_READ_TIMEOUT_SECONDS = 30L
const val NEWPIPE_WRITE_TIMEOUT_SECONDS = 30L

private const val USER_AGENT_HEADER = "User-Agent"

/** Build an OkHttp client with sensible timeouts for extraction + downloading. */
fun newPipeOkHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(NEWPIPE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NEWPIPE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NEWPIPE_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

/** Required NewPipeExtractor [Downloader] backed by OkHttp, with a proper user agent + timeouts. */
class OkHttpDownloader(
    private val client: OkHttpClient,
    private val userAgent: String = Net.USER_AGENT,
) : Downloader() {
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (request.headers().keys.none { it.equals(USER_AGENT_HEADER, ignoreCase = true) }) {
            builder.header(USER_AGENT_HEADER, userAgent)
        }

        val body: RequestBody? = request.dataToSend()?.toRequestBody(null as MediaType?)
        val method = request.httpMethod().uppercase()
        when (method) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> builder.post(body ?: EMPTY_BODY)
            "PUT" -> builder.put(body ?: EMPTY_BODY)
            "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
            else -> builder.method(request.httpMethod(), body)
        }

        client.newCall(builder.build()).execute().use { response ->
            val headers = LinkedHashMap<String, MutableList<String>>()
            response.headers.forEach { (name, value) ->
                headers.getOrPut(name) { mutableListOf() }.add(value)
            }
            val responseBody = response.body?.string().orEmpty()
            return Response(
                response.code,
                response.message,
                headers,
                responseBody,
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}
