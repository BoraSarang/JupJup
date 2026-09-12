package com.borasarang.planjupjup.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 네트워크/시간 포맷 유틸 */
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

object TimeUtils {
    const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    fun formatRelative(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0) return "없음"
        val diff = System.currentTimeMillis() - epochMillis
        if (diff < 0) return "방금 전"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 1) return "방금 전"
        if (minutes < 60) return "${minutes}분 전"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return "${hours}시간 전"
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days < 30) return "${days}일 전"
        return java.text.SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(java.util.Date(epochMillis))
    }

    fun formatPrice(won: Int?): String {
        if (won == null) return "-"
        return String.format(Locale.KOREA, "%,d원", won)
    }

    /** 수집 주기 선택지(분). WorkManager 최소 주기 15분 */
    val INTERVAL_OPTIONS_MINUTES = listOf(30, 60, 360, 720, 1440, 10080)
    const val MIN_INTERVAL_MINUTES = 15

    /** 오늘 00:00 (기기 로컬 타임존) */
    fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 다음 지정 시각(0~23시)까지 남은 밀리초 — 일일 요약 워커 첫 실행 지연용 */
    fun millisUntilNextHour(hour: Int): Long {
        val now = java.util.Calendar.getInstance()
        val next = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (!after(now)) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    /** 30→"30분마다", 60→"1시간마다", 1440→"24시간마다", 10080→"주 1회" */
    fun formatInterval(minutes: Int): String {
        return when {
            minutes <= 0 -> "미설정"
            minutes < 60 -> "${minutes}분마다"
            minutes == 10080 -> "주 1회"
            minutes == 1440 -> "24시간마다"
            minutes % 1440 == 0 -> "${minutes / 1440}일마다"
            minutes % 60 == 0 -> "${minutes / 60}시간마다"
            else -> "${minutes}분마다"
        }
    }
}
