package com.suprxsidh.stride.ui.onboarding

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.health.HealthConnectManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {
    private lateinit var db: StrideDatabase
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries().build()
        viewModel = OnboardingViewModel(UserProfileRepository(db.userProfileDao(), db.weighInDao()))
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `submitting with blank fields sets an error and does not call onDone`() = runTest {
        var called = false
        viewModel.heightCm = ""
        viewModel.submit { called = true }

        assertEquals(false, called)
        assertNotNull(viewModel.error)
    }

    @Test
    fun `submitting valid data clears any error and calls onDone`() = runTest {
        var called = false
        viewModel.heightCm = "178"
        viewModel.weightKg = "80"
        viewModel.age = "26"
        viewModel.sex = Sex.MALE
        viewModel.submit { called = true }

        assertEquals(true, called)
        assertNull(viewModel.error)
    }

    @Test
    fun `blank goal weight leaves it to the repository default`() = runTest {
        viewModel.heightCm = "178"
        viewModel.weightKg = "80"
        viewModel.age = "26"
        viewModel.sex = Sex.MALE
        viewModel.goalWeightKg = ""
        viewModel.submit {}

        val profile = db.userProfileDao().get()
        assertEquals(70.0, profile!!.goalWeightKg, 0.001)
    }

    @Test
    fun `blank protein floor leaves it to the repository's 1_6x-weight default`() = runTest {
        viewModel.heightCm = "178"
        viewModel.weightKg = "80"
        viewModel.age = "26"
        viewModel.sex = Sex.MALE
        viewModel.proteinFloorG = ""
        viewModel.submit {}

        val profile = db.userProfileDao().get()
        assertEquals(128.0, profile!!.proteinFloorG, 0.001) // 1.6 * 80
    }

    @Test
    fun `explicit protein floor input is passed through to the stored profile`() = runTest {
        viewModel.heightCm = "178"
        viewModel.weightKg = "80"
        viewModel.age = "26"
        viewModel.sex = Sex.MALE
        viewModel.proteinFloorG = "150"
        viewModel.submit {}

        val profile = db.userProfileDao().get()
        assertEquals(150.0, profile!!.proteinFloorG, 0.001)
    }

    @Test
    fun `onHealthConnectPermissionsResult true when all required permissions granted`() = runTest {
        viewModel.onHealthConnectPermissionsResult(HealthConnectManager.REQUIRED_PERMISSIONS)
        assertTrue(viewModel.healthConnectPermissionsGranted.value)
    }

    @Test
    fun `onHealthConnectPermissionsResult false when some permissions are missing`() = runTest {
        viewModel.onHealthConnectPermissionsResult(setOf(HealthConnectManager.REQUIRED_PERMISSIONS.first()))
        assertFalse(viewModel.healthConnectPermissionsGranted.value)
    }
}
