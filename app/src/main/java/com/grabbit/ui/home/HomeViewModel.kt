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
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.download.DownloadRequest
import com.grabbit.data.download.MediaType
import com.grabbit.data.preview.LinkPreview
import com.grabbit.data.preview.OEmbedPreviewProvider
import com.grabbit.data.search.MediaSearchProvider
import com.grabbit.data.search.MediaSearchResult
import com.grabbit.data.search.SearchException
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.settings.toDownloadOptions
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import com.grabbit.data.ytdlp.availableHeights
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

private const val TAG = "HomeViewModel"

// Debounce delay before auto-fetching after URL change (~300ms)
private const val FETCH_DEBOUNCE_MS = 300L

/** Owns link input, metadata fetching and playlist selection for the home screen. */
@HiltViewModel
@Suppress("TooManyFunctions") // ViewModel with many UI actions; splitting would harm clarity
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ytDlp: YtDlpEngine,
        private val downloads: DownloadRepository,
        private val settingsRepository: SettingsRepository,
        private val searchProvider: MediaSearchProvider,
        private val oEmbed: OEmbedPreviewProvider,
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
            val selectedPlatform: MediaPlatform? = null,
            val searching: Boolean = false,
            val searchResults: List<MediaSearchResult> = emptyList(),
            val searchError: AppError? = null,
            /** Instant oEmbed title/author/thumbnail shown before yt-dlp finishes. */
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
        private var trace: FetchTrace? = null
        private var linkPreviewReported = false
        private var mediaPreviewReported = false

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateInUi(viewModelScope, AppSettings())

        init {
            readClipboard()
        }

        /** Updates the URL field and hides the clipboard hint. Triggers debounced auto-fetch. */
        fun onUrlChange(value: String) {
            _state.update { it.copy(url = value, clipboardHint = false) }
            // A new link means the old extraction is pointless: kill it now, not after the debounce.
            cancelActiveFetch()
            schedulePreview(value)
            scheduleAutoFetch(value)
        }

        /** Fills the URL field from the clipboard when it holds a link. */
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
                    // Re-check URL hasn't changed during debounce
                    if (Links.normalize(_state.value.url) == extracted && _state.value.selectedPlatform == null) {
                        fetchUrl(extracted)
                    }
                }
        }

        /** Shows title/author/thumbnail from oEmbed while the full yt-dlp fetch is running. */
        private fun schedulePreview(rawUrl: String) {
            previewJob?.cancel()
            previewJob = null
            val url = Links.extract(rawUrl)?.let(Links::normalize)
            if (url == null || _state.value.selectedPlatform != null) {
                previewUrl = null
                _state.update { it.copy(preview = null) }
                return
            }
            // Drop the previous link's card immediately; oEmbed may fail for the new one.
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

        /** Handles a link sent by MainActivity and immediately fetches its metadata. */
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

        /** Clears the URL, result and any in-flight extraction. */
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

        /** Toggles a platform and moves the active one between the selector and the field. */
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

        /** Runs the URL fetch or the platform search, depending on the active mode. */
        fun submit() {
            if (_state.value.selectedPlatform == null) fetch() else search()
        }

        /** Searches the selected platform for the current query. */
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
                        AppLog.e(TAG, "search failed", e)
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

        /** Fetches metadata for the current URL. */
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

        /** Opens a search result through the normal metadata pipeline. */
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
            fetchJob =
                viewModelScope.launch {
                    try {
                        val loadAsMix = _state.value.loadAsMix
                        val media = ytDlp.fetch(url, loadAsMix, processId, trace = trace)
                        if (fetchProcessId != processId) return@launch
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
                        AppLog.e(TAG, "metadata fetch failed", e)
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
        }

        /** Logs the moment a preview card (oEmbed or full) reaches the screen. */
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

        private fun resetPreviewReports() {
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

        private fun cancelPreview() {
            previewJob?.cancel()
            previewJob = null
            previewUrl = null
            _state.update { it.copy(preview = null) }
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

        /** Stores the media type used by quick download. */
        fun setQuickMediaType(type: MediaType) {
            viewModelScope.launch { settingsRepository.setQuickMediaType(type) }
        }

        /** Enqueues a download with the current defaults without opening the sheet. */
        fun quickDownload() {
            val media = _state.value.media ?: return
            val settings = settings.value
            startDownload(settings.toDownloadOptions(settings.quickMediaType, media.availableHeights()))
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
                                infoJsonPath = media.infoJsonPath,
                                infoFetchedAtMs = media.infoFetchedAtMs,
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
            cancelPreview()
            trace = null
            resetPreviewReports()
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
