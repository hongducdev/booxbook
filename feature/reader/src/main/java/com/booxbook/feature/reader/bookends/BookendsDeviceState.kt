package com.booxbook.feature.reader.bookends

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Đọc trạng thái phần cứng cho các token thiết bị.
 *
 * Đọc theo yêu cầu thay vì đăng ký lắng nghe: các token này chỉ được vẽ lại mỗi 60 giây (cùng nhịp với
 * token đồng hồ), nên một `BroadcastReceiver` thường trú là chi phí vô ích — và trên e-ink thì không có
 * chuyện vẽ lại tức thời để cần tới.
 *
 * Mọi lời gọi hệ thống đều được bọc `runCatching`: đọc trạng thái pin hay độ sáng thất bại không được
 * phép làm mất cả overlay, chỉ nên làm token đó rỗng.
 */
@Singleton
class BookendsDeviceState @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun read(): BookendsDeviceReading {
        val battery = readBattery()
        val network = readNetwork()
        return BookendsDeviceReading(
            batteryPercent = battery.first,
            isCharging = battery.second,
            isWifiOn = network.first,
            isConnected = network.second,
            lightPercent = readBrightnessPercent()
        )
    }

    /** Trả (phần trăm pin, đang sạc). */
    private fun readBattery(): Pair<Int, Boolean> {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return 0 to false

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return percent to charging
    }

    /** Trả (Wi-Fi đang bật, có kết nối Internet). */
    private fun readNetwork(): Pair<Boolean, Boolean> {
        val manager = runCatching {
            context.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return false to true

        val capabilities = runCatching {
            manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
        }.getOrNull() ?: return false to true

        val onWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return onWifi to online
    }

    /**
     * Độ sáng màn hình quy về phần trăm.
     *
     * `SCREEN_BRIGHTNESS` là số thô 0..255 (không phải phần trăm) nên phải tự quy đổi. Trả `null` khi
     * thiết bị không cho đọc, để token rỗng thay vì hiện "0%".
     */
    private fun readBrightnessPercent(): Int? {
        val raw = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull() ?: return null

        if (raw < 0) return null
        return (raw * 100 / MAX_BRIGHTNESS).coerceIn(0, 100)
    }

    private companion object {
        const val MAX_BRIGHTNESS = 255
    }
}
