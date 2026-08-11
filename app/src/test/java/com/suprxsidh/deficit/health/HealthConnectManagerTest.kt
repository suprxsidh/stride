package com.suprxsidh.deficit.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectManagerTest {
    @Test
    fun `required permissions cover all seven read types plus weight write`() {
        assertEquals(8, HealthConnectManager.REQUIRED_PERMISSIONS.size)
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_EXERCISE") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("READ_HEART_RATE") })
        assertTrue(HealthConnectManager.REQUIRED_PERMISSIONS.any { it.contains("WRITE_WEIGHT") })
    }
}
