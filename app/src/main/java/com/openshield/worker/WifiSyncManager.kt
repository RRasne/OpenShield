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
    private val cm    = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                Log.d("WifiSync", "Wi-Fi bağlı — flush + sync")
                scope.launch {
                    communityRepository.flushPendingReports() // kuyruktaki raporları gönder
                    communityRepository.syncCommunityList()   // 6 saatte bir liste güncelle
                }
            }
        }
    }

    /** O an Wi-Fi bağlı mı? SmsReceiver anında karar vermek için kullanır. */
    fun isWifiConnected(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun register() {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try { cm.registerNetworkCallback(req, callback) }
        catch (e: Exception) { Log.w("WifiSync", "kayıt başarısız: ${e.message}") }
    }

    fun unregister() {
        try { cm.unregisterNetworkCallback(callback) }
        catch (_: Exception) { }
    }
}
