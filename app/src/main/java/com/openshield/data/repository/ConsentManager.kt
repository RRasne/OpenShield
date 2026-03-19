package com.openshield.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("openshield_prefs", Context.MODE_PRIVATE)

    var communityConsent: Boolean
        get()      = prefs.getBoolean("community_consent", false)
        set(value) = prefs.edit().putBoolean("community_consent", value).apply()

    var lastSyncTime: Long
        get()      = prefs.getLong("last_sync_time", 0L)
        set(value) = prefs.edit().putLong("last_sync_time", value).apply()

    /** Onboarding tamamlandı mı? İlk açılışta false, tamamlanınca true. */
    var onboardingDone: Boolean
        get()      = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()
}