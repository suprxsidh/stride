package com.suprxsidh.deficit.system

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization detection and settings deep-link.
 *
 * Deliberately uses `PowerManager.isIgnoringBatteryOptimizations` (read-only, no manifest
 * permission required) and links to the general
 * [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS] list rather than requesting
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` directly — the user grants it manually and the app
 * detects the result afterward, matching the existing Health Connect setup step's pattern.
 */
object BatteryOptimization {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
