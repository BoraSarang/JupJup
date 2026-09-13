package com.borasarang.common.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter

/**
 * 네트워크 유틸. services/mac + services/plan 바이트 동일분을 이관 (R2).
 * 시간 포맷(TimeUtils)은 주기 옵션·전용 포맷이 달라 각 모듈에 유지한다.
 */
object NetUtils {
    /**
     * 로컬 IP. 1) Wi-Fi 연결 정보 2) 네트워크 인터페이스 열거 순으로 탐색.
     * 모바일 데이터·핫스팟(AP) 상태에서도 사설 IP를 찾는다.
     */
    @Suppress("DEPRECATION")
    fun getLocalIp(context: Context): String? {
        wifiIp(context)?.let { return it }
        return interfaceIp()
    }

    private fun wifiIp(context: Context): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val ip = wm.connectionInfo?.ipAddress ?: return null
            if (ip == 0) return null
            Formatter.formatIpAddress(ip).takeIf { it != "0.0.0.0" }
        } catch (_: Exception) {
            null
        }
    }

    private fun interfaceIp(): String? {
        return try {
            val candidates = mutableListOf<String>()
            java.net.NetworkInterface.getNetworkInterfaces()?.asSequence()?.forEach { nic ->
                if (!nic.isUp || nic.isLoopback) return@forEach
                nic.inetAddresses.asSequence()
                    .filterIsInstance<java.net.Inet4Address>()
                    .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    .forEach { candidates += it.hostAddress ?: "" }
            }
            // 192.168.x (핫스팟/Wi-Fi) 우선
            candidates.firstOrNull { it.startsWith("192.168.") }
                ?: candidates.firstOrNull { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun isConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}
