package com.capstone.mobilemeasure.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.capstone.mobilemeasure.data.RssiSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 현재 휴대폰이 실제로 연결 중인 Wi-Fi만 측정한다.
 *
 * Android 12+에서는 ConnectivityManager.getNetworkCapabilities() 직접 호출이
 * 권한과 무관하게 항상 SSID/BSSID를 redact한다. 실제 값을 받으려면 NetworkCallback에
 * FLAG_INCLUDE_LOCATION_INFO를 지정해 등록해야 한다.
 */
class WifiScanner(
    private val context: Context,
    private val onDiagnostic: ((String) -> Unit)? = null,
) {
    private val appContext = context.applicationContext

    private val wifiManager: WifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val connectivityManager: ConnectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val locationManager: LocationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var lastDiagnostic: String? = null

    @Volatile
    private var latestInfo: WifiInfo? = null

    fun scanFlow(): Flow<List<RssiSample>> = callbackFlow {
        if (!preflightOk()) {
            while (isActive) {
                trySend(emptyList())
                delay(1000L)
            }
            return@callbackFlow
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = makeCallback()
        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: SecurityException) {
            report("registerNetworkCallback SecurityException: ${e.message}")
            return@callbackFlow
        }

        val pollJob = launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val info = latestInfo

                val sample = info?.let { extractSample(it, now) }
                Log.d("WifiScanner", "tick info=$info -> sample=$sample")

                if (sample == null) {
                    if (info == null) {
                        report("Wi-Fi 콜백 수신 대기 중 (Wi-Fi에 연결되어 있나요?)")
                    } else {
                        report("콜백 식별자 마스킹 — ${permissionStateString()}")
                    }
                    trySend(emptyList())
                } else {
                    trySend(listOf(sample))
                }

                delay(1000L)
            }
        }

        awaitClose {
            pollJob.cancel()
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
                // already unregistered
            }
            latestInfo = null
        }
    }

    private fun preflightOk(): Boolean {
        if (!wifiManager.isWifiEnabled) {
            report("Wi-Fi 비활성화: 설정에서 Wi-Fi를 켜주세요")
            return false
        }
        if (!isLocationServicesEnabled()) {
            report("위치 서비스 OFF: 단말 위치 서비스를 켜주세요")
            return false
        }
        if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            report("ACCESS_FINE_LOCATION 미허용")
            return false
        }
        return true
    }

    private fun extractSample(info: WifiInfo, timestampMs: Long): RssiSample? {
        val cleanedSsid = info.ssid?.removeSurrounding("\"")
        val rawBssid = info.bssid
        if (cleanedSsid.isNullOrBlank() || cleanedSsid == "<unknown ssid>") return null
        if (rawBssid.isNullOrBlank() || rawBssid == "02:00:00:00:00:00") return null
        return RssiSample(
            timestampMs = timestampMs,
            ssid = cleanedSsid,
            bssid = rawBssid,
            rssi = info.rssi,
            frequencyMhz = info.frequency,
        )
    }

    @Suppress("DEPRECATION")
    private fun makeCallback(): ConnectivityManager.NetworkCallback {
        val onCaps: (NetworkCapabilities) -> Unit = { caps ->
            val info = (caps.transportInfo as? WifiInfo) ?: legacyConnectionInfo()
            latestInfo = info
            Log.d(
                "WifiScanner",
                "cb caps: ssid=${info?.ssid} bssid=${info?.bssid} " +
                    "rssi=${info?.rssi} freq=${info?.frequency}"
            )
        }
        val onLost: () -> Unit = { latestInfo = null }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
            ) {
                override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) = onCaps(c)
                override fun onLost(n: Network) = onLost()
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) = onCaps(c)
                override fun onLost(n: Network) = onLost()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun legacyConnectionInfo(): WifiInfo? = try {
        wifiManager.connectionInfo
    } catch (_: Exception) {
        null
    }

    private fun permissionStateString(): String {
        val fine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.NEARBY_WIFI_DEVICES).toString()
        } else "n/a"
        val locOn = isLocationServicesEnabled()
        return "FINE=$fine COARSE=$coarse NEARBY=$nearby locationOn=$locOn"
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationServicesEnabled(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    } catch (_: Exception) {
        true
    }

    private fun report(reason: String) {
        Log.w("WifiScanner", "DIAG $reason")
        if (reason == lastDiagnostic) return
        lastDiagnostic = reason
        onDiagnostic?.invoke(reason)
    }
}
