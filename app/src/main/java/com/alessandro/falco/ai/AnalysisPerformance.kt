package com.alessandro.falco.ai

import android.content.Context
import android.os.Build
import android.os.PowerManager

class AnalysisPerformanceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("falco_analysis_performance", Context.MODE_PRIVATE)
    fun selected(): Int = prefs.getInt("parallelism", 1).takeIf { it in MODES } ?: 1
    fun save(value: Int) = prefs.edit().putInt("parallelism", value.takeIf { it in MODES } ?: 1).apply()
    fun automaticScaling(): Boolean = prefs.getBoolean("automatic_scaling", true)
    fun saveAutomaticScaling(enabled: Boolean) = prefs.edit().putBoolean("automatic_scaling", enabled).apply()
    fun effective(): Int {
        val selected = selected()
        if (!automaticScaling()) return selected
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return selected
        val thermal = context.getSystemService(Context.POWER_SERVICE).let { it as PowerManager }.currentThermalStatus
        return when {
            thermal >= PowerManager.THERMAL_STATUS_SEVERE -> 1
            thermal >= PowerManager.THERMAL_STATUS_MODERATE -> minOf(selected, 2)
            else -> selected
        }
    }
    companion object { val MODES = listOf(1, 2, 4, 6) }
}
