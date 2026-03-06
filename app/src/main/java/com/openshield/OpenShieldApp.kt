package com.openshield

import android.app.Application
import com.openshield.worker.CommunityReportWorker
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt’in kök bileşenini başlatan Application sınıfı.
 * AndroidManifest.xml içinde `android:name=".OpenShieldApp"` olarak tanımlanmıştır.
 */
@HiltAndroidApp
class OpenShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Kullanıcı daha önce onay verdiyse WorkManager'ı schedule et
        CommunityReportWorker.schedule(this)
    }
}
