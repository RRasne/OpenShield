package com.openshield.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openshield.data.db.BlockedLogEntity
import com.openshield.data.db.SpamNumberEntity
import com.openshield.data.db.WhitelistEntity
import com.openshield.data.repository.SpamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SpamRepository
) : ViewModel() {

    val spamNumbers: StateFlow<List<SpamNumberEntity>> = repository.userSpamNumbers
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val whitelist: StateFlow<List<WhitelistEntity>> = repository.allWhitelist
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val blockedLog: StateFlow<List<BlockedLogEntity>> = repository.recentBlocked
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addSpam(number: String, label: String) = viewModelScope.launch {
        repository.addSpam(number, label)
    }

    fun removeSpam(number: String) = viewModelScope.launch {
        repository.removeSpam(number)
    }

    fun addWhitelist(number: String, name: String) = viewModelScope.launch {
        repository.addWhitelist(number, name)
    }

    fun removeWhitelist(number: String) = viewModelScope.launch {
        repository.removeWhitelist(number)
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearHistory()
    }
}