package com.openshield

import android.app.Application
import androidx.work.Configuration
import com.openshield.data.BundledSpamImporter
import com.openshield.worker.CommunityReportWorker
import com.openshield.worker.WifiSyncManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OpenShieldApp : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var wifiSyncManager: WifiSyncManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            BundledSpamImporter.importIfNeeded(this@OpenShieldApp)
        }
        CommunityReportWorker.schedule(this)
        wifiSyncManager.register()
    }

    override fun onTerminate() {
        super.onTerminate()
        wifiSyncManager.unregister()
    }
}
