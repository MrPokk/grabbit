package com.grabbit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grabbit.core.stateInUi
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppViewModel
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings> =
            settingsRepository.settings
                .stateInUi(viewModelScope, AppSettings())
    }
