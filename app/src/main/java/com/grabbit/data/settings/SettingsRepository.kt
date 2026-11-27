package com.grabbit.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grabbit.core.AppLog
import com.grabbit.core.Defaults
import com.grabbit.core.Languages
import com.grabbit.core.Limits
import com.grabbit.core.Storage
import com.grabbit.data.download.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SettingsRepository"

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultVideoHeight: Int = Defaults.VIDEO_HEIGHT,
    val defaultVideoContainer: String = Defaults.VIDEO_CONTAINER,
    val defaultAudioFormat: String = Defaults.AUDIO_FORMAT,
    val defaultAudioBitrate: Int = Defaults.AUDIO_BITRATE_KBPS,
    val quickMediaType: MediaType = MediaType.VIDEO,
    val concurrentDownloads: Int = Defaults.CONCURRENT_DOWNLOADS,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val subtitles: Boolean = false,
    val sponsorBlock: Boolean = false,
    val filenameTemplate: String = "%(title)s - %(uploader)s",
    val cookiesPath: String? = null,
    val cookiesName: String? = null,
    val proxy: String = "",
    val customFolderUri: String? = null,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val language: String? = null,
) {
    val hasCustomFolder: Boolean get() = !customFolderUri.isNullOrBlank()
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private object Keys {
            val THEME = stringPreferencesKey("theme")
            val DYNAMIC = booleanPreferencesKey("dynamic_color")
            val VIDEO_HEIGHT = intPreferencesKey("video_height")
            val VIDEO_CONTAINER = stringPreferencesKey("video_container")
            val AUDIO_FORMAT = stringPreferencesKey("audio_format")
            val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
            val QUICK_MEDIA_TYPE = stringPreferencesKey("quick_media_type")
            val CONCURRENT = intPreferencesKey("concurrent")
            val EMBED_THUMBNAIL = booleanPreferencesKey("embed_thumbnail")
            val EMBED_METADATA = booleanPreferencesKey("embed_metadata")
            val SUBTITLES = booleanPreferencesKey("subtitles")
            val SPONSORBLOCK = booleanPreferencesKey("sponsorblock")
            val FILENAME = stringPreferencesKey("filename_template")
            val COOKIES_PATH = stringPreferencesKey("cookies_path")
            val COOKIES_NAME = stringPreferencesKey("cookies_name")
            val PROXY = stringPreferencesKey("proxy")
            val FOLDER_URI = stringPreferencesKey("folder_uri")
            val LANGUAGE = stringPreferencesKey("language")
            val AUTO_UPDATE = booleanPreferencesKey("auto_update")
            val LAST_UPDATE = longPreferencesKey("last_update")
        }

        val settings: Flow<AppSettings> =
            context.settingsStore.data
                .catch { e ->
                    if (e is IOException) {
                        AppLog.e(TAG, "could not read settings; using defaults", e)
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.map { prefs ->
                    val defaults = AppSettings()
                    AppSettings(
                        themeMode =
                            prefs[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                                ?: defaults.themeMode,
                        dynamicColor = prefs[Keys.DYNAMIC] ?: defaults.dynamicColor,
                        defaultVideoHeight = prefs[Keys.VIDEO_HEIGHT] ?: defaults.defaultVideoHeight,
                        defaultVideoContainer = prefs[Keys.VIDEO_CONTAINER] ?: defaults.defaultVideoContainer,
                        defaultAudioFormat = prefs[Keys.AUDIO_FORMAT] ?: defaults.defaultAudioFormat,
                        defaultAudioBitrate = prefs[Keys.AUDIO_BITRATE] ?: defaults.defaultAudioBitrate,
                        quickMediaType =
                            prefs[Keys.QUICK_MEDIA_TYPE]
                                ?.let { name -> MediaType.entries.firstOrNull { it.name == name } }
                                ?: defaults.quickMediaType,
                        concurrentDownloads =
                            (prefs[Keys.CONCURRENT] ?: defaults.concurrentDownloads)
                                .coerceIn(Limits.MIN_CONCURRENT_DOWNLOADS, Limits.MAX_CONCURRENT_DOWNLOADS),
                        embedThumbnail = prefs[Keys.EMBED_THUMBNAIL] ?: defaults.embedThumbnail,
                        embedMetadata = prefs[Keys.EMBED_METADATA] ?: defaults.embedMetadata,
                        subtitles = prefs[Keys.SUBTITLES] ?: defaults.subtitles,
                        sponsorBlock = prefs[Keys.SPONSORBLOCK] ?: defaults.sponsorBlock,
                        filenameTemplate = prefs[Keys.FILENAME] ?: defaults.filenameTemplate,
                        cookiesPath = prefs[Keys.COOKIES_PATH],
                        cookiesName = prefs[Keys.COOKIES_NAME],
                        proxy = prefs[Keys.PROXY] ?: defaults.proxy,
                        customFolderUri = prefs[Keys.FOLDER_URI],
                        autoCheckUpdates = prefs[Keys.AUTO_UPDATE] ?: defaults.autoCheckUpdates,
                        lastUpdateCheck = prefs[Keys.LAST_UPDATE] ?: defaults.lastUpdateCheck,
                        language = prefs[Keys.LANGUAGE]?.takeIf { it in Languages.SUPPORTED },
                    )
                }

        suspend fun current(): AppSettings = settings.first()

        suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

        suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC] = enabled }

        suspend fun setDefaultVideoHeight(height: Int) = edit { it[Keys.VIDEO_HEIGHT] = height }

        suspend fun setDefaultVideoContainer(container: String) = edit { it[Keys.VIDEO_CONTAINER] = container }

        suspend fun setDefaultAudioFormat(format: String) = edit { it[Keys.AUDIO_FORMAT] = format }

        suspend fun setDefaultAudioBitrate(bitrate: Int) = edit { it[Keys.AUDIO_BITRATE] = bitrate }

        suspend fun setQuickMediaType(type: MediaType) = edit { it[Keys.QUICK_MEDIA_TYPE] = type.name }

        suspend fun setConcurrentDownloads(count: Int) =
            edit {
                it[Keys.CONCURRENT] = count.coerceIn(Limits.MIN_CONCURRENT_DOWNLOADS, Limits.MAX_CONCURRENT_DOWNLOADS)
            }

        suspend fun setEmbedThumbnail(enabled: Boolean) = edit { it[Keys.EMBED_THUMBNAIL] = enabled }

        suspend fun setEmbedMetadata(enabled: Boolean) = edit { it[Keys.EMBED_METADATA] = enabled }

        suspend fun setSubtitles(enabled: Boolean) = edit { it[Keys.SUBTITLES] = enabled }

        suspend fun setSponsorBlock(enabled: Boolean) = edit { it[Keys.SPONSORBLOCK] = enabled }

        suspend fun setFilenameTemplate(template: String) = edit { it[Keys.FILENAME] = template }

        suspend fun setCookies(
            path: String?,
            name: String?,
        ) = edit {
            if (path == null) {
                it.remove(Keys.COOKIES_PATH)
                it.remove(Keys.COOKIES_NAME)
            } else {
                it[Keys.COOKIES_PATH] = path
                it[Keys.COOKIES_NAME] = name ?: Storage.COOKIES_FILE_NAME
            }
        }

        suspend fun setProxy(proxy: String) = edit { it[Keys.PROXY] = proxy.trim() }

        suspend fun setCustomFolder(uri: String?) =
            edit {
                if (uri == null) it.remove(Keys.FOLDER_URI) else it[Keys.FOLDER_URI] = uri
            }

        suspend fun setAutoCheckUpdates(enabled: Boolean) = edit { it[Keys.AUTO_UPDATE] = enabled }

        suspend fun setLanguage(tag: String?) =
            edit {
                if (tag == null) it.remove(Keys.LANGUAGE) else it[Keys.LANGUAGE] = tag
            }

        suspend fun setLastUpdateCheck(timestamp: Long) = edit { it[Keys.LAST_UPDATE] = timestamp }

        private suspend fun edit(block: (MutablePreferences) -> Unit) {
            context.settingsStore.edit { block(it) }
        }
    }
