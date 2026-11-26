package com.grabbit.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.Links
import com.grabbit.core.stateInUi
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.download.DownloadRequest
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "HomeViewModel"

/** Owns link input, metadata fetching and playlist selection for the home screen. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ytDlp: YtDlpEngine,
        private val downloads: DownloadRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Everything the home screen needs to render its current state. */
        data class UiState(
            val url: String = "",
            // Keep the original request URL for radio/toggle logic.
            val urlForPlaylistToggle: String = "",
            val loading: Boolean = false,
            val media: FetchedMedia? = null,
            val error: AppError? = null,
            val selectedIndices: Set<Int> = emptySet(),
            val showSheet: Boolean = false,
            val clipboardHint: Boolean = false,
            val loadAsMix: Boolean = false,
        )

        private val _state = MutableStateFlow(UiState())
        val state: StateFlow<UiState> = _state.asStateFlow()

        private var fetchJob: Job? = null
        private var fetchProcessId: String? = null

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateInUi(viewModelScope, AppSettings())

        init {
            readClipboard()
        }

        /** Updates the URL field and hides the clipboard hint. */
        fun onUrlChange(value: String) {
            _state.update { it.copy(url = value, clipboardHint = false) }
        }

        /** Fills the URL field from the clipboard when it holds a link. */
        fun pasteFromClipboard() {
            val link = clipboardLink()
            if (link != null) {
                _state.update { it.copy(url = link, clipboardHint = true, error = null) }
            }
        }

        /** Handles a link sent by MainActivity and immediately fetches its metadata. */
        fun acceptSharedLink(link: String) {
            val clean = Links.extract(link)?.let(Links::normalize) ?: return
            _state.update { it.copy(url = clean, error = null, media = null) }
            fetch()
        }

        /** Clears the URL, result and any in-flight extraction. */
        fun clear() {
            fetchJob?.cancel()
            fetchProcessId?.let(ytDlp::cancelFetch)
            fetchProcessId = null
            _state.update {
                UiState(
                    url = "",
                    clipboardHint = false,
                )
            }
        }

        /** Fetches metadata for the current URL. */
        fun fetch() {
            fetchJob?.cancel()
            fetchProcessId?.let(ytDlp::cancelFetch)
            val url = Links.extract(currentUrl())
            if (url == null) {
                _state.update { it.copy(error = AppError(R.string.error_invalid_url), loading = false) }
                return
            }
            val processId = UUID.randomUUID().toString()
            fetchProcessId = processId
            _state.update {
                it.copy(url = url, urlForPlaylistToggle = url, loading = true, error = null, media = null)
            }
            fetchJob =
                viewModelScope.launch {
                    try {
                        val loadAsMix = _state.value.loadAsMix
                        val media = ytDlp.fetch(url, loadAsMix, processId)
                        val selected =
                            (media as? FetchedMedia.Collection)
                                ?.items
                                ?.map { it.index }
                                ?.toSet()
                                .orEmpty()
                        _state.update { it.copy(media = media, selectedIndices = selected) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: InfoException) {
                        AppLog.e(TAG, "metadata fetch failed", e)
                        _state.update { it.copy(error = e.error) }
                    } finally {
                        fetchProcessId = null
                        _state.update { it.copy(loading = false) }
                    }
                }
        }

        /** Toggles a Mix URL between a single video and a bounded playlist, then refetches. */
        fun toggleLoadAsMix() {
            val originalUrl = _state.value.urlForPlaylistToggle
            if (originalUrl.isBlank() || !Links.isRadioPlaylist(originalUrl)) return
            _state.update { it.copy(loadAsMix = !it.loadAsMix, media = null, loading = false) }
            fetch()
        }

        /** Updates yt-dlp and retries the current fetch, logging a failed update. */
        fun updateYtDlpAndRetry() {
            viewModelScope.launch {
                suspendRunCatching { ytDlp.update() }
                    .onFailure { e -> AppLog.e(TAG, "yt-dlp update failed", e) }
                fetch()
            }
        }

        /** Adds or removes one playlist item from the selection. */
        fun toggleSelection(index: Int) {
            _state.update { state ->
                val selected = state.selectedIndices.toMutableSet()
                if (!selected.add(index)) selected.remove(index)
                state.copy(selectedIndices = selected)
            }
        }

        /** Selects every item of the fetched collection. */
        fun selectAll() {
            val media = _state.value.media as? FetchedMedia.Collection ?: return
            _state.update { it.copy(selectedIndices = media.items.map { item -> item.index }.toSet()) }
        }

        /** Clears the playlist selection. */
        fun deselectAll() {
            _state.update { it.copy(selectedIndices = emptySet()) }
        }

        /** Opens the download options sheet when media has been fetched. */
        fun openSheet() {
            if (_state.value.media == null) return
            _state.update { it.copy(showSheet = true) }
        }

        /** Closes the download options sheet. */
        fun dismissSheet() {
            _state.update { it.copy(showSheet = false) }
        }

        /** Enqueues the selected items. */
        fun startDownload(options: DownloadOptions) {
            val state = _state.value
            val media = state.media ?: return
            val requests =
                when (media) {
                    is FetchedMedia.Single ->
                        listOf(
                            DownloadRequest(
                                url = media.info.url,
                                title = media.title,
                                uploader = media.uploader,
                                thumbnail = media.thumbnail,
                                durationSeconds = media.info.durationSeconds,
                                site = media.info.site,
                                options = options,
                            ),
                        )

                    is FetchedMedia.Collection ->
                        media.items
                            .filter { it.index in state.selectedIndices }
                            .map { item ->
                                DownloadRequest(
                                    url = item.url,
                                    title = item.title,
                                    uploader = media.uploader,
                                    thumbnail = item.thumbnail ?: media.thumbnail,
                                    durationSeconds = item.durationSeconds,
                                    site = null,
                                    options = options,
                                    playlistIndex = item.index,
                                )
                            }
                }

            if (requests.isEmpty()) return
            downloads.enqueue(requests)
            _state.update { UiState() }
        }

        private fun currentUrl(): String = Links.normalize(_state.value.url)

        private fun readClipboard() {
            val link = clipboardLink() ?: return
            if (_state.value.url.isBlank()) {
                _state.update { it.copy(url = link, clipboardHint = true) }
            }
        }

        private fun clipboardLink(): String? {
            val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0).coerceToText(context).toString()
            return Links.extract(text)
        }
    }
