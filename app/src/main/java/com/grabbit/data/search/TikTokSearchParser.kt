package com.grabbit.data.search

import com.grabbit.core.MediaPlatform

private val RESULT =
    Regex(
        """title:"((?:[^"\\]|\\.)*)",url:"[^"]*",full_title:(?:void 0|"(?:[^"\\]|\\.)*"),""" +
            """description:"((?:[^"\\]|\\.)*)".*?original:"(https://www\.tiktok\.com/@[^"]+/video/\d+)"""",
        RegexOption.DOT_MATCHES_ALL,
    )
private val VIDEO_URL = Regex("""https://www\.tiktok\.com/@[A-Za-z0-9._-]+/video/\d+""")
private val VIDEO_ID = Regex("""/video/(\d+)""")
private val UPLOADER = Regex("""tiktok\.com/@([A-Za-z0-9._-]+)/video/""")
private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

private const val PAGE_SUFFIX = " | TikTok"
private const val NO_DURATION = 0L
private const val HEX_RADIX = 16
private const val GROUP_TITLE = 1
private const val GROUP_DESCRIPTION = 2
private const val GROUP_URL = 3

/**
 * Reads the result records embedded in Brave's HTML.
 *
 * Brave serialises each result as a JavaScript object, so the fields are pulled with a
 * tolerant regex instead of a JSON parser.
 */
internal object TikTokSearchParser {
    fun parse(
        html: String,
        limit: Int,
    ): List<MediaSearchResult> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<MediaSearchResult>()
        for (match in RESULT.findAll(html)) {
            val url = match.groupValues[GROUP_URL]
            if (!seen.add(url)) continue
            val title = clean(match.groupValues[GROUP_TITLE]).removeSuffix(PAGE_SUFFIX)
            results +=
                MediaSearchResult(
                    platform = MediaPlatform.TIKTOK,
                    id = videoId(url),
                    title = title.ifBlank { clean(match.groupValues[GROUP_DESCRIPTION]) }.ifBlank { url },
                    url = url,
                    thumbnail = null,
                    durationSeconds = NO_DURATION,
                    uploader = uploader(url),
                )
            if (results.size == limit) return results
        }
        for (match in VIDEO_URL.findAll(html)) {
            val url = match.value
            if (!seen.add(url)) continue
            results +=
                MediaSearchResult(
                    platform = MediaPlatform.TIKTOK,
                    id = videoId(url),
                    title = url,
                    url = url,
                    thumbnail = null,
                    durationSeconds = NO_DURATION,
                    uploader = uploader(url),
                )
            if (results.size == limit) break
        }
        return results
    }

    private fun videoId(url: String): String = VIDEO_ID.find(url)?.groupValues?.get(1) ?: url

    private fun uploader(url: String): String? = UPLOADER.find(url)?.groupValues?.get(1)

    private fun clean(value: String): String {
        val unescaped =
            UNICODE_ESCAPE.replace(value) { match ->
                match
                    .groupValues[1]
                    .toInt(radix = HEX_RADIX)
                    .toChar()
                    .toString()
            }
        return unescaped
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
            .trim()
    }
}
