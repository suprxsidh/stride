package com.suprxsidh.stride.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectManagerTest {
    @Test
    fun `required permissions cover the five read types actually used plus weight write`() {
        assertEquals(6, HealthConnectManager.REQUIRED_PERMISSIONS.size)
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_EXERCISE") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_HEART_RATE") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_DISTANCE") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_TOTAL_CALORIES_BURNED") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_WEIGHT") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("WRITE_WEIGHT") })
    }

    @Test
    fun `no permission is requested for a record type the data source never reads`() {
        // Every extra type is another checkbox the user can decline, and hasAllPermissions is a
        // containsAll check — one declined unused type would permanently block sync.
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.none { it.contains("READ_STEPS") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.none { it.contains("READ_SPEED") })
    }
}
