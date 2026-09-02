package com.suprxsidh.stride.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectManagerTest {
    @Test
    fun `required permissions cover calories burned read plus weight read and write`() {
        assertEquals(3, HealthConnectManager.REQUIRED_PERMISSIONS.size)
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_TOTAL_CALORIES_BURNED") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_WEIGHT") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("WRITE_WEIGHT") })
    }

    @Test
    fun `no permission is requested for a record type the data source never reads`() {
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.none { it.contains("READ_STEPS") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.none { it.contains("READ_SPEED") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.none { it.contains("READ_EXERCISE") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.none { it.contains("READ_DISTANCE") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.none { it.contains("READ_HEART_RATE") })
    }
}
