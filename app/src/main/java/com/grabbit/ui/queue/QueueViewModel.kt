package com.grabbit.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.core.stateInUi
import com.grabbit.data.download.DownloadEngine
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.download.DownloadTask
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Owns the queue screen state and forwards task actions to the download layer. */
@HiltViewModel
class QueueViewModel
    @Inject
    constructor(
        private val engine: DownloadEngine,
        private val downloads: DownloadRepository,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
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
