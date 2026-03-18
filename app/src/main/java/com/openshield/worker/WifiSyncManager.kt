package com.openshield.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.openshield.data.repository.CommunityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val communityRepository: CommunityRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val isWifi      = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                              caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (isWifi && isValidated) {
                Log.d("WifiSync", "Wi-Fi bağlı — flush + sync başlıyor")
                scope.launch {
                    // 1. Kuyruktaki raporları hemen gönder
                    communityRepository.flushPendingReports()
                    // 2. 6 saatte bir community list güncelle
                    communityRepository.syncCommunityList()
                }
            }
        }
    }

    /** Wi-Fi'nin o an bağlı olup olmadığını döner */
    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps    = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun register() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d("WifiSync", "NetworkCallback kayıt edildi")
        } catch (e: Exception) {
            Log.w("WifiSync", "Kayıt başarısız: ${e.message}")
        }
    }

    fun unregister() {
        try { connectivityManager.unregisterNetworkCallback(networkCallback) }
        catch (_: Exception) { }
    }
}
