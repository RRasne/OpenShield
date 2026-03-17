package com.openshield.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.openshield.util.ConsentManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi bağlantısını dinler.
 * Bağlantı geldiğinde:
 *   1. Kullanıcı community consent vermiş mi? → hayırsa dur
 *   2. Son sync'ten 6+ saat geçmiş mi? → hayırsa dur
 *   3. Her ikisi de evet → CommunityUpdateWorker'ı tetikle
 *
 * Kayıt: OpenShieldApp.onCreate()'de register edilir.
 * Çıkarma: Uygulama process'i sonlandığında otomatik iptal olur.
 * Bu yüzden her uygulama açılışında yeniden register edilmesi doğru davranış.
 */
@Singleton
class WifiSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consentManager: ConsentManager
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (isWifi && isConnected) {
                onWifiConnected()
            }
        }
    }

    fun register() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            // İzin hatası veya çift kayıt — sessizce geç
        }
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) { }
    }

    private fun onWifiConnected() {
        // Consent yok → hiçbir şey yapma
        if (!consentManager.isCommunityConsentGiven) return

        // Son sync'ten 6 saat geçmemiş → atlama
        if (!consentManager.isSyncDue()) return

        // Çalıştır
        CommunityUpdateWorker.runSync(context)
    }
}
