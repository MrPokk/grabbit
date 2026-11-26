package com.grabbit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.core.AppLog
import com.grabbit.core.Timing
import com.grabbit.core.stateInUi
import com.grabbit.data.db.HistoryDao
import com.grabbit.data.db.HistoryEntity
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.download.DownloadRequest
import com.grabbit.data.media.SavedFileDeleter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "HistoryViewModel"

/** Filters history entries and re-enqueues saved downloads. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val dao: HistoryDao,
        private val downloads: DownloadRepository,
        private val deleter: SavedFileDeleter,
    ) : ViewModel() {
        private val json = Json { ignoreUnknownKeys = true }

        private val _message = MutableStateFlow<Int?>(null)
        val message: StateFlow<Int?> = _message.asStateFlow()

        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        val items: StateFlow<List<HistoryEntity>> =
            _query
                .debounce(Timing.SEARCH_DEBOUNCE.inWholeMilliseconds)
                .flatMapLatest { value -> dao.observe(value.trim()) }
                .stateInUi(viewModelScope, emptyList())

        /** Updates the debounced history filter. */
        fun setQuery(value: String) {
            _query.value = value
        }

        /** Removes one entry from history, keeping its file. */
        fun remove(entity: HistoryEntity) {
            viewModelScope.launch { dao.delete(entity) }
        }

        /** Deletes the saved file first, then drops the entry only when deletion succeeded. */
        fun deleteFromDevice(entity: HistoryEntity) {
            viewModelScope.launch {
                if (deleter.delete(entity.uri)) {
                    dao.delete(entity)
                } else {
                    _message.value = R.string.error_delete_failed
                }
            }
        }

        fun consumeMessage() {
            _message.value = null
        }

        /** Re-enqueues a saved download, falling back to defaults for unreadable options. */
        fun redownload(entity: HistoryEntity) {
            val options =
                try {
                    json.decodeFromString<DownloadOptions>(entity.optionsJson)
                } catch (e: SerializationException) {
                    AppLog.e(TAG, "could not read saved download options", e)
                    DownloadOptions()
                }
            downloads.enqueue(
                listOf(
                    DownloadRequest(
                        url = entity.url,
                        title = entity.title,
                        uploader = entity.uploader,
                        thumbnail = entity.thumbnail,
                        durationSeconds = entity.durationSeconds,
                        site = entity.sourceSite,
                        options = options,
                    ),
                ),
            )
        }
    }
