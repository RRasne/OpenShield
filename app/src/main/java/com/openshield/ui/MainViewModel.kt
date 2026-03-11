package com.openshield.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openshield.data.db.BlockedLogEntity
import com.openshield.data.db.PendingReviewEntity
import com.openshield.data.db.SpamNumberEntity
import com.openshield.data.db.WhitelistEntity
import com.openshield.data.model.CommunityContributionSummary
import com.openshield.data.model.FeedbackVerdict
import com.openshield.data.model.SmsHistoryItem
import com.openshield.data.repository.SpamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val pendingReviews: StateFlow<List<PendingReviewEntity>> = repository.pendingReviews
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _smsHistory = MutableStateFlow<List<SmsHistoryItem>>(emptyList())
    val smsHistory: StateFlow<List<SmsHistoryItem>> = _smsHistory

    private val _communitySummary = MutableStateFlow(CommunityContributionSummary())
    val communitySummary: StateFlow<CommunityContributionSummary> = _communitySummary

    val smsFeedback: StateFlow<Map<Long, FeedbackVerdict>> = repository.userSmsFeedbackFlow()
        .map { rows ->
            rows.associate { row ->
                row.messageId to runCatching { FeedbackVerdict.valueOf(row.verdict) }
                    .getOrDefault(if (row.isSpam) FeedbackVerdict.SPAM else FeedbackVerdict.NOT_SPAM)
            }
        }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        refreshCommunitySummary()
    }

    fun refreshSmsHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val history = repository.loadSmsHistory()
            _smsHistory.update { history }
        }
        refreshCommunitySummary()
    }

    fun refreshCommunitySummary() = viewModelScope.launch {
        val summary = withContext(Dispatchers.IO) {
            repository.getCommunityContributionSummary()
        }
        _communitySummary.value = summary
    }

    fun markSms(messageId: Long, sender: String, verdict: FeedbackVerdict) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.markSmsAndQueueCommunityReport(messageId, sender, verdict)
        }
        refreshCommunitySummary()
    }

    fun markSenderMessages(messages: List<SmsHistoryItem>, verdict: FeedbackVerdict) = viewModelScope.launch {
        if (messages.isEmpty()) return@launch
        withContext(Dispatchers.IO) {
            messages.forEach { message ->
                repository.markSmsAndQueueCommunityReport(
                    messageId = message.id,
                    sender = message.sender,
                    verdict = verdict
                )
            }
        }
        refreshCommunitySummary()
    }

    fun decideSuspicious(item: PendingReviewEntity, isSpam: Boolean) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.decideSuspicious(item, isSpam)
        }
        refreshCommunitySummary()
    }

    fun addSpam(number: String, label: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.addSpam(number, label)
        }
    }

    fun removeSpam(number: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.removeSpam(number)
        }
    }

    fun addWhitelist(number: String, name: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.addWhitelist(number, name)
        }
    }

    fun removeWhitelist(number: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.removeWhitelist(number)
        }
    }

    fun clearFeedback() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.clearFeedback()
        }
        refreshCommunitySummary()
    }

    fun clearHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.clearHistory()
        }
        refreshCommunitySummary()
    }

    fun clearAllData() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            repository.clearHistory()
            repository.userSpamNumbers.first().forEach { repository.removeSpam(it.number) }
            repository.allWhitelist.first().forEach { repository.removeWhitelist(it.number) }
        }
        refreshCommunitySummary()
    }
}
