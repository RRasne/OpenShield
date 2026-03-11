package com.openshield.util

import android.content.Context
import android.content.SharedPreferences
import com.openshield.worker.CommunityReportWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("openshield_consent", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COMMUNITY_CONSENT = "community_consent_given"
        private const val KEY_CONSENT_TIMESTAMP = "community_consent_timestamp"
        private const val KEY_LAST_SYNC = "last_community_sync"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"

        const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    val isCommunityConsentGiven: Boolean
        get() = prefs.getBoolean(
            KEY_COMMUNITY_CONSENT,
            CommunityReportWorker.hasConsent(context)
        )

    val isOnboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    val lastSyncTimestamp: Long
        get() = prefs.getLong(
            KEY_LAST_SYNC,
            CommunityReportWorker.getLastSyncAt(context) ?: 0L
        )

    fun isSyncDue(): Boolean {
        val elapsed = System.currentTimeMillis() - lastSyncTimestamp
        return elapsed >= MIN_SYNC_INTERVAL_MS
    }

    fun setCommunityConsent(given: Boolean) {
        prefs.edit()
            .putBoolean(KEY_COMMUNITY_CONSENT, given)
            .putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
            .apply()
        CommunityReportWorker.setConsent(context, given)
    }

    fun setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun updateLastSync() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
        CommunityReportWorker.setLastSyncAt(context, now)
    }

    fun revokeConsent() {
        prefs.edit()
            .putBoolean(KEY_COMMUNITY_CONSENT, false)
            .putLong(KEY_LAST_SYNC, 0L)
            .apply()
        CommunityReportWorker.setConsent(context, false)
    }
}
