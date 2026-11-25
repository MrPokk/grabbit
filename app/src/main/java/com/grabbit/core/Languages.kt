package com.grabbit.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Per-app language (Android 13+ API) via [AppCompatDelegate].
 *
 * [SUPPORTED] mirrors the app's `locales_config.xml`. A `null` stored value
 * means "system default": the system locale is used if it is supported,
 * otherwise English is picked on first launch.
 */
object Languages {

    const val DEFAULT_LANGUAGE = "en"

    val SUPPORTED = listOf("en", "ru")

    /** Locale of the system if the app ships a translation for it. */
    fun systemTag(): String? = Locale.getDefault().language.takeIf { it in SUPPORTED }

    /** Resolved tag used when a setting exists: `language ?: systemTag() ?: "en"`. */
    fun tagOf(language: String?): String =
        language?.takeIf { it in SUPPORTED } ?: systemTag() ?: DEFAULT_LANGUAGE

    /** Display name of a supported language tag, localized to the current system locale. */
    fun displayName(tag: String): String = Locale.forLanguageTag(tag).displayName

    /** Applies a stored language setting immediately; `null` means system default. */
    fun apply(language: String?) {
        if (language == null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagOf(language)))
        }
    }
}
