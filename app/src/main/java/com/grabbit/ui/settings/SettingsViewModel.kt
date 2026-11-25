package com.grabbit.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.core.AppLog
import com.grabbit.core.Languages
import com.grabbit.core.Limits
import com.grabbit.core.Storage
import com.grabbit.core.stateInUi
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.settings.ThemeMode
import com.grabbit.data.ytdlp.UpdateOutcome
import com.grabbit.data.ytdlp.YtDlpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

/** Owns settings edits, per-app language changes, SAF folders and yt-dlp updates. */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SettingsRepository,
        private val ytDlp: YtDlpEngine,
    ) : ViewModel() {
        /** Progress and outcome of a yt-dlp update check. */
        data class UpdateState(
            val running: Boolean = false,
            val updated: Boolean = false,
            val upToDate: Boolean = false,
            val error: String? = null,
        )

        val settings: StateFlow<AppSettings> =
            repository.settings
                .stateInUi(viewModelScope, AppSettings())

        private val _ytDlpVersion = MutableStateFlow<String?>(null)
        val ytDlpVersion: StateFlow<String?> = _ytDlpVersion.asStateFlow()

        private val _updateState = MutableStateFlow(UpdateState())
        val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

        private val _message = MutableStateFlow<Int?>(null)
        val message: StateFlow<Int?> = _message.asStateFlow()

        init {
            refreshVersion()
        }

        /** Reads the installed yt-dlp version for display. */
        fun refreshVersion() {
            viewModelScope.launch { _ytDlpVersion.value = ytDlp.installedVersion() }
        }

        /** Checks for a new yt-dlp release and refreshes the displayed version. */
        fun updateYtDlp() {
            if (_updateState.value.running) return
            _updateState.value = UpdateState(running = true)
            viewModelScope.launch {
                val outcome = ytDlp.update()
                _updateState.value =
                    when (outcome) {
                        is UpdateOutcome.Updated -> UpdateState(updated = true)
                        is UpdateOutcome.UpToDate -> UpdateState(upToDate = true)
                        is UpdateOutcome.Failed -> UpdateState(error = outcome.message)
                    }
                refreshVersion()
            }
        }

        /** Clears the snackbar message. */
        fun consumeMessage() {
            _message.value = null
        }

        /** Persists the selected theme mode. */
        fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }

        /** Enables or disables dynamic colors. */
        fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }

        /** Persists the choice and applies it immediately (per-app language). */
        fun setLanguage(tag: String?) =
            viewModelScope.launch {
                repository.setLanguage(tag)
                Languages.apply(tag)
            }

        /** Persists the default video height. */
        fun setVideoHeight(height: Int) = viewModelScope.launch { repository.setDefaultVideoHeight(height) }

        /** Persists the default video container. */
        fun setVideoContainer(container: String) =
            viewModelScope.launch { repository.setDefaultVideoContainer(container) }

        /** Persists the default audio format. */
        fun setAudioFormat(format: String) = viewModelScope.launch { repository.setDefaultAudioFormat(format) }

        /** Persists the default audio bitrate. */
        fun setAudioBitrate(bitrate: Int) = viewModelScope.launch { repository.setDefaultAudioBitrate(bitrate) }

        /** Persists the concurrent download limit. */
        fun setConcurrency(count: Int) = viewModelScope.launch { repository.setConcurrentDownloads(count) }

        /** Enables or disables thumbnail embedding. */
        fun setEmbedThumbnail(enabled: Boolean) = viewModelScope.launch { repository.setEmbedThumbnail(enabled) }

        /** Enables or disables metadata embedding. */
        fun setEmbedMetadata(enabled: Boolean) = viewModelScope.launch { repository.setEmbedMetadata(enabled) }

        /** Enables or disables subtitle download. */
        fun setSubtitles(enabled: Boolean) = viewModelScope.launch { repository.setSubtitles(enabled) }

        /** Enables or disables SponsorBlock removal. */
        fun setSponsorBlock(enabled: Boolean) = viewModelScope.launch { repository.setSponsorBlock(enabled) }

        /** Persists the output filename template. */
        fun setFilenameTemplate(template: String) = viewModelScope.launch { repository.setFilenameTemplate(template) }

        /** Persists the proxy address. */
        fun setProxy(proxy: String) = viewModelScope.launch { repository.setProxy(proxy) }

        /** Enables or disables automatic yt-dlp update checks. */
        fun setAutoCheckUpdates(enabled: Boolean) = viewModelScope.launch { repository.setAutoCheckUpdates(enabled) }

        /** Takes a persistable grant when possible and stores the picked SAF tree. */
        fun setCustomFolder(uri: Uri) {
            viewModelScope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                } catch (e: SecurityException) {
                    // The URI grant is optional; SAF reads may still work without it.
                    AppLog.e(TAG, "could not persist the folder permission", e)
                }
                repository.setCustomFolder(uri.toString())
            }
        }

        /** Returns downloads to MediaStore. */
        fun clearCustomFolder() = viewModelScope.launch { repository.setCustomFolder(null) }

        /** Removes the stored cookies file. */
        fun clearCookies() = viewModelScope.launch { repository.setCookies(null, null) }

        /** Copies a Netscape cookies file into app storage and validates its header. */
        fun importCookies(uri: Uri) {
            viewModelScope.launch {
                val imported =
                    withContext(Dispatchers.IO) {
                        suspendRunCatching {
                            val name = displayName(uri) ?: Storage.COOKIES_FILE_NAME
                            val target = File(context.filesDir, "cookies/${Storage.COOKIES_FILE_NAME}")
                            target.parentFile?.mkdirs()
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("cannot open the selected file")

                            val header =
                                target.bufferedReader().use { reader ->
                                    buildString {
                                        repeat(Limits.COOKIE_HEADER_LINES) {
                                            val line = reader.readLine() ?: return@repeat
                                            appendLine(line)
                                        }
                                    }
                                }
                            val looksLikeCookies =
                                target.length() > 0 &&
                                    (
                                        header.contains("Netscape", ignoreCase = true) ||
                                            header.contains("HTTP Cookie File", ignoreCase = true) ||
                                            header.contains("\t")
                                    )
                            if (!looksLikeCookies) {
                                target.delete()
                                error("not a Netscape cookies.txt file")
                            }
                            name to target.absolutePath
                        }.onFailure { e -> AppLog.e(TAG, "cookie import failed", e) }
                    }

                imported.fold(
                    onSuccess = { (name, path) -> repository.setCookies(path, name) },
                    onFailure = { _message.value = R.string.settings_cookies_invalid },
                )
            }
        }

        private fun displayName(uri: Uri): String? =
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            } catch (e: SecurityException) {
                AppLog.e(TAG, "could not read the display name of a picked file", e)
                null
            } catch (e: IllegalArgumentException) {
                AppLog.e(TAG, "could not read the display name of a picked file", e)
                null
            }
    }
