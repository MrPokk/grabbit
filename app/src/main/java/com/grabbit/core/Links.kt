package com.grabbit.core

private const val QUERY_PARAM_PARTS = 2

object Links {
    private val urlRegex = Regex("""https?://[^\s<>"']+""")

    private val trailing = setOf('.', ',', ')', ']', '}', '"', '\'', '»', '…', '!', '?', ';', ':')

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = urlRegex.find(text)?.value?.trimEnd { it in trailing } ?: return null
        return trimmed.takeIf { looksLikeUrl(it) }
    }

    fun looksLikeUrl(value: String): Boolean {
        val v = value.trim()
        if (!v.startsWith("http://") && !v.startsWith("https://")) return false
        val host = v.substringAfter("://").substringBefore('/')
        return host.contains('.') && host.length > Limits.MIN_HOST_LENGTH
    }

    fun host(url: String): String =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringAfter('@')
            .substringBefore(':')
            .lowercase()

    fun isYouTube(url: String): Boolean {
        val host = host(url)
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    fun isTikTok(url: String): Boolean {
        val host = host(url)
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    fun isRadioPlaylist(url: String): Boolean {
        val query = url.substringAfter('?', "").substringBefore('#')
        return query.isNotBlank() && query.split('&').any(::isRadioParam)
    }

    private fun isRadioParam(param: String): Boolean {
        val pair = param.split('=', limit = QUERY_PARAM_PARTS)
        val name = pair[0].lowercase()
        val value = pair.getOrNull(1) ?: ""
        return when (name) {
            "list" -> value.startsWith("rd", ignoreCase = true)
            "start_radio", "radio" -> value == "1" || value.equals("true", ignoreCase = true)
            else -> false
        }
    }

    fun normalize(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val base = url.substring(0, queryStart)
        val fragment = url.substringAfter('#', "")
        val fragmentSuffix = if (fragment.isEmpty()) "" else "#$fragment"
        val query = url.substring(queryStart + 1).substringBefore('#')
        val kept =
            query
                .split('&')
                .filterNot { param ->
                    val name = param.substringBefore('=', "").lowercase()
                    name.isEmpty() || name.startsWith("utm_") || name in trackingParams
                }
        return if (kept.isEmpty()) base + fragmentSuffix else base + "?" + kept.joinToString("&") + fragmentSuffix
    }

    private val trackingParams =
        setOf(
            "si",
            "t",
            "feature",
            "gclid",
            "dclid",
            "msclkid",
            "fbclid",
            "rva",
            "yttag",
            "sabl",
            "msexp",
            "mshq",
            "mshid",
            "aud",
            "pp",
            "adid",
            "anv",
            "mcid",
            "cr",
            "flow",
            "pmtn",
            "source_extra",
            "spm_id_from",
            "share_id",
            "app",
            "feed_alert",
        )
}
