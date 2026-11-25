package com.grabbit.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.settings.ThemeMode
import com.grabbit.core.Languages
import com.grabbit.data.ytdlp.UpdateOutcome
import com.grabbit.data.ytdlp.YtDlpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val ytDlp: YtDlpEngine,
) : ViewModel() {

    data class UpdateState(
        val running: Boolean = false,
        val updated: Boolean = false,
        val upToDate: Boolean = false,
        val error: String? = null,
    )

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _ytDlpVersion = MutableStateFlow<String?>(null)
    val ytDlpVersion: StateFlow<String?> = _ytDlpVersion.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    init {
        refreshVersion()
    }

    fun refreshVersion() {
        viewModelScope.launch { _ytDlpVersion.value = ytDlp.installedVersion() }
    }

    fun updateYtDlp() {
        if (_updateState.value.running) return
        _updateState.value = UpdateState(running = true)
        viewModelScope.launch {
            val outcome = ytDlp.update()
            _updateState.value = when (outcome) {
                is UpdateOutcome.Updated -> UpdateState(updated = true)
                is UpdateOutcome.UpToDate -> UpdateState(upToDate = true)
                is UpdateOutcome.Failed -> UpdateState(error = outcome.message)
            }
            refreshVersion()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setTheme(mode: ThemeMode) = launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { repository.setDynamicColor(enabled) }

    /** Persists the choice and applies it immediately (per-app language). */
    fun setLanguage(tag: String?) = launch {
        repository.setLanguage(tag)
        Languages.apply(tag)
    }
    fun setVideoHeight(height: Int) = launch { repository.setDefaultVideoHeight(height) }
    fun setVideoContainer(container: String) = launch { repository.setDefaultVideoContainer(container) }
    fun setAudioFormat(format: String) = launch { repository.setDefaultAudioFormat(format) }
    fun setAudioBitrate(bitrate: Int) = launch { repository.setDefaultAudioBitrate(bitrate) }
    fun setConcurrency(count: Int) = launch { repository.setConcurrentDownloads(count) }
    fun setEmbedThumbnail(enabled: Boolean) = launch { repository.setEmbedThumbnail(enabled) }
    fun setEmbedMetadata(enabled: Boolean) = launch { repository.setEmbedMetadata(enabled) }
    fun setSubtitles(enabled: Boolean) = launch { repository.setSubtitles(enabled) }
    fun setSponsorBlock(enabled: Boolean) = launch { repository.setSponsorBlock(enabled) }
    fun setFilenameTemplate(template: String) = launch { repository.setFilenameTemplate(template) }
    fun setProxy(proxy: String) = launch { repository.setProxy(proxy) }
    fun setAutoCheckUpdates(enabled: Boolean) = launch { repository.setAutoCheckUpdates(enabled) }

    fun setCustomFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            repository.setCustomFolder(uri.toString())
        }
    }

    fun clearCustomFolder() = launch { repository.setCustomFolder(null) }

    fun clearCookies() = launch { repository.setCookies(null, null) }

    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val name = displayName(uri) ?: "cookies.txt"
                    val target = File(context.filesDir, "cookies/cookies.txt")
                    target.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("cannot open the selected file")

                    val header = target.bufferedReader().use { reader ->
                        buildString {
                            repeat(4) {
                                val line = reader.readLine() ?: return@repeat
                                appendLine(line)
                            }
                        }
                    }
                    val looksLikeCookies = target.length() > 0 &&
                        (header.contains("Netscape", ignoreCase = true) ||
                            header.contains("HTTP Cookie File", ignoreCase = true) ||
                            header.contains("\t"))
                    if (!looksLikeCookies) {
                        target.delete()
                        error("not a Netscape cookies.txt file")
                    }
                    name to target.absolutePath
                }
            }

            imported.fold(
                onSuccess = { (name, path) -> repository.setCookies(path, name) },
                onFailure = { _message.value = R.string.settings_cookies_invalid },
            )
        }
    }

    private fun displayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
