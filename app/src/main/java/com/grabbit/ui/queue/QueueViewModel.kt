package com.grabbit.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.data.download.DownloadRepository
import com.grabbit.data.download.DownloadTask
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = downloads.tasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun pause(id: String) = downloads.pause(id)
    fun resume(id: String) = downloads.resume(id)
    fun cancel(id: String) = downloads.cancel(id)
    fun remove(id: String) = downloads.remove(id)
    fun retry(id: String) = downloads.retry(id)
    fun clearFinished() = downloads.clearFinished()
}
