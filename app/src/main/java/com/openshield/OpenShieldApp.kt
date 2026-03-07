package com.openshield

import android.app.Application
import androidx.work.Configuration
import com.openshield.worker.CommunityReportWorker
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point for Hilt and WorkManager setup.
 */
@HiltAndroidApp
class OpenShieldApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Schedule worker only if user has already granted consent.
        CommunityReportWorker.schedule(this)
    }
}
