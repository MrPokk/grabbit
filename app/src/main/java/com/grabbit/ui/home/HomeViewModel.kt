package com.grabbit.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.FetchStage
import com.grabbit.core.FetchTrace
import com.grabbit.core.Links
import com.grabbit.core.MediaPlatform
import com.grabbit.core.stateInUi
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.preview.LinkPreview
import com.grabbit.data.preview.OEmbedPreviewProvider
import com.grabbit.data.search.MediaSearchProvider
import com.grabbit.data.search.MediaSearchResult
import com.grabbit.data.search.SearchException
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

internal const val HOME_TAG = "HomeViewModel"

private const val FETCH_DEBOUNCE_MS = 300L

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        internal val ytDlp: YtDlpEngine,
        internal val downloads: DownloadRepository,
        internal val settingsRepository: SettingsRepository,
        private val searchProvider: MediaSearchProvider,
        private val oEmbed: OEmbedPreviewProvider,
    ) : ViewModel() {
        data class UiState(
            val url: String = "",
            val urlForPlaylistToggle: String = "",
            val loading: Boolean = false,
            val media: FetchedMedia? = null,
            val error: AppError? = null,
            val selectedIndices: Set<Int> = emptySet(),
            val showSheet: Boolean = false,
            val clipboardHint: Boolean = false,
            val loadAsMix: Boolean = false,
            val selectedPlatform: MediaPlatform? = null,
            val searching: Boolean = false,
            val searchResults: List<MediaSearchResult> = emptyList(),
            val searchError: AppError? = null,
            val preview: LinkPreview? = null,
        )

        private val _state = MutableStateFlow(UiState())
        val state: StateFlow<UiState> = _state.asStateFlow()

        private var fetchJob: Job? = null
        private var fetchProcessId: String? = null
        private var searchJob: Job? = null
        private var searchGeneration = 0
        private var previewJob: Job? = null
        private var previewUrl: String? = null
        internal var trace: FetchTrace? = null
        private var linkPreviewReported = false
        private var mediaPreviewReported = false

        internal fun updateState(block: (UiState) -> UiState) = _state.update(block)

        internal fun replaceState(value: UiState) {
            _state.value = value
        }

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateInUi(viewModelScope, AppSettings())

        init {
            readClipboard()
        }

        fun onUrlChange(value: String) {
            _state.update { it.copy(url = value, clipboardHint = false) }

            cancelActiveFetch()
            schedulePreview(value)
            scheduleAutoFetch(value)
        }

        fun pasteFromClipboard() {
            val link = clipboardLink()
            if (link != null) {
                _state.update { it.copy(url = link, clipboardHint = true, error = null) }
                schedulePreview(link)
                scheduleAutoFetch(link)
            }
        }

        private var autoFetchJob: Job? = null

        private fun scheduleAutoFetch(rawUrl: String) {
            val normalizeStartedAt = FetchTrace.startedAt()
            val extracted = Links.extract(rawUrl)?.let(Links::normalize)
            val normalizeMs = FetchTrace.sinceMs(normalizeStartedAt)
            if (extracted == null || _state.value.selectedPlatform != null) {
                trace = null
                resetPreviewReports()
                return
            }
            val startedTrace = FetchTrace(extracted)
            startedTrace.record(FetchStage.URL_NORMALIZE, normalizeMs)
            trace = startedTrace
            resetPreviewReports()
            autoFetchJob?.cancel()
            autoFetchJob =
                viewModelScope.launch {
                    delay(FETCH_DEBOUNCE_MS)

                    if (Links.normalize(_state.value.url) == extracted && _state.value.selectedPlatform == null) {
                        fetchUrl(extracted)
                    }
                }
        }

        private fun schedulePreview(rawUrl: String) {
            previewJob?.cancel()
            previewJob = null
            val url = Links.extract(rawUrl)?.let(Links::normalize)
            if (url == null || _state.value.selectedPlatform != null) {
                previewUrl = null
                _state.update { it.copy(preview = null) }
                return
            }

            if (previewUrl != url) {
                previewUrl = url
                _state.update { it.copy(preview = null) }
            }
            previewJob =
                viewModelScope.launch {
                    val preview = oEmbed.fetch(url)
                    if (preview != null && Links.normalize(_state.value.url) == url) {
                        previewUrl = url
                        _state.update { it.copy(preview = preview) }
                    }
                }
        }

        fun acceptSharedLink(link: String) {
            val normalizeStartedAt = FetchTrace.startedAt()
            val clean = Links.extract(link)?.let(Links::normalize) ?: return
            val normalizeMs = FetchTrace.sinceMs(normalizeStartedAt)
            _state.update {
                it.copy(
                    url = clean,
                    selectedPlatform = null,
                    searchResults = emptyList(),
                    searchError = null,
                    error = null,
                    media = null,
                )
            }
            startTrace(clean, normalizeMs)
            schedulePreview(clean)
            fetchUrl(clean)
        }

        fun clear() {
            cancelWork()
            cancelPreview()
            trace = null
            resetPreviewReports()
            val platform = _state.value.selectedPlatform
            _state.update {
                UiState(
                    url = "",
                    clipboardHint = false,
                    selectedPlatform = platform,
                )
            }
        }

        fun onPlatformClick(platform: MediaPlatform) {
            cancelWork()
            cancelPreview()
            trace = null
            resetPreviewReports()
            val current = _state.value.selectedPlatform
            _state.update {
                if (current == platform) {
                    it.copy(
                        selectedPlatform = null,
                        searchResults = emptyList(),
                        searchError = null,
                        searching = false,
                    )
                } else {
                    it.copy(
                        selectedPlatform = platform,
                        searchResults = emptyList(),
                        searchError = null,
                        searching = false,
                        media = null,
                        error = null,
                        selectedIndices = emptySet(),
                        showSheet = false,
                    )
                }
            }
        }

        fun submit() {
            if (_state.value.selectedPlatform == null) fetch() else search()
        }

        fun search() {
            val platform = _state.value.selectedPlatform ?: return
            val query = _state.value.url.trim()
            if (query.isEmpty()) return
            cancelWork()
            val generation = ++searchGeneration
            _state.update {
                it.copy(
                    searching = true,
                    searchError = null,
                    searchResults = emptyList(),
                    error = null,
                    media = null,
                )
            }
            searchJob =
                viewModelScope.launch {
                    try {
                        val results = searchProvider.search(platform, query)
                        if (searchGeneration != generation) return@launch
                        _state.update {
                            if (it.selectedPlatform == platform && it.url.trim() == query) {
                                it.copy(searchResults = results)
                            } else {
                                it
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SearchException) {
                        AppLog.e(HOME_TAG, "search failed", e)
                        if (searchGeneration == generation) {
                            _state.update { it.copy(searchError = e.error) }
                        }
                    } finally {
                        if (searchGeneration == generation) {
                            _state.update { it.copy(searching = false) }
                        }
                    }
                }
        }

        fun fetch() {
            cancelWork()
            val url = Links.extract(currentUrl())
            if (url == null) {
                _state.update { it.copy(error = AppError(R.string.error_invalid_url), loading = false) }
                return
            }
            startTrace(url)
            fetchUrl(url)
        }

        fun openSearchResult(result: MediaSearchResult) {
            _state.update { it.copy(url = result.url, error = null, media = null, showSheet = false) }
            startTrace(Links.normalize(result.url))
            fetchUrl(Links.normalize(result.url))
        }

        private fun startTrace(
            url: String,
            normalizeMs: Long = 0L,
        ) {
            val started = FetchTrace(url)
            started.record(FetchStage.URL_NORMALIZE, normalizeMs)
            trace = started
            resetPreviewReports()
        }

        private fun fetchUrl(url: String) {
            cancelWork()
            val processId = UUID.randomUUID().toString()
            fetchProcessId = processId
            if (trace == null) startTrace(url)
            _state.update {
                it.copy(
                    url = url,
                    urlForPlaylistToggle = url,
                    loading = true,
                    error = null,
                    media = null,
                    searchError = null,
                )
            }
            fetchJob = viewModelScope.launch { launchFetch(url, processId) }
        }

        private suspend fun launchFetch(
            url: String,
            processId: String,
        ) {
            try {
                val loadAsMix = _state.value.loadAsMix
                val media = ytDlp.fetch(url, loadAsMix, processId, trace = trace)
                if (fetchProcessId != processId) return
                val selected =
                    (media as? FetchedMedia.Collection)
                        ?.items
                        ?.map { it.index }
                        ?.toSet()
                        .orEmpty()
                val updateStartedAt = FetchTrace.startedAt()
                _state.update { it.copy(media = media, selectedIndices = selected) }
                trace?.record(FetchStage.STATE_UPDATE, FetchTrace.sinceMs(updateStartedAt))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InfoException) {
                AppLog.e(HOME_TAG, "metadata fetch failed", e)
                if (fetchProcessId == processId) {
                    _state.update { it.copy(error = e.error) }
                }
            } finally {
                if (fetchProcessId == processId) {
                    fetchProcessId = null
                    _state.update { it.copy(loading = false) }
                }
            }
        }

        fun onPreviewShown(hasMedia: Boolean) {
            val activeTrace = trace ?: return
            if (hasMedia) {
                if (mediaPreviewReported) return
                mediaPreviewReported = true
            } else {
                if (linkPreviewReported) return
                linkPreviewReported = true
            }
            activeTrace.record(FetchStage.PREVIEW_SHOWN, activeTrace.totalMs())
            activeTrace.logSummary()
        }

        internal fun resetPreviewReports() {
            linkPreviewReported = false
            mediaPreviewReported = false
        }

        private fun cancelActiveFetch() {
            if (fetchJob == null && fetchProcessId == null) return
            fetchJob?.cancel()
            fetchJob = null
            fetchProcessId?.let(ytDlp::cancelFetch)
            fetchProcessId = null
            _state.update { it.copy(loading = false) }
        }

        private fun cancelWork() {
            autoFetchJob?.cancel()
            autoFetchJob = null
            cancelActiveFetch()
            searchJob?.cancel()
            searchJob = null
            searchGeneration++
            _state.update { it.copy(loading = false, searching = false) }
        }

        internal fun cancelPreview() {
            previewJob?.cancel()
            previewJob = null
            previewUrl = null
            _state.update { it.copy(preview = null) }
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
            return if (clip.itemCount == 0) null else Links.extract(clip.getItemAt(0).coerceToText(context).toString())
        }
    }
