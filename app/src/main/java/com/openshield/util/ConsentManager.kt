package com.openshield.util

import android.content.Context
import android.content.SharedPreferences
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
        private const val KEY_COMMUNITY_CONSENT  = "community_consent_given"
        private const val KEY_CONSENT_TIMESTAMP  = "community_consent_timestamp"
        private const val KEY_LAST_SYNC          = "last_community_sync"
        private const val KEY_ONBOARDING_DONE    = "onboarding_completed"

        const val MIN_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L  // 6 saat
    }

    /** Kullanıcı topluluk özelliğini onayladı mı? — okunabilir ve yazılabilir */
    var isCommunityConsentGiven: Boolean
        get() = prefs.getBoolean(KEY_COMMUNITY_CONSENT, false)
        set(value) {
            prefs.edit()
                .putBoolean(KEY_COMMUNITY_CONSENT, value)
                .putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }

    /** Onboarding tamamlandı mı? */
    val isOnboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    /** Son başarılı sync zamanı */
    val lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)

    fun isSyncDue(): Boolean =
        System.currentTimeMillis() - lastSyncTimestamp >= MIN_SYNC_INTERVAL_MS

    fun setCommunityConsent(given: Boolean) {
        isCommunityConsentGiven = given
    }

    fun setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun updateLastSync() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    fun revokeConsent() {
        prefs.edit()
            .putBoolean(KEY_COMMUNITY_CONSENT, false)
            .putLong(KEY_LAST_SYNC, 0L)
            .apply()
    }
}
