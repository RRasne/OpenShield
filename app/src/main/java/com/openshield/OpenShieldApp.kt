package com.openshield

import android.app.Application
import androidx.work.Configuration
import com.openshield.data.BundledSpamImporter
import com.openshield.worker.CommunityReportWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OpenShieldApp : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            BundledSpamImporter.importIfNeeded(this@OpenShieldApp)
        }
        CommunityReportWorker.schedule(this)
    }
}
