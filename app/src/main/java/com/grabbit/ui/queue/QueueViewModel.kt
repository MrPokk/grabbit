package com.grabbit.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.R
import com.grabbit.core.stateInUi
import com.grabbit.data.db.HistoryDao
import com.grabbit.data.download.DownloadEngine
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.download.DownloadTask
import com.grabbit.data.media.SavedFileDeleter
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel
    @Inject
    constructor(
        private val engine: DownloadEngine,
        private val downloads: DownloadRepository,
        private val deleter: SavedFileDeleter,
        private val history: HistoryDao,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private val _message = MutableStateFlow<Int?>(null)
        val message: StateFlow<Int?> = _message.asStateFlow()

        val tasks: StateFlow<List<DownloadTask>> =
            engine.tasks
                .stateInUi(viewModelScope, emptyList())

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateInUi(viewModelScope, AppSettings())

        fun pause(id: String) {
            engine.pause(id)
        }

        fun resume(id: String) {
            downloads.resume(id)
        }

        fun cancel(id: String) {
            engine.cancel(id)
        }

        fun remove(id: String) {
            engine.remove(id)
        }

        fun deleteFromDevice(task: DownloadTask) {
            viewModelScope.launch {
                val uri = task.savedUri
                val deleted = uri == null || deleter.delete(uri)
                if (deleted) {
                    uri?.let { history.deleteByUri(it) }
                    engine.remove(task.id)
                } else {
                    _message.value = R.string.error_delete_failed
                }
            }
        }

        fun consumeMessage() {
            _message.value = null
        }

        fun retry(id: String) {
            downloads.resume(id)
        }

        fun downloadAgain(task: DownloadTask) {
            downloads.enqueue(listOf(task.request))
        }

        fun clearFinished() {
            engine.clearFinished()
        }
    }
