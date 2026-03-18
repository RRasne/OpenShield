package com.openshield.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openshield.data.db.PendingReviewEntity
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.SpamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val db: SpamDatabase
) : ViewModel() {

    private val repository = SpamRepository(db)

    // ─── Listeler ─────────────────────────────────────────────────────────────

    val spamNumbers = repository.allSpamNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whitelist = repository.allWhitelist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedLog = repository.recentBlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Bekleyen İncelemeler ─────────────────────────────────────────────────

    private val _pendingReviews = MutableStateFlow<List<PendingReviewEntity>>(emptyList())
    val pendingReviews: StateFlow<List<PendingReviewEntity>> = _pendingReviews.asStateFlow()

    init {
        loadPendingReviews()
    }

    private fun loadPendingReviews() {
        viewModelScope.launch {
            _pendingReviews.value = repository.getPendingReviews()
        }
    }

    fun resolveReview(entity: PendingReviewEntity, isSpam: Boolean) {
        viewModelScope.launch {
            repository.resolvePendingReview(entity, isSpam)
            // Listeyi güncelle
            _pendingReviews.value = _pendingReviews.value.filter { it.id != entity.id }
        }
    }

    // ─── Kara/Beyaz Liste İşlemleri ───────────────────────────────────────────

    fun addSpam(number: String, label: String) {
        viewModelScope.launch { repository.addSpam(number, label) }
    }

    fun removeSpam(number: String) {
        viewModelScope.launch { repository.removeSpam(number) }
    }

    fun addWhitelist(number: String, name: String) {
        viewModelScope.launch { repository.addWhitelist(number, name) }
    }

    fun removeWhitelist(number: String) {
        viewModelScope.launch { repository.removeWhitelist(number) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }
}
