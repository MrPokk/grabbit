package com.grabbit.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.grabbit.BuildConfig
import java.util.Locale

/**
 * Per-app language (Android 13+ API) via [AppCompatDelegate].
 *
 * [SUPPORTED] is generated from the `i18n/` resource folders. A `null` stored value
 * means "system default": the system locale is used if it is supported,
 * otherwise English is picked on first launch.
 */
object Languages {
    /** The only named default; matches `unqualifiedResLocale`. */
    const val DEFAULT_LANGUAGE = "en"

    /** Language tags generated from the app's translation folders. */
    val SUPPORTED: List<String> = BuildConfig.SUPPORTED_LANGUAGES.toList()

    /** Locale of the system if the app ships a translation for it. */
    fun systemTag(): String? = Locale.getDefault().language.takeIf { it in SUPPORTED }

    /** Resolved tag used when a setting exists: `language ?: systemTag() ?: "en"`. */
    fun tagOf(language: String?): String = language?.takeIf { it in SUPPORTED } ?: systemTag() ?: DEFAULT_LANGUAGE

    /** Display name of a supported language tag, localized to that same language. */
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** Applies a stored language setting immediately; `null` means system default. */
    fun apply(language: String?) {
        if (language == null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagOf(language)))
        }
    }
}
