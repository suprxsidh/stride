package com.suprxsidh.stride.system

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryOptimizationTest {

    @Test
    fun `isIgnoringBatteryOptimizations is true when PowerManager reports it ignored`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadow = Shadows.shadowOf(powerManager) as ShadowPowerManager
        shadow.setIgnoringBatteryOptimizations(context.packageName, true)

        assertTrue(BatteryOptimization.isIgnoringBatteryOptimizations(context))
    }

    @Test
    fun `isIgnoringBatteryOptimizations is false when PowerManager reports it not ignored`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadow = Shadows.shadowOf(powerManager) as ShadowPowerManager
        shadow.setIgnoringBatteryOptimizations(context.packageName, false)

        assertFalse(BatteryOptimization.isIgnoringBatteryOptimizations(context))
    }

    @Test
    fun `batterySettingsIntent targets the general battery optimization settings screen`() {
        val intent = BatteryOptimization.batterySettingsIntent()

        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, intent.action)
    }
}
