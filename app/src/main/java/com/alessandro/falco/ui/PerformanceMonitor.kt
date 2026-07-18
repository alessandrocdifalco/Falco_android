package com.alessandro.falco.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.system.Os
import android.system.OsConstants
import android.os.SystemClock
import java.io.File

data class PerformanceState(val cpu: Int = 0, val ramMb: Int = 0, val batteryC: Float = 0f, val thermal: String = "Normale")

class PerformanceMonitor(private val context: Context) {
    private var lastTicks = ticks(); private var lastAt = SystemClock.elapsedRealtime()
    fun sample(): PerformanceState {
        val now = SystemClock.elapsedRealtime(); val currentTicks = ticks(); val elapsed = (now - lastAt).coerceAtLeast(1)
        val hz = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L).coerceAtLeast(1)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpu = (((currentTicks - lastTicks).coerceAtLeast(0) * 100_000.0) / hz / elapsed / cores).toInt().coerceIn(0, 100)
        lastTicks = currentTicks; lastAt = now
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.div(10f) ?: 0f
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus else 0
        val thermal = when { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "N/D"; thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "Severo"; thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> "Caldo"; thermalStatus >= PowerManager.THERMAL_STATUS_LIGHT -> "Tiepido"; else -> "Normale" }
        return PerformanceState(cpu, (Debug.getPss() / 1024L).coerceAtLeast(0L).toInt(), battery, thermal)
    }
    private fun ticks(): Long = runCatching {
        val afterName = File("/proc/self/stat").readText().substringAfterLast(") ").split(' ')
        afterName[11].toLong() + afterName[12].toLong()
    }.getOrDefault(0)
}
