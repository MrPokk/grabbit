package com.grabbit.core

/**
 * Link handling helpers. The app accepts a raw URL, a share-sheet text blob
 * ("Look at this video https://…") or text with punctuation around the link.
 */
object Links {

    private val urlRegex = Regex("""https?://[^\s<>"']+""")

    private val trailing = charArrayOf('.', ',', ')', ']', '}', '"', '\'', '»', '…', '!', '?', ';', ':')

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val raw = urlRegex.find(text)?.value ?: return null
        val trimmed = raw.trimEnd(*trailing)
        return if (looksLikeUrl(trimmed)) trimmed else null
    }

    fun looksLikeUrl(value: String): Boolean {
        val v = value.trim()
        if (!v.startsWith("http://") && !v.startsWith("https://")) return false
        val host = v.substringAfter("://").substringBefore('/')
        return host.contains('.') && host.length > 3
    }

    /**
     * True for YouTube "Mix" / radio URLs (`list=RD…`, `start_radio=1`).
     * These are dynamic, endless playlists that yt-dlp treats as playlists
     * by default.
     */
    fun isRadioPlaylist(url: String): Boolean {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isBlank()) return false
        return query.split('&').any { param ->
            val pair = param.split('=', limit = 2)
            val name = pair[0].lowercase()
            val value = pair.getOrNull(1) ?: ""
            when (name) {
                "list" -> value.startsWith("rd", ignoreCase = true)
                "start_radio", "radio" -> value == "1" || value.equals("true", ignoreCase = true)
                else -> false
            }
        }
    }

    /**
     * Strips URL tracking / referrer parameters YouTube, Google and friends
     * append (`utm_*`, `si`, `t`, …). Video ids (`v`, `id`) and playlist
     * params (`list`) are kept.
     */
    fun normalize(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val base = url.substring(0, queryStart)
        val fragment = url.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
        val kept = url.substring(queryStart + 1).substringBefore('#')
            .split('&')
            .filterNot { param ->
                val name = param.substringBefore('=', "").lowercase()
                name.isEmpty() ||
                    name.startsWith("utm_") ||
                    name in trackingParams
            }
        return if (kept.isEmpty()) base + fragment
        else base + "?" + kept.joinToString("&") + fragment
    }

    private val trackingParams = setOf(
        "si", "t", "feature", "gclid", "dclid", "msclkid", "fbclid", "rva", "yttag",
        "sabl", "msexp", "mshq", "mshid", "aud", "pp", "adid", "anv", "mcid", "cr",
        "flow", "pmtn", "source_extra", "spm_id_from", "share_id", "app", "feed_alert",
    )
}
