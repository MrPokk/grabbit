package com.grabbit.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.grabbit.BuildConfig
import java.util.Locale

object Languages {
    const val DEFAULT_LANGUAGE = "en"

    val SUPPORTED: List<String> = BuildConfig.SUPPORTED_LANGUAGES.toList()

    fun systemTag(): String? = Locale.getDefault().language.takeIf { it in SUPPORTED }

    fun tagOf(language: String?): String = language?.takeIf { it in SUPPORTED } ?: systemTag() ?: DEFAULT_LANGUAGE

    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    fun apply(language: String?) {
        if (language == null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tagOf(language)))
        }
    }
}
