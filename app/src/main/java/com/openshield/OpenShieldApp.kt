package com.openshield

import android.app.Application
import com.openshield.worker.WifiSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OpenShieldApp : Application() {

    @Inject
    lateinit var wifiSyncManager: WifiSyncManager

    override fun onCreate() {
        super.onCreate()
        // Wi-Fi'ye her bağlanınca consent kontrolü yapar, uygunsa sync çalışır.
        wifiSyncManager.register()
    }

    override fun onTerminate() {
        super.onTerminate()
        wifiSyncManager.unregister()
    }
}
