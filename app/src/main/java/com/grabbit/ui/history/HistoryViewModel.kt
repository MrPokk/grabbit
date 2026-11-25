package com.grabbit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.data.db.HistoryDao
import com.grabbit.data.db.HistoryEntity
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.download.DownloadRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: HistoryDao,
    private val downloads: DownloadRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val items: StateFlow<List<HistoryEntity>> = _query
        .debounce(150)
        .flatMapLatest { value -> dao.observe(value.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun delete(entity: HistoryEntity) {
        viewModelScope.launch { dao.delete(entity) }
    }

    fun redownload(entity: HistoryEntity) {
        val options = runCatching {
            json.decodeFromString<DownloadOptions>(entity.optionsJson)
        }.getOrDefault(DownloadOptions())
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
