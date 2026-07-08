package com.schwanitz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.local.LanguagePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val languagePreferences: LanguagePreferences
) : ViewModel() {

    val currentLanguage: StateFlow<String> = languagePreferences.getLanguage()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LanguagePreferences.SYSTEM_DEFAULT)

    suspend fun setLanguage(code: String) {
        languagePreferences.setLanguage(code)
    }
}
