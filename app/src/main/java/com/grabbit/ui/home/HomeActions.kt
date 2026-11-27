package com.grabbit.ui.home

import androidx.lifecycle.viewModelScope
import com.grabbit.core.AppLog
import com.grabbit.core.Links
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.DownloadRequest
import com.grabbit.data.download.MediaType
import com.grabbit.data.settings.toDownloadOptions
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.availableHeights
import com.grabbit.ui.home.HomeViewModel.UiState
import kotlinx.coroutines.launch

internal fun HomeViewModel.toggleLoadAsMix() {
    val originalUrl = state.value.urlForPlaylistToggle
    if (originalUrl.isBlank() || !Links.isRadioPlaylist(originalUrl)) return
    updateState { it.copy(loadAsMix = !it.loadAsMix, media = null, loading = false) }
    fetch()
}

internal fun HomeViewModel.updateYtDlpAndRetry() {
    viewModelScope.launch {
        suspendRunCatching { ytDlp.update() }
            .onFailure { e -> AppLog.e(HOME_TAG, "yt-dlp update failed", e) }
        fetch()
    }
}

internal fun HomeViewModel.toggleSelection(index: Int) {
    updateState { state ->
        val selected = state.selectedIndices.toMutableSet()
        if (!selected.add(index)) selected.remove(index)
        state.copy(selectedIndices = selected)
    }
}

internal fun HomeViewModel.selectAll() {
    val media = state.value.media as? FetchedMedia.Collection ?: return
    updateState { it.copy(selectedIndices = media.items.map { item -> item.index }.toSet()) }
}

internal fun HomeViewModel.deselectAll() {
    updateState { it.copy(selectedIndices = emptySet()) }
}

internal fun HomeViewModel.openSheet() {
    if (state.value.media == null) return
    updateState { it.copy(showSheet = true) }
}

internal fun HomeViewModel.dismissSheet() {
    updateState { it.copy(showSheet = false) }
}

internal fun HomeViewModel.setQuickMediaType(type: MediaType) {
    viewModelScope.launch { settingsRepository.setQuickMediaType(type) }
}

internal fun HomeViewModel.quickDownload() {
    val media = state.value.media ?: return
    val settings = settings.value
    startDownload(settings.toDownloadOptions(settings.quickMediaType, media.availableHeights()))
}

internal fun HomeViewModel.startDownload(options: DownloadOptions) {
    val current = state.value
    val media = current.media ?: return
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
                    .filter { it.index in current.selectedIndices }
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
    replaceState(UiState())
}
