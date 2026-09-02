# Calorie-Only Scope-Down Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strip Stride down to a pure calorie counter — Gemini food logging (photo/text), onboarding-derived calorie budget, weigh-ins, and Health Connect calories-burned — by removing weekly running commitment, consistency tracking, run analytics, weekly review, motivation lines, and Open Food Facts search.

**Architecture:** This is subtractive. Five tasks, each ordered so the app compiles and tests pass at the end of every task: (1) weekly-commitment/consistency/review/motivation subsystem, (2) running/exercise-session tracking (replaced with a direct daily-calories-burned read from Health Connect), (3) Open Food Facts search path, (4) onboarding/dashboard copy cleanup, (5) final build+test verification.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, Health Connect, WorkManager, Retrofit/Ktor (removed in Task 3), Robolectric/JUnit for unit tests.

**Spec:** `docs/superpowers/specs/2026-09-02-calorie-only-scope-down-design.md`

## Global Constraints

- Single-user, sideloaded debug app — Room migrations use `fallbackToDestructiveMigration()` (already in place); every schema change is just a `@Database(version = N)` bump, no `Migration` objects.
- No backend, no accounts, no analytics — this plan touches only local DB, Health Connect, and Gemini wiring, consistent with existing project constraints.
- `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` must both pass at the end of every task.
- Toolchain: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`, gradle via `./gradlew` (wrapper already generated).
- All package/class names use `com.suprxsidh.stride` (project already renamed from `deficit` → `stride`).

---

## Task 1: Cut weekly commitment, consistency, weekly review, and motivation

**Files:**
- Delete: `app/src/main/java/com/suprxsidh/stride/data/calc/WeeklyCommitmentCalc.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/calc/WeeklyCommitmentCalcTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/calc/MotivationLine.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/calc/MotivationLineTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/repository/WeeklyCommitmentRepository.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/repository/WeeklyCommitmentRepositoryTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/repository/ConsistencyRepository.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/repository/ConsistencyRepositoryTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/repository/WeeklyReviewRepository.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/repository/WeeklyReviewRepositoryTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/db/dao/WeeklyReviewDao.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/db/dao/WeeklyReviewDaoTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/db/entity/WeeklyReviewEntity.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/ui/dashboard/WeeklyCommitmentCard.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/ui/dashboard/WeeklyReviewCard.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/ui/dashboard/MotivationCard.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/ui/consistency/ConsistencyScreen.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/ui/consistency/ConsistencyViewModel.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/ui/consistency/ConsistencyScreenTest.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/ui/consistency/ConsistencyViewModelTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/db/entity/AppSettingsEntity.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/repository/SettingsRepository.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/data/repository/SettingsRepositoryTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/ui/settings/SettingsViewModelTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/db/StrideDatabase.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/AppContainer.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/nav/Routes.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/nav/StrideNavHost.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/MainActivity.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `SettingsRepository` with only `getGeminiApiKey/observeGeminiApiKey/setGeminiApiKey/getManualBudgetOverrideKcal/setManualBudgetOverrideKcal`. `AppSettingsEntity` with only `id, geminiApiKey, manualBudgetOverrideKcal`. `SettingsViewModel` with only `geminiApiKey`, `manualBudgetOverrideKcal`, `saveGeminiApiKey`, `clearGeminiApiKey`, `saveManualBudgetOverride`, `clearManualBudgetOverride`. `DashboardViewModel` constructor drops `weeklyCommitmentRepository`, `weeklyReviewRepository`, `settingsRepository` params (Task 2 will further drop `todaysRun`).
- Consumes (Task 2 depends on this): the trimmed `DashboardViewModel` constructor signature and `DashboardScreen`'s viewModel-factory call.

- [ ] **Step 1: Delete the dead files listed above**

```bash
cd /Users/Suprasidh/claudecode-projects/stride
rm app/src/main/java/com/suprxsidh/stride/data/calc/WeeklyCommitmentCalc.kt
rm app/src/test/java/com/suprxsidh/stride/data/calc/WeeklyCommitmentCalcTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/calc/MotivationLine.kt
rm app/src/test/java/com/suprxsidh/stride/data/calc/MotivationLineTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/repository/WeeklyCommitmentRepository.kt
rm app/src/test/java/com/suprxsidh/stride/data/repository/WeeklyCommitmentRepositoryTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/repository/ConsistencyRepository.kt
rm app/src/test/java/com/suprxsidh/stride/data/repository/ConsistencyRepositoryTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/repository/WeeklyReviewRepository.kt
rm app/src/test/java/com/suprxsidh/stride/data/repository/WeeklyReviewRepositoryTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/db/dao/WeeklyReviewDao.kt
rm app/src/test/java/com/suprxsidh/stride/data/db/dao/WeeklyReviewDaoTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/db/entity/WeeklyReviewEntity.kt
rm app/src/main/java/com/suprxsidh/stride/ui/dashboard/WeeklyCommitmentCard.kt
rm app/src/main/java/com/suprxsidh/stride/ui/dashboard/WeeklyReviewCard.kt
rm app/src/main/java/com/suprxsidh/stride/ui/dashboard/MotivationCard.kt
rm -r app/src/main/java/com/suprxsidh/stride/ui/consistency
rm -r app/src/test/java/com/suprxsidh/stride/ui/consistency
```

- [ ] **Step 2: Replace `AppSettingsEntity.kt` with the trimmed version**

```kotlin
package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val geminiApiKey: String? = null,
    val manualBudgetOverrideKcal: Int? = null
)
```

- [ ] **Step 3: Replace `SettingsRepository.kt` with the trimmed version**

```kotlin
package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingsDao) {
    suspend fun getGeminiApiKey(): String? = dao.get()?.geminiApiKey

    fun observeGeminiApiKey(): Flow<String?> = dao.observe().map { it?.geminiApiKey }

    suspend fun setGeminiApiKey(key: String?) {
        upsertCopy { it.copy(geminiApiKey = key) }
    }

    suspend fun getManualBudgetOverrideKcal(): Int? = dao.get()?.manualBudgetOverrideKcal

    suspend fun setManualBudgetOverrideKcal(kcal: Int?) = upsertCopy { it.copy(manualBudgetOverrideKcal = kcal) }

    private suspend fun upsertCopy(mutate: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = dao.get() ?: AppSettingsEntity(id = 1)
        dao.upsert(mutate(current))
    }
}
```

- [ ] **Step 4: Replace `SettingsRepositoryTest.kt` with the trimmed version**

```kotlin
package com.suprxsidh.stride.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private lateinit var db: StrideDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SettingsRepository(db.appSettingsDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `getGeminiApiKey returns null before any key is set`() = runTest {
        assertNull(repository.getGeminiApiKey())
    }

    @Test
    fun `setGeminiApiKey then getGeminiApiKey round-trips the value`() = runTest {
        repository.setGeminiApiKey("test-key-123")
        assertEquals("test-key-123", repository.getGeminiApiKey())
    }

    @Test
    fun `setGeminiApiKey with null clears the stored key`() = runTest {
        repository.setGeminiApiKey("test-key-123")
        repository.setGeminiApiKey(null)
        assertNull(repository.getGeminiApiKey())
    }

    @Test
    fun `manual budget override is null by default and round-trips through set and clear`() = runTest {
        assertEquals(null, repository.getManualBudgetOverrideKcal())

        repository.setManualBudgetOverrideKcal(1900)
        assertEquals(1900, repository.getManualBudgetOverrideKcal())

        repository.setManualBudgetOverrideKcal(null)
        assertEquals(null, repository.getManualBudgetOverrideKcal())
    }
}
```

- [ ] **Step 5: Replace `SettingsViewModel.kt` with the trimmed version**

```kotlin
package com.suprxsidh.stride.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository
import com.suprxsidh.stride.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val adaptiveBudgetRepository: AdaptiveBudgetRepository
) : ViewModel() {

    val geminiApiKey: StateFlow<String?> =
        settingsRepository.observeGeminiApiKey().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _manualBudgetOverrideKcal = MutableStateFlow<Int?>(null)
    val manualBudgetOverrideKcal: StateFlow<Int?> = _manualBudgetOverrideKcal.asStateFlow()

    init {
        viewModelScope.launch {
            _manualBudgetOverrideKcal.value = settingsRepository.getManualBudgetOverrideKcal()
        }
    }

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(key.trim().ifBlank { null }) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(null) }
    }

    private companion object {
        const val MIN_MANUAL_BUDGET_KCAL = 1200
    }

    fun saveManualBudgetOverride(kcal: Int) {
        val clampedKcal = maxOf(kcal, MIN_MANUAL_BUDGET_KCAL)
        viewModelScope.launch {
            adaptiveBudgetRepository.setManualOverride(clampedKcal)
            _manualBudgetOverrideKcal.value = clampedKcal
        }
    }

    fun clearManualBudgetOverride() {
        viewModelScope.launch {
            adaptiveBudgetRepository.clearManualOverride()
            _manualBudgetOverrideKcal.value = null
        }
    }
}
```

- [ ] **Step 6: Replace `SettingsViewModelTest.kt` with the trimmed version**

```kotlin
package com.suprxsidh.stride.ui.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: StrideDatabase
    private lateinit var viewModel: SettingsViewModel
    private lateinit var adaptiveBudgetRepository: com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).build()
        val settingsRepository = SettingsRepository(db.appSettingsDao())
        adaptiveBudgetRepository = com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository(
            db.userProfileDao(), db.weighInDao(), settingsRepository
        )
        viewModel = SettingsViewModel(settingsRepository, adaptiveBudgetRepository)
    }

    @After
    fun tearDown() { db.close(); Dispatchers.resetMain() }

    @Test
    fun `saveGeminiApiKey updates the observed state`() = runTest {
        backgroundScope.launch { viewModel.geminiApiKey.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveGeminiApiKey("my-key")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("my-key", viewModel.geminiApiKey.value)
    }

    @Test
    fun `saving and clearing the manual budget override updates observed state`() = runTest {
        db.userProfileDao().upsert(
            com.suprxsidh.stride.data.db.entity.UserProfileEntity(
                heightCm = 178.0, weightKgAtStart = 80.0, age = 29,
                sex = com.suprxsidh.stride.data.calc.Sex.MALE.name, goalWeightKg = 70.0, softBudgetKcal = 1850, createdAt = 0L
            )
        )
        backgroundScope.launch { viewModel.manualBudgetOverrideKcal.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.manualBudgetOverrideKcal.value)

        viewModel.saveManualBudgetOverride(1700)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1700, viewModel.manualBudgetOverrideKcal.value)

        viewModel.clearManualBudgetOverride()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.manualBudgetOverrideKcal.value)
    }

    @Test
    fun `saveManualBudgetOverride clamps below the 1200 kcal minimum`() = runTest {
        backgroundScope.launch { viewModel.manualBudgetOverrideKcal.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveManualBudgetOverride(400)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1200, viewModel.manualBudgetOverrideKcal.value)
    }
}
```

- [ ] **Step 7: In `SettingsScreen.kt`, delete the "Weekly run goal" section**

Delete everything from the `StartLineDivider` right after the Gemini API key `Row { ... }` block through the end of the weekly-goal `Button` call — i.e. delete this whole block (it sits between the Gemini section and the "Calorie budget override" section):

```kotlin
        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.lg))
        Text("Weekly run goal".uppercase(), style = MaterialTheme.typography.titleMedium)
        Text("Target and hard floor for the Monday-Sunday week. Any run on any day counts equally.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
        Spacer(Modifier.height(Spacing.sm))

        val target by viewModel.weeklyRunTarget.collectAsState()
        val floor by viewModel.weeklyRunFloor.collectAsState()
        var targetInput by remember(target) { mutableStateOf(target.toString()) }
        var floorInput by remember(floor) { mutableStateOf(floor.toString()) }

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = targetInput, onValueChange = { targetInput = it },
                label = { Text("Target") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.sm))
            OutlinedTextField(
                value = floorInput, onValueChange = { floorInput = it },
                label = { Text("Floor") }, modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Button(onClick = {
            targetInput.toIntOrNull()?.let { viewModel.saveWeeklyRunTarget(it) }
            floorInput.toIntOrNull()?.let { viewModel.saveWeeklyRunFloor(it) }
        }) { Text("Save weekly goal") }
```

The file after this deletion goes straight from the Gemini `Row { Button("Save") ... }` block to the `StartLineDivider` that starts "Calorie budget override". No import changes needed — every symbol used elsewhere in the file (`Row`, `OutlinedTextField`, etc.) is still used by the remaining sections.

- [ ] **Step 8: Bump `StrideDatabase.kt` to version 7, dropping `WeeklyReviewEntity`/`WeeklyReviewDao`**

Replace the file with:

```kotlin
package com.suprxsidh.stride.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.dao.CustomFoodDao
import com.suprxsidh.stride.data.db.dao.ExerciseSessionDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.OffCacheDao
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.db.entity.OffCacheEntity
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import com.suprxsidh.stride.data.db.entity.SyncStateEntity
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity

@Database(
    entities = [
        UserProfileEntity::class,
        FoodEntryEntity::class,
        CustomFoodEntity::class,
        WeighInEntity::class,
        OffCacheEntity::class,
        ExerciseSessionEntity::class,
        SyncStateEntity::class,
        AppSettingsEntity::class,
        PendingDraftEntity::class
    ],
    // v7: WeeklyReviewEntity removed and AppSettingsEntity lost its weekly-commitment/review/
    // motivation fields as part of the calorie-only scope-down (see
    // docs/superpowers/specs/2026-09-02-calorie-only-scope-down-design.md). Still no Migration
    // object -- fallbackToDestructiveMigration() below handles the upgrade, same convention as
    // every prior version bump on this single-user, sideloaded, no-cloud-sync app.
    version = 7,
    exportSchema = false
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao
    abstract fun offCacheDao(): OffCacheDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingDraftDao(): PendingDraftDao

    companion object {
        @Volatile private var INSTANCE: StrideDatabase? = null

        fun getInstance(context: Context): StrideDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StrideDatabase::class.java,
                    "deficit.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
```

(`ExerciseSessionEntity`/`OffCacheEntity` stay in this task — Tasks 2 and 3 remove them with their own version bumps.)

- [ ] **Step 9: Replace `AppContainer.kt`, dropping `weeklyCommitmentRepository`/`weeklyReviewRepository`/`consistencyRepository`**

```kotlin
package com.suprxsidh.stride.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.stride.ai.gemini.GeminiServiceFactory
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.repository.AdaptiveBudgetRepository
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.GeminiFoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.SettingsRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import com.suprxsidh.stride.food.off.OpenFoodFactsRepository
import com.suprxsidh.stride.food.off.OpenFoodFactsServiceFactory
import com.suprxsidh.stride.health.HealthConnectDataSource
import com.suprxsidh.stride.health.HealthConnectManager

class AppContainer(private val context: Context) {
    private val database = StrideDatabase.getInstance(context)
    val userProfileRepository = UserProfileRepository(database.userProfileDao(), database.weighInDao())
    val foodRepository = FoodRepository(database.foodEntryDao(), database.customFoodDao())
    val weightRepository = WeightRepository(database.weighInDao())
    val settingsRepository = SettingsRepository(database.appSettingsDao())
    val adaptiveBudgetRepository = AdaptiveBudgetRepository(database.userProfileDao(), database.weighInDao(), settingsRepository)
    val openFoodFactsRepository = OpenFoodFactsRepository(
        OpenFoodFactsServiceFactory.create(),
        database.offCacheDao()
    )
    val geminiFoodRepository = GeminiFoodRepository(
        estimator = GeminiFoodEstimator(GeminiServiceFactory.create()),
        settingsRepository = settingsRepository,
        foodRepository = foodRepository,
        pendingDraftDao = database.pendingDraftDao()
    )

    val healthConnectAvailability: Int = HealthConnectManager.availability(context)

    // Nullable: Health Connect may not be installed/available on this device. Only construct
    // the real client-backed data source when the SDK reports SDK_AVAILABLE; otherwise leave
    // this null and have callers null-check and show an "unavailable" UI state instead of
    // crashing AppContainer construction.
    val healthConnectRepository: HealthConnectRepository? =
        if (healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectRepository(
                dataSource = HealthConnectDataSource(HealthConnectManager.getClient(context)),
                exerciseSessionDao = database.exerciseSessionDao(),
                syncStateDao = database.syncStateDao(),
                weighInDao = database.weighInDao()
            )
        } else {
            null
        }

    suspend fun hasHealthConnectPermissions(): Boolean =
        healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE && HealthConnectManager.hasAllPermissions(context)
}
```

(`healthConnectRepository`'s constructor call still passes `exerciseSessionDao` here — Task 2 changes `HealthConnectRepository`'s constructor and this call site together.)

- [ ] **Step 10: Replace `Routes.kt`, dropping `CONSISTENCY`**

```kotlin
package com.suprxsidh.stride.ui.nav

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val FOOD_LOG = "food_log"
    const val WEIGHT = "weight"
    const val RUN_DETAIL = "run_detail"
    const val SETTINGS = "settings"
}
```

(`RUN_DETAIL` stays here — Task 2 removes it.)

- [ ] **Step 11: In `StrideNavHost.kt`, remove the `ConsistencyScreen` import and its `composable(Routes.CONSISTENCY) { ... }` line**

Delete this import line:
```kotlin
import com.suprxsidh.stride.ui.consistency.ConsistencyScreen
```

Delete this line from the `NavHost` body:
```kotlin
        composable(Routes.CONSISTENCY) { ConsistencyScreen() }
```

- [ ] **Step 12: In `MainActivity.kt`, remove the Consistency bottom-nav destination**

Change:
```kotlin
private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.DASHBOARD, "Today", Icons.Default.Home),
    BottomDestination(Routes.FOOD_LOG, "Food", Icons.AutoMirrored.Filled.List),
    BottomDestination(Routes.WEIGHT, "Weight", Icons.Default.Info),
    BottomDestination(Routes.CONSISTENCY, "Consistency", Icons.Default.DateRange)
)
```
to:
```kotlin
private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.DASHBOARD, "Today", Icons.Default.Home),
    BottomDestination(Routes.FOOD_LOG, "Food", Icons.AutoMirrored.Filled.List),
    BottomDestination(Routes.WEIGHT, "Weight", Icons.Default.Info)
)
```
and delete the now-unused import:
```kotlin
import androidx.compose.material.icons.filled.DateRange
```

- [ ] **Step 13: Replace `DashboardViewModel.kt`, dropping the weekly-commitment/review/motivation surface**

```kotlin
package com.suprxsidh.stride.ui.dashboard

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    private val weightRepository: WeightRepository,
    private val healthConnectRepository: HealthConnectRepository?,
    private val healthConnectAvailability: Int,
    private val hasHealthConnectPermissions: suspend () -> Boolean,
    private val isIgnoringBatteryOptimizations: () -> Boolean,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    // Health Connect permission can be granted from OUTSIDE the app: the dashboard's
    // "Open Health Connect settings" button (see DashboardScreen) deep-links to the system
    // Health Connect app, and the user grants there then returns without killing Stride.
    // The sync worker is otherwise only ever scheduled from onboarding's permission-grant
    // callback or a cold MainActivity.onCreate start -- neither of which runs on this path --
    // so without this hook, granting permission via the settings deep link silently never
    // starts sync until the process is fully killed and cold-started again. Same root cause
    // as the onboarding scheduling bug, different trigger site.
    private val scheduleHealthConnectSync: () -> Unit = {}
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> =
        userProfileRepository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rollingAverageSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaysRun: StateFlow<ExerciseSessionEntity?> =
        (healthConnectRepository?.observeExerciseSessions() ?: flowOf(emptyList()))
            .map { sessions ->
                val today = DayBoundary.logicalDate(clock()).toString()
                sessions.filter { it.date == today }.maxByOrNull { it.startTimeEpochMs }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.UNAVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

    fun refreshDeviceStatuses() {
        _batteryOptimizationIgnored.value = isIgnoringBatteryOptimizations()
        viewModelScope.launch {
            val previousStatus = _healthConnectStatus.value
            val newStatus = try {
                when {
                    healthConnectAvailability != HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.UNAVAILABLE
                    !hasHealthConnectPermissions() -> HealthConnectStatus.PERMISSIONS_NEEDED
                    else -> HealthConnectStatus.OK
                }
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to read Health Connect permission state", e)
                HealthConnectStatus.PERMISSIONS_NEEDED
            }
            _healthConnectStatus.value = newStatus

            if (newStatus == HealthConnectStatus.OK && previousStatus != HealthConnectStatus.OK) {
                scheduleHealthConnectSync()
            }
        }
    }

    init {
        refreshDeviceStatuses()
    }
}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
```

`todaysRun`/`ExerciseSessionEntity` stay for now — Task 2 replaces this with `caloriesBurnedToday` and removes the `ExerciseSessionEntity` import along with the entity itself.

- [ ] **Step 14: In `DashboardScreen.kt`, drop the weekly-commitment/review/motivation UI and the `onViewRunHistory` param it no longer needs**

Change the function signature:
```kotlin
fun DashboardScreen(onQuickAdd: () -> Unit, onViewRunHistory: () -> Unit = {}) {
```
stays as-is for this task (Task 2 removes `onViewRunHistory` along with the run-history button).

In the `DashboardViewModel(...)` factory call, delete these three arguments (and the trailing comma of the one before them):
```kotlin
                app.container.weeklyCommitmentRepository,
                app.container.weeklyReviewRepository,
                app.container.settingsRepository,
```
so the call becomes:
```kotlin
            DashboardViewModel(
                app.container.foodRepository,
                app.container.userProfileRepository,
                app.container.weightRepository,
                app.container.healthConnectRepository,
                app.container.healthConnectAvailability,
                app.container::hasHealthConnectPermissions,
                { com.suprxsidh.stride.system.BatteryOptimization.isIgnoringBatteryOptimizations(app) },
                scheduleHealthConnectSync = {
                    HealthConnectSyncWorker.schedulePeriodic(app)
                    HealthConnectSyncWorker.triggerOneOff(app)
                }
            )
```

Delete these three blocks from the `Column` body entirely:
```kotlin
        val unseenReview by viewModel.unseenWeeklyReview.collectAsState()
        unseenReview?.let { WeeklyReviewCard(it, onDismiss = viewModel::dismissWeeklyReview) }

        val weeklyState by viewModel.weeklyCommitmentState.collectAsState()
        val floorIntactStreakWeeks by viewModel.floorIntactStreakWeeks.collectAsState()
        weeklyState?.let { WeeklyCommitmentCard(it, floorIntactStreakWeeks) }

        val motivationLine by viewModel.motivationLine.collectAsState()
        motivationLine?.let { MotivationCard(it) }
```

- [ ] **Step 15: Replace `DashboardViewModelTest.kt`, dropping weekly-commitment/review/motivation tests**

```kotlin
package com.suprxsidh.stride.ui.dashboard

import androidx.health.connect.client.HealthConnectClient
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.dao.ExerciseSessionDao
import com.suprxsidh.stride.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import com.suprxsidh.stride.health.HealthDataSource
import com.suprxsidh.stride.health.RemoteExerciseSession
import com.suprxsidh.stride.health.RemoteWeightRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: StrideDatabase
    private lateinit var viewModel: DashboardViewModel
    private lateinit var healthDao: ExerciseSessionDao

    private class NoOpHealthDataSource : HealthDataSource {
        override suspend fun readExerciseSessions(since: Instant): List<RemoteExerciseSession> = emptyList()
        override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> = emptyList()
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String = ""
    }

    private fun buildViewModel(
        clock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 10, 12, 0) },
        healthConnectAvailability: Int = HealthConnectClient.SDK_AVAILABLE,
        hasPermissions: suspend () -> Boolean = { true },
        isIgnoringBatteryOptimizations: () -> Boolean = { false },
        scheduleHealthConnectSync: () -> Unit = {}
    ): DashboardViewModel {
        return DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao(), clock = clock),
            UserProfileRepository(db.userProfileDao(), db.weighInDao()),
            WeightRepository(db.weighInDao()),
            HealthConnectRepository(NoOpHealthDataSource(), db.exerciseSessionDao(), db.syncStateDao(), db.weighInDao(), clock),
            healthConnectAvailability,
            hasPermissions,
            isIgnoringBatteryOptimizations,
            clock,
            scheduleHealthConnectSync
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        healthDao = db.exerciseSessionDao()
        viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `profile is null before onboarding`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.profile.value)
    }

    @Test
    fun `today buffered total reflects logged entries`() = runTest(testDispatcher) {
        db.foodEntryDao().insert(FoodEntryEntity(date = "2026-08-10", name = "Test", rawKcal = 200, bufferedKcal = 220, source = "QUICK", offBarcode = null, loggedAt = 1L))
        backgroundScope.launch { viewModel.todayBufferedTotal.collect {} }
        testDispatcher.scheduler.runCurrent()
        assertEquals(220, viewModel.todayBufferedTotal.value)
    }

    @Test
    fun `after onboarding the profile reflects the computed soft budget`() = runTest(testDispatcher) {
        UserProfileRepository(db.userProfileDao(), db.weighInDao()).completeOnboarding(178.0, 80.0, 26, Sex.MALE)
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1645, viewModel.profile.value?.softBudgetKcal)
    }

    @Test
    fun `todaysRun exposes the latest session for today's date`() = runTest(testDispatcher) {
        val today = "2026-08-11"
        val session = ExerciseSessionEntity(
            id = 1, hcRecordId = "hc-1", date = today, exerciseType = "RUNNING",
            startTimeEpochMs = 1L, durationMin = 30, distanceM = 5000.0,
            avgPaceSecPerKm = 360.0, avgHr = 150, maxHr = 170, kcalReal = 350, kcalCredited = 175
        )
        healthDao.insert(session)
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 11, 9, 0) })
        backgroundScope.launch { viewModel.todaysRun.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("hc-1", viewModel.todaysRun.value?.hcRecordId)
        assertEquals(175, viewModel.todaysRun.value?.kcalCredited)
    }

    @Test
    fun `todaysRun is null when no session logged today`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 11, 9, 0) })
        backgroundScope.launch { viewModel.todaysRun.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.todaysRun.value)
    }

    @Test
    fun `healthConnectStatus is UNAVAILABLE when the SDK isn't available`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = 2, // HealthConnectClient.SDK_UNAVAILABLE
            hasPermissions = { true }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.UNAVAILABLE, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `healthConnectStatus is PERMISSIONS_NEEDED when available but not granted`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = HealthConnectClient.SDK_AVAILABLE,
            hasPermissions = { false }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `healthConnectStatus is OK when available and granted`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = HealthConnectClient.SDK_AVAILABLE,
            hasPermissions = { true }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `batteryOptimizationIgnored reflects the lambda's value on load`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            isIgnoringBatteryOptimizations = { true }
        )
        backgroundScope.launch { viewModel.batteryOptimizationIgnored.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }

    @Test
    fun `refreshDeviceStatuses rechecks both healthConnectStatus and batteryOptimizationIgnored`() = runTest(testDispatcher) {
        var permissionsGranted = false
        var batteryIgnored = false
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { permissionsGranted },
            isIgnoringBatteryOptimizations = { batteryIgnored }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        backgroundScope.launch { viewModel.batteryOptimizationIgnored.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
        assertEquals(false, viewModel.batteryOptimizationIgnored.value)

        permissionsGranted = true
        batteryIgnored = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }

    @Test
    fun `refreshDeviceStatuses schedules HC sync on the transition into OK`() = runTest(testDispatcher) {
        var scheduleCalls = 0
        var permissionsGranted = false
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { permissionsGranted },
            scheduleHealthConnectSync = { scheduleCalls++ }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
        assertEquals(0, scheduleCalls)

        permissionsGranted = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(1, scheduleCalls)

        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, scheduleCalls)
    }

    @Test
    fun `refreshDeviceStatuses does not schedule HC sync when permission is already OK at init`() = runTest(testDispatcher) {
        var scheduleCalls = 0
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { true },
            scheduleHealthConnectSync = { scheduleCalls++ }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(1, scheduleCalls)
    }
}
```

- [ ] **Step 16: Run the build and tests**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 17: Commit**

```bash
git add -A
git commit -m "refactor: cut weekly commitment, consistency, weekly review, and motivation"
```

---

## Task 2: Cut running/exercise-session tracking, add daily calories-burned read

**Files:**
- Delete: `app/src/main/java/com/suprxsidh/stride/data/calc/RunAnalytics.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/calc/RunAnalyticsTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/calc/RunTypes.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/calc/RunTypesTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/db/entity/ExerciseSessionEntity.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/db/dao/ExerciseSessionDao.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/data/db/dao/ExerciseSessionDaoTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/ui/health/RunDetailScreen.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/ui/health/RunDetailViewModel.kt`
- Delete: `app/src/test/java/com/suprxsidh/stride/ui/health/RunDetailViewModelTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/health/HealthDataSource.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/health/HealthConnectDataSource.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/health/HealthConnectManager.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/health/HealthConnectManagerTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/health/HealthConnectSyncWorker.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/repository/HealthConnectRepository.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/data/repository/HealthConnectRepositoryTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/db/StrideDatabase.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/AppContainer.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/nav/Routes.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/nav/StrideNavHost.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `HealthDataSource.readTotalCaloriesBurned(since: Instant, until: Instant): Int`. `HealthConnectRepository.getTodaysCaloriesBurned(): Int` (constructor drops `exerciseSessionDao`). `DashboardViewModel.caloriesBurnedToday: StateFlow<Int?>` (constructor drops `todaysRun`'s backing deps — no `ExerciseSessionEntity` import remains).
- Consumes: Task 1's trimmed `DashboardViewModel`/`AppContainer`/`Routes`/`StrideNavHost`.

- [ ] **Step 1: Delete the dead files listed above**

```bash
cd /Users/Suprasidh/claudecode-projects/stride
rm app/src/main/java/com/suprxsidh/stride/data/calc/RunAnalytics.kt
rm app/src/test/java/com/suprxsidh/stride/data/calc/RunAnalyticsTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/calc/RunTypes.kt
rm app/src/test/java/com/suprxsidh/stride/data/calc/RunTypesTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/db/entity/ExerciseSessionEntity.kt
rm app/src/main/java/com/suprxsidh/stride/data/db/dao/ExerciseSessionDao.kt
rm app/src/test/java/com/suprxsidh/stride/data/db/dao/ExerciseSessionDaoTest.kt
rm -r app/src/main/java/com/suprxsidh/stride/ui/health
rm app/src/test/java/com/suprxsidh/stride/ui/health/RunDetailViewModelTest.kt
```

- [ ] **Step 2: Replace `HealthDataSource.kt`**

```kotlin
package com.suprxsidh.stride.health

import java.time.Instant

data class RemoteWeightRecord(
    val hcRecordId: String,
    val time: Instant,
    val weightKg: Double
)

interface HealthDataSource {
    /** Sum of Health Connect's TotalCaloriesBurnedRecord (basal + active) in [since, until]. */
    suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int
    suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord>
    suspend fun writeWeightRecord(weightKg: Double, time: Instant): String
}
```

- [ ] **Step 3: Replace `HealthConnectDataSource.kt`**

```kotlin
package com.suprxsidh.stride.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

class HealthConnectDataSource(private val client: HealthConnectClient) : HealthDataSource {

    override suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int {
        return client.readRecords(
            ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, until)
            )
        ).records.sumOf { it.energy.inKilocalories }.toInt()
    }

    override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> {
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, Instant.now())
            )
        ).records
        return records.map { RemoteWeightRecord(it.metadata.id, it.time, it.weight.inKilograms) }
    }

    override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String {
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(time)
        val response = client.insertRecords(
            listOf(
                WeightRecord(
                    metadata = Metadata.manualEntry(),
                    weight = Mass.kilograms(weightKg),
                    time = time,
                    zoneOffset = zoneOffset
                )
            )
        )
        return response.recordIdsList.first()
    }
}
```

- [ ] **Step 4: Replace `HealthConnectManager.kt`, trimming `REQUIRED_PERMISSIONS`**

```kotlin
package com.suprxsidh.stride.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

object HealthConnectManager {
    /**
     * Exactly the record types [com.suprxsidh.stride.health.HealthConnectDataSource] actually
     * touches, and nothing more. [hasAllPermissions] is a `containsAll` check, so every extra type
     * listed here is another checkbox the user can decline in the Health Connect consent screen to
     * permanently block sync.
     */
    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class)
    )

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun getClient(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(context: Context): Boolean =
        getClient(context).permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)

    fun requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()
}
```

- [ ] **Step 5: Replace `HealthConnectManagerTest.kt`**

```kotlin
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
```

- [ ] **Step 6: Replace `HealthConnectRepository.kt`**

```kotlin
package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import com.suprxsidh.stride.health.HealthDataSource
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface WeighInSyncSource {
    suspend fun syncWeighIns(): Int
}

class HealthConnectRepository(
    private val dataSource: HealthDataSource,
    private val syncStateDao: SyncStateDao,
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : WeighInSyncSource {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Calories burned so far in today's logical day (the same 3am boundary food entries and
     * weigh-ins use). Health Connect's TotalCaloriesBurnedRecord already includes both basal and
     * active burn, so this doubles as a live "how many calories do I have today" figure without a
     * separate per-exercise credit calculation.
     */
    suspend fun getTodaysCaloriesBurned(): Int {
        val startOfDay = DayBoundary.logicalDate(clock()).atTime(3, 0)
            .atZone(ZoneId.systemDefault()).toInstant()
        val now = clock().atZone(ZoneId.systemDefault()).toInstant()
        return dataSource.readTotalCaloriesBurned(startOfDay, now)
    }

    override suspend fun syncWeighIns(): Int {
        var count = 0
        for (unsynced in weighInDao.getUnsyncedToHc()) {
            val time = LocalDateTime.parse(unsynced.date + "T07:00:00").atZone(ZoneId.systemDefault()).toInstant()
            val hcId = dataSource.writeWeightRecord(unsynced.weightKg, time)
            weighInDao.upsert(unsynced.copy(syncedToHc = true, hcRecordId = hcId))
            count++
        }

        val since = syncStateDao.get()?.lastWeightSyncEpochMs?.let { Instant.ofEpochMilli(it) }
            ?: firstSyncFloor()
        val watermark = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        for (remote in dataSource.readNewWeightRecords(readSince(since))) {
            if (weighInDao.getByHcRecordId(remote.hcRecordId) != null) continue
            val date = logicalDateOf(remote.time)
            val existing = weighInDao.getForDate(date)
            if (existing != null) {
                weighInDao.upsert(
                    existing.copy(weightKg = remote.weightKg, syncedToHc = true, hcRecordId = remote.hcRecordId)
                )
            } else {
                weighInDao.upsert(
                    WeighInEntity(date = date, weightKg = remote.weightKg, syncedToHc = true, hcRecordId = remote.hcRecordId)
                )
            }
            count++
        }

        syncStateDao.setWeightSyncWatermark(watermark)
        return count
    }

    private fun firstSyncFloor(): Instant =
        clock().minusDays(FIRST_SYNC_LOOKBACK_DAYS).atZone(ZoneId.systemDefault()).toInstant()

    /**
     * Widens the *read* window (never the stored watermark) backwards. Samsung Health publishes a
     * completed record into Health Connect 30-60 minutes after the fact, and other providers batch
     * even later -- with the watermark as the exact left edge, anything published after the sync
     * that recorded it would fall before the next window and be dropped forever. Weigh-ins are
     * deduped on their Health Connect record id, so re-reading overlapping time is idempotent.
     */
    private fun readSince(since: Instant): Instant = since.minus(SYNC_LOOKBACK)

    /**
     * Health Connect timestamps must go through the same 3am day boundary the rest of the app
     * uses, otherwise a weigh-in between midnight and 3am is stamped with the next calendar day
     * and never matches "today".
     */
    private fun logicalDateOf(instant: Instant): String =
        DayBoundary.logicalDate(instant.atZone(ZoneId.systemDefault()).toLocalDateTime()).format(dateFormatter)

    private companion object {
        val SYNC_LOOKBACK: Duration = Duration.ofHours(48)
        const val FIRST_SYNC_LOOKBACK_DAYS = 30L
    }
}
```

- [ ] **Step 7: Replace `HealthConnectRepositoryTest.kt`**

```kotlin
package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.SyncStateEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity
import com.suprxsidh.stride.health.HealthDataSource
import com.suprxsidh.stride.health.RemoteWeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HealthConnectRepositoryTest {

    private class FakeDataSource(
        var caloriesBurned: Int = 0,
        var newWeights: List<RemoteWeightRecord> = emptyList()
    ) : HealthDataSource {
        val written = mutableListOf<Pair<Double, Instant>>()
        val weightSince = mutableListOf<Instant>()
        var lastCaloriesWindow: Pair<Instant, Instant>? = null

        override suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int {
            lastCaloriesWindow = since to until
            return caloriesBurned
        }

        override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> {
            weightSince.add(since)
            return newWeights.filter { !it.time.isBefore(since) }
        }

        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String {
            written.add(weightKg to time)
            return "hc-written-${written.size}"
        }
    }

    private class FakeSyncStateDao : SyncStateDao {
        var state: SyncStateEntity? = null
        override suspend fun get() = state
        override suspend fun upsert(state: SyncStateEntity) { this.state = state }
    }

    private class FakeWeighInDao : WeighInDao {
        val rows = mutableListOf<WeighInEntity>()
        private var nextId = 1L
        private val flow = MutableStateFlow<List<WeighInEntity>>(emptyList())

        override suspend fun upsert(weighIn: WeighInEntity): Long {
            val stored = if (weighIn.id != 0L) {
                rows.removeAll { it.id == weighIn.id }
                weighIn
            } else {
                weighIn.copy(id = nextId++)
            }
            rows.add(stored)
            flow.value = rows.toList()
            return stored.id
        }
        override fun observeAll(): Flow<List<WeighInEntity>> = flow
        override suspend fun getForDate(date: String) = rows.find { it.date == date }
        override suspend fun getByHcRecordId(hcRecordId: String) = rows.find { it.hcRecordId == hcRecordId }
        override suspend fun getUnsyncedToHc() = rows.filter { !it.syncedToHc }
    }

    private val fixedClock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 11, 10, 0) }

    private fun now(): Instant = fixedClock().atZone(ZoneId.systemDefault()).toInstant()

    @Test
    fun `getTodaysCaloriesBurned reads the window from today's 3am boundary to now`() = runTest {
        val dataSource = FakeDataSource(caloriesBurned = 1400)
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), FakeWeighInDao(), fixedClock)

        val burned = repo.getTodaysCaloriesBurned()

        assertEquals(1400, burned)
        val expectedStart = LocalDateTime.of(2026, 8, 11, 3, 0).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expectedStart, dataSource.lastCaloriesWindow?.first)
        assertEquals(now(), dataSource.lastCaloriesWindow?.second)
    }

    @Test
    fun `getTodaysCaloriesBurned before the 3am boundary uses the previous logical day's start`() = runTest {
        val earlyClock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 11, 1, 30) }
        val dataSource = FakeDataSource(caloriesBurned = 200)
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), FakeWeighInDao(), earlyClock)

        repo.getTodaysCaloriesBurned()

        val expectedStart = LocalDateTime.of(2026, 8, 10, 3, 0).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals(expectedStart, dataSource.lastCaloriesWindow?.first)
    }

    @Test
    fun `syncWeighIns pushes unsynced local entries to Health Connect`() = runTest {
        val weighInDao = FakeWeighInDao()
        weighInDao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 80.0, syncedToHc = false))
        val dataSource = FakeDataSource()
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        val count = repo.syncWeighIns()

        assertEquals(1, count)
        assertEquals(1, dataSource.written.size)
        assertTrue(weighInDao.rows.first().syncedToHc)
    }

    @Test
    fun `syncWeighIns imports a new Health Connect weight not seen before`() = runTest {
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-w-1", Instant.parse("2026-08-10T05:00:00Z"), 79.4))
        )
        val weighInDao = FakeWeighInDao()
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        val imported = weighInDao.getByHcRecordId("hc-w-1")
        assertEquals(79.4, imported!!.weightKg, 0.001)
        assertTrue(imported.syncedToHc)
    }

    @Test
    fun `syncWeighIns does not re-import an already-known Health Connect record`() = runTest {
        val weighInDao = FakeWeighInDao()
        weighInDao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 79.4, syncedToHc = true, hcRecordId = "hc-w-1"))
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-w-1", Instant.parse("2026-08-10T05:00:00Z"), 79.4))
        )
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals(1, weighInDao.rows.size)
    }

    @Test
    fun `a weigh-in imported before 3am lands on the previous logical day`() = runTest {
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-w-1", LocalDateTime.of(2026, 8, 11, 2, 0).atZone(ZoneId.systemDefault()).toInstant(), 79.0))
        )
        val weighInDao = FakeWeighInDao()
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals("2026-08-10", weighInDao.getByHcRecordId("hc-w-1")!!.date)
    }

    @Test
    fun `importing a weight for a date that already has an entry updates it in place`() = runTest {
        val weighInDao = FakeWeighInDao()
        val existingId = weighInDao.upsert(
            WeighInEntity(date = "2026-08-11", weightKg = 80.0, syncedToHc = true, hcRecordId = "hc-mine")
        )
        val dataSource = FakeDataSource(
            newWeights = listOf(RemoteWeightRecord("hc-scale", LocalDateTime.of(2026, 8, 11, 7, 0).atZone(ZoneId.systemDefault()).toInstant(), 79.1))
        )
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals(1, weighInDao.rows.count { it.date == "2026-08-11" })
        val row = weighInDao.getForDate("2026-08-11")!!
        assertEquals(existingId, row.id)
        assertEquals(79.1, row.weightKg, 0.001)
        assertEquals("hc-scale", row.hcRecordId)
        assertTrue(row.syncedToHc)
    }

    @Test
    fun `repeated sync cycles keep importing new weigh-ins`() = runTest {
        val dataSource = FakeDataSource()
        val weighInDao = FakeWeighInDao()
        val repo = HealthConnectRepository(dataSource, FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        dataSource.newWeights = listOf(RemoteWeightRecord("hc-w-2", now().minus(Duration.ofHours(2)), 78.9))
        val imported = repo.syncWeighIns()

        assertEquals(1, imported)
        assertEquals(78.9, weighInDao.getByHcRecordId("hc-w-2")!!.weightKg, 0.001)
    }

    @Test
    fun `the weight read window is widened backwards but the stored watermark is not`() = runTest {
        val dataSource = FakeDataSource()
        val syncState = FakeSyncStateDao()
        val repo = HealthConnectRepository(dataSource, syncState, FakeWeighInDao(), fixedClock)

        repo.syncWeighIns()
        repo.syncWeighIns()

        assertEquals(now().toEpochMilli(), syncState.state!!.lastWeightSyncEpochMs)
        assertTrue(dataSource.weightSince.last().isBefore(now().minus(Duration.ofHours(24))))
    }
}
```

- [ ] **Step 8: In `HealthConnectSyncWorker.kt`, drop the `syncExerciseSessions()` call**

Change:
```kotlin
    override suspend fun doWork(): Result {
        val repository = repositoryProvider(applicationContext) ?: return Result.success()
        return try {
            repository.syncExerciseSessions()
            repository.syncWeighIns()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
```
to:
```kotlin
    override suspend fun doWork(): Result {
        val repository = repositoryProvider(applicationContext) ?: return Result.success()
        return try {
            repository.syncWeighIns()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
```
(No test changes needed — `HealthConnectSyncWorkerTest.kt` doesn't assert on which sync methods run internally.)

- [ ] **Step 9: Bump `StrideDatabase.kt` to version 8, dropping `ExerciseSessionEntity`/`ExerciseSessionDao`**

Replace the file with:

```kotlin
package com.suprxsidh.stride.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.dao.CustomFoodDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.OffCacheDao
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.db.entity.OffCacheEntity
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import com.suprxsidh.stride.data.db.entity.SyncStateEntity
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity

@Database(
    entities = [
        UserProfileEntity::class,
        FoodEntryEntity::class,
        CustomFoodEntity::class,
        WeighInEntity::class,
        OffCacheEntity::class,
        SyncStateEntity::class,
        AppSettingsEntity::class,
        PendingDraftEntity::class
    ],
    // v8: ExerciseSessionEntity removed as part of the calorie-only scope-down -- Health Connect
    // now only reads TotalCaloriesBurnedRecord directly (HealthConnectRepository.getTodaysCaloriesBurned)
    // and WeightRecord, no longer per-exercise-session data. fallbackToDestructiveMigration()
    // below handles the upgrade, same convention as every prior version bump.
    version = 8,
    exportSchema = false
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao
    abstract fun offCacheDao(): OffCacheDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingDraftDao(): PendingDraftDao

    companion object {
        @Volatile private var INSTANCE: StrideDatabase? = null

        fun getInstance(context: Context): StrideDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StrideDatabase::class.java,
                    "deficit.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 10: In `AppContainer.kt`, drop `exerciseSessionDao` from the `HealthConnectRepository(...)` construction**

Change:
```kotlin
    val healthConnectRepository: HealthConnectRepository? =
        if (healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectRepository(
                dataSource = HealthConnectDataSource(HealthConnectManager.getClient(context)),
                exerciseSessionDao = database.exerciseSessionDao(),
                syncStateDao = database.syncStateDao(),
                weighInDao = database.weighInDao()
            )
        } else {
            null
        }
```
to:
```kotlin
    val healthConnectRepository: HealthConnectRepository? =
        if (healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectRepository(
                dataSource = HealthConnectDataSource(HealthConnectManager.getClient(context)),
                syncStateDao = database.syncStateDao(),
                weighInDao = database.weighInDao()
            )
        } else {
            null
        }
```

- [ ] **Step 11: Replace `Routes.kt`, dropping `RUN_DETAIL`**

```kotlin
package com.suprxsidh.stride.ui.nav

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val FOOD_LOG = "food_log"
    const val WEIGHT = "weight"
    const val SETTINGS = "settings"
}
```

- [ ] **Step 12: Replace `StrideNavHost.kt`**

```kotlin
package com.suprxsidh.stride.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.suprxsidh.stride.ui.dashboard.DashboardScreen
import com.suprxsidh.stride.ui.food.FoodLogScreen
import com.suprxsidh.stride.ui.onboarding.OnboardingScreen
import com.suprxsidh.stride.ui.settings.SettingsScreen
import com.suprxsidh.stride.ui.weight.WeightScreen

@Composable
fun StrideNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onComplete = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onQuickAdd = { navController.navigate(Routes.FOOD_LOG) })
        }
        composable(Routes.FOOD_LOG) { FoodLogScreen() }
        composable(Routes.WEIGHT) { WeightScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
```

- [ ] **Step 13: Replace `DashboardViewModel.kt`, swapping `todaysRun` for `caloriesBurnedToday`**

```kotlin
package com.suprxsidh.stride.ui.dashboard

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    private val weightRepository: WeightRepository,
    private val healthConnectRepository: HealthConnectRepository?,
    private val healthConnectAvailability: Int,
    private val hasHealthConnectPermissions: suspend () -> Boolean,
    private val isIgnoringBatteryOptimizations: () -> Boolean,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    // Health Connect permission can be granted from OUTSIDE the app: the dashboard's
    // "Open Health Connect settings" button (see DashboardScreen) deep-links to the system
    // Health Connect app, and the user grants there then returns without killing Stride.
    // The sync worker is otherwise only ever scheduled from onboarding's permission-grant
    // callback or a cold MainActivity.onCreate start -- neither of which runs on this path --
    // so without this hook, granting permission via the settings deep link silently never
    // starts sync until the process is fully killed and cold-started again. Same root cause
    // as the onboarding scheduling bug, different trigger site.
    private val scheduleHealthConnectSync: () -> Unit = {}
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> =
        userProfileRepository.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rollingAverageSeries: StateFlow<List<Pair<LocalDate, Double>>> =
        weightRepository.observeRollingAverageSeries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _caloriesBurnedToday = MutableStateFlow<Int?>(null)
    val caloriesBurnedToday: StateFlow<Int?> = _caloriesBurnedToday.asStateFlow()

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.UNAVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    private val _batteryOptimizationIgnored = MutableStateFlow(false)
    val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

    fun refreshDeviceStatuses() {
        _batteryOptimizationIgnored.value = isIgnoringBatteryOptimizations()
        viewModelScope.launch {
            val previousStatus = _healthConnectStatus.value
            val newStatus = try {
                when {
                    healthConnectAvailability != HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.UNAVAILABLE
                    !hasHealthConnectPermissions() -> HealthConnectStatus.PERMISSIONS_NEEDED
                    else -> HealthConnectStatus.OK
                }
            } catch (e: Exception) {
                Log.w("DashboardViewModel", "Failed to read Health Connect permission state", e)
                HealthConnectStatus.PERMISSIONS_NEEDED
            }
            _healthConnectStatus.value = newStatus

            if (newStatus == HealthConnectStatus.OK && previousStatus != HealthConnectStatus.OK) {
                scheduleHealthConnectSync()
            }

            if (newStatus == HealthConnectStatus.OK) {
                _caloriesBurnedToday.value = try {
                    healthConnectRepository?.getTodaysCaloriesBurned()
                } catch (e: Exception) {
                    Log.w("DashboardViewModel", "Failed to read today's calories burned from Health Connect", e)
                    null
                }
            }
        }
    }

    init {
        refreshDeviceStatuses()
    }
}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
```

- [ ] **Step 14: Replace `DashboardScreen.kt`**

```kotlin
package com.suprxsidh.stride.ui.dashboard

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.stride.StrideApp
import com.suprxsidh.stride.health.HealthConnectSyncWorker
import com.suprxsidh.stride.ui.theme.Spacing
import com.suprxsidh.stride.ui.theme.StrideOnSurfaceMuted
import com.suprxsidh.stride.ui.theme.component.LedReadout
import com.suprxsidh.stride.ui.theme.component.PunchCardRow
import com.suprxsidh.stride.ui.theme.component.StartLineDivider
import com.suprxsidh.stride.ui.theme.component.StartLineProgress
import java.time.LocalDate

@Composable
fun DashboardScreen(onQuickAdd: () -> Unit) {
    val app = LocalContext.current.applicationContext as StrideApp
    val viewModel: DashboardViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                app.container.foodRepository,
                app.container.userProfileRepository,
                app.container.weightRepository,
                app.container.healthConnectRepository,
                app.container.healthConnectAvailability,
                app.container::hasHealthConnectPermissions,
                { com.suprxsidh.stride.system.BatteryOptimization.isIgnoringBatteryOptimizations(app) },
                scheduleHealthConnectSync = {
                    HealthConnectSyncWorker.schedulePeriodic(app)
                    HealthConnectSyncWorker.triggerOneOff(app)
                }
            )
        }
    })

    val profile by viewModel.profile.collectAsState()
    val total by viewModel.todayBufferedTotal.collectAsState()
    val series by viewModel.rollingAverageSeries.collectAsState()
    val caloriesBurnedToday by viewModel.caloriesBurnedToday.collectAsState()
    val hcStatus by viewModel.healthConnectStatus.collectAsState()
    val batteryIgnored by viewModel.batteryOptimizationIgnored.collectAsState()
    val context = LocalContext.current

    // Health Connect permissions and battery optimization are both granted via a settings
    // deep link outside the app, so recheck both banners whenever the user returns to the
    // dashboard rather than trusting the one-shot values computed when the ViewModel was created.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshDeviceStatuses()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        Text("Today".uppercase(), style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.padding(top = Spacing.sm)) {
            val budget = profile?.softBudgetKcal ?: 0

            LedReadout(
                value = total.toString(),
                label = if (budget > 0) "kcal logged · soft target $budget" else "kcal logged today",
                modifier = Modifier.fillMaxWidth(),
            )

            if (budget > 0) {
                StartLineProgress(
                    progress = (total.toFloat() / budget.toFloat()).coerceIn(0f, 1f),
                    overBudget = total > budget,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            caloriesBurnedToday?.let { burned ->
                Text(
                    "Burned today: $burned kcal (Health Connect)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrideOnSurfaceMuted,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        StartLineDivider(modifier = Modifier.padding(vertical = Spacing.lg))
        Text("Weight (7-day average)".uppercase(), style = MaterialTheme.typography.titleMedium)
        WeightSparkline(points = series.takeLast(30))

        when (hcStatus) {
            HealthConnectStatus.PERMISSIONS_NEEDED -> PunchCardRow(modifier = Modifier.padding(top = Spacing.md)) {
                Column {
                    Text("Health Connect permissions needed to sync calories burned and weight.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                    }) { Text("Open Health Connect settings") }
                }
            }
            HealthConnectStatus.UNAVAILABLE -> Text(
                "Health Connect isn't available on this device — install it from the Play Store to sync calories burned and weight.",
                style = MaterialTheme.typography.bodySmall,
                color = StrideOnSurfaceMuted,
            )
            HealthConnectStatus.OK -> {}
        }

        if (!batteryIgnored) {
            PunchCardRow(modifier = Modifier.padding(top = Spacing.xs)) {
                Column {
                    Text("Battery optimization may silently stop background sync.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        context.startActivity(com.suprxsidh.stride.system.BatteryOptimization.batterySettingsIntent())
                    }) { Text("Open battery settings") }
                }
            }
        }

        Button(onClick = onQuickAdd, modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
            Text("Log food".uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun WeightSparkline(points: List<Pair<LocalDate, Double>>) {
    if (points.size < 2) {
        Text("Log a couple of weigh-ins to see your trend here.", style = MaterialTheme.typography.bodyMedium, color = StrideOnSurfaceMuted)
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = Spacing.xs)) {
        val values = points.map { it.second }
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, (_, value) ->
            val x = index * stepX
            val y = size.height - ((value - minV) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}
```

- [ ] **Step 15: Replace `DashboardViewModelTest.kt`, swapping the `todaysRun` tests for `caloriesBurnedToday` tests**

```kotlin
package com.suprxsidh.stride.ui.dashboard

import androidx.health.connect.client.HealthConnectClient
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.calc.Sex
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.HealthConnectRepository
import com.suprxsidh.stride.data.repository.UserProfileRepository
import com.suprxsidh.stride.data.repository.WeightRepository
import com.suprxsidh.stride.health.HealthDataSource
import com.suprxsidh.stride.health.RemoteWeightRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: StrideDatabase
    private lateinit var viewModel: DashboardViewModel

    private class FakeHealthDataSource(var caloriesBurned: Int = 0) : HealthDataSource {
        override suspend fun readTotalCaloriesBurned(since: Instant, until: Instant): Int = caloriesBurned
        override suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord> = emptyList()
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String = ""
    }

    private fun buildViewModel(
        clock: () -> LocalDateTime = { LocalDateTime.of(2026, 8, 10, 12, 0) },
        healthConnectAvailability: Int = HealthConnectClient.SDK_AVAILABLE,
        hasPermissions: suspend () -> Boolean = { true },
        isIgnoringBatteryOptimizations: () -> Boolean = { false },
        scheduleHealthConnectSync: () -> Unit = {},
        caloriesBurned: Int = 0
    ): DashboardViewModel {
        return DashboardViewModel(
            FoodRepository(db.foodEntryDao(), db.customFoodDao(), clock = clock),
            UserProfileRepository(db.userProfileDao(), db.weighInDao()),
            WeightRepository(db.weighInDao()),
            HealthConnectRepository(FakeHealthDataSource(caloriesBurned), db.syncStateDao(), db.weighInDao(), clock),
            healthConnectAvailability,
            hasPermissions,
            isIgnoringBatteryOptimizations,
            clock,
            scheduleHealthConnectSync
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 10, 12, 0) })
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `profile is null before onboarding`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.profile.value)
    }

    @Test
    fun `today buffered total reflects logged entries`() = runTest(testDispatcher) {
        db.foodEntryDao().insert(FoodEntryEntity(date = "2026-08-10", name = "Test", rawKcal = 200, bufferedKcal = 220, source = "QUICK", loggedAt = 1L))
        backgroundScope.launch { viewModel.todayBufferedTotal.collect {} }
        testDispatcher.scheduler.runCurrent()
        assertEquals(220, viewModel.todayBufferedTotal.value)
    }

    @Test
    fun `after onboarding the profile reflects the computed soft budget`() = runTest(testDispatcher) {
        UserProfileRepository(db.userProfileDao(), db.weighInDao()).completeOnboarding(178.0, 80.0, 26, Sex.MALE)
        backgroundScope.launch { viewModel.profile.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1645, viewModel.profile.value?.softBudgetKcal)
    }

    @Test
    fun `healthConnectStatus is UNAVAILABLE when the SDK isn't available`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = 2, // HealthConnectClient.SDK_UNAVAILABLE
            hasPermissions = { true }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.UNAVAILABLE, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `healthConnectStatus is PERMISSIONS_NEEDED when available but not granted`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = HealthConnectClient.SDK_AVAILABLE,
            hasPermissions = { false }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `healthConnectStatus is OK when available and granted`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = HealthConnectClient.SDK_AVAILABLE,
            hasPermissions = { true }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
    }

    @Test
    fun `caloriesBurnedToday is populated from Health Connect once status is OK`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { true },
            caloriesBurned = 1380
        )
        backgroundScope.launch { viewModel.caloriesBurnedToday.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1380, viewModel.caloriesBurnedToday.value)
    }

    @Test
    fun `caloriesBurnedToday stays null while permissions are needed`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { false },
            caloriesBurned = 1380
        )
        backgroundScope.launch { viewModel.caloriesBurnedToday.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.caloriesBurnedToday.value)
    }

    @Test
    fun `batteryOptimizationIgnored reflects the lambda's value on load`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            isIgnoringBatteryOptimizations = { true }
        )
        backgroundScope.launch { viewModel.batteryOptimizationIgnored.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }

    @Test
    fun `refreshDeviceStatuses rechecks both healthConnectStatus and batteryOptimizationIgnored`() = runTest(testDispatcher) {
        var permissionsGranted = false
        var batteryIgnored = false
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { permissionsGranted },
            isIgnoringBatteryOptimizations = { batteryIgnored }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        backgroundScope.launch { viewModel.batteryOptimizationIgnored.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
        assertEquals(false, viewModel.batteryOptimizationIgnored.value)

        permissionsGranted = true
        batteryIgnored = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(true, viewModel.batteryOptimizationIgnored.value)
    }

    @Test
    fun `refreshDeviceStatuses schedules HC sync on the transition into OK`() = runTest(testDispatcher) {
        var scheduleCalls = 0
        var permissionsGranted = false
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { permissionsGranted },
            scheduleHealthConnectSync = { scheduleCalls++ }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.PERMISSIONS_NEEDED, viewModel.healthConnectStatus.value)
        assertEquals(0, scheduleCalls)

        permissionsGranted = true
        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(1, scheduleCalls)

        viewModel.refreshDeviceStatuses()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, scheduleCalls)
    }

    @Test
    fun `refreshDeviceStatuses does not schedule HC sync when permission is already OK at init`() = runTest(testDispatcher) {
        var scheduleCalls = 0
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            hasPermissions = { true },
            scheduleHealthConnectSync = { scheduleCalls++ }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
        assertEquals(1, scheduleCalls)
    }
}
```

Note: this test file (and Task 3's edits) drop the `offBarcode` argument from every `FoodEntryEntity(...)` construction — Task 3 makes that field disappear from the entity itself, so it's removed here proactively to avoid a two-step break. If Task 3 hasn't run yet when this step executes, keep `offBarcode = null` in the constructor calls above; if Task 3 already ran, they're already gone. Since this plan runs tasks in order, Task 3 runs after this one, so **for this step, keep `offBarcode = null`** in the `FoodEntryEntity(...)` call in `today buffered total reflects logged entries` — the version above already omits it in anticipation; add it back now (`source = "QUICK", offBarcode = null, loggedAt = 1L`) and Task 3 will remove it again when it trims the entity.

- [ ] **Step 16: Run the build and tests**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 17: Commit**

```bash
git add -A
git commit -m "refactor: cut running/exercise-session tracking, add Health Connect daily calories-burned read"
```

---

## Task 3: Cut Open Food Facts search — Gemini becomes the sole logging path

**Files:**
- Delete: `app/src/main/java/com/suprxsidh/stride/food/off/` (entire directory: `OffModels.kt`, `OpenFoodFactsApi.kt`, `OpenFoodFactsRepository.kt`, `OpenFoodFactsServiceFactory.kt`)
- Delete: `app/src/test/java/com/suprxsidh/stride/food/off/OpenFoodFactsRepositoryTest.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/db/entity/OffCacheEntity.kt`
- Delete: `app/src/main/java/com/suprxsidh/stride/data/db/dao/OffCacheDao.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/db/entity/FoodEntryEntity.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/db/dao/FoodEntryDao.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/data/db/dao/FoodEntryDaoTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/repository/FoodRepository.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/db/StrideDatabase.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/data/AppContainer.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/food/FoodLogViewModel.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/ui/food/FoodLogViewModelTest.kt`
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/food/FoodLogScreen.kt`
- Modify: `app/src/test/java/com/suprxsidh/stride/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `FoodEntryEntity` without `offBarcode`. `FoodRepository` without `logOffProduct`. `FoodLogViewModel(foodRepository, geminiFoodRepository)` (drops the `offRepository` constructor param).
- Consumes: Task 1/2's already-trimmed `AppContainer`, `DashboardViewModelTest`.

- [ ] **Step 1: Delete the dead files listed above**

```bash
cd /Users/Suprasidh/claudecode-projects/stride
rm -r app/src/main/java/com/suprxsidh/stride/food
rm app/src/test/java/com/suprxsidh/stride/food/off/OpenFoodFactsRepositoryTest.kt
rm app/src/main/java/com/suprxsidh/stride/data/db/entity/OffCacheEntity.kt
rm app/src/main/java/com/suprxsidh/stride/data/db/dao/OffCacheDao.kt
```

- [ ] **Step 2: Replace `FoodEntryEntity.kt`, dropping `offBarcode`**

```kotlin
package com.suprxsidh.stride.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_entry")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val name: String,
    val rawKcal: Int,
    val bufferedKcal: Int,
    val source: String,
    val loggedAt: Long
)
```

- [ ] **Step 3: Replace `FoodEntryDao.kt`, dropping the now-unused `getForDateRange`**

```kotlin
package com.suprxsidh.stride.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Query("SELECT * FROM food_entry WHERE date = :date ORDER BY loggedAt ASC")
    fun observeForDate(date: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT COALESCE(SUM(bufferedKcal), 0) FROM food_entry WHERE date = :date")
    fun observeBufferedTotalForDate(date: String): Flow<Int>

    @Delete
    suspend fun delete(entry: FoodEntryEntity)
}
```

- [ ] **Step 4: In `FoodEntryDaoTest.kt`, drop `offBarcode = null` from every `FoodEntryEntity(...)` call and delete the `getForDateRange` test**

Remove the `, offBarcode = null` fragment from all four `FoodEntryEntity(...)` constructions (in `buffered total for date sums only that date's entries`, `buffered total for a date with no entries is zero, not null`'s setup — it has none, skip — `observeForDate returns entries ordered by loggedAt`, and `getForDateRange returns entries within the inclusive range, ordered by date then time`), and delete the entire `getForDateRange returns entries within the inclusive range, ordered by date then time` test method (its DAO method no longer exists). The file becomes:

```kotlin
package com.suprxsidh.stride.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodEntryDaoTest {
    private lateinit var db: StrideDatabase
    private lateinit var dao: FoodEntryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.foodEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `buffered total for date sums only that date's entries`() = runTest {
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Roti+Dal", rawKcal = 450, bufferedKcal = 495, source = "QUICK", loggedAt = 1L))
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Chaas", rawKcal = 80, bufferedKcal = 88, source = "CUSTOM", loggedAt = 2L))
        dao.insert(FoodEntryEntity(date = "2026-08-11", name = "Other day", rawKcal = 300, bufferedKcal = 330, source = "QUICK", loggedAt = 3L))

        val total = dao.observeBufferedTotalForDate("2026-08-10").first()
        assertEquals(583, total)
    }

    @Test
    fun `buffered total for a date with no entries is zero, not null`() = runTest {
        val total = dao.observeBufferedTotalForDate("2026-01-01").first()
        assertEquals(0, total)
    }

    @Test
    fun `observeForDate returns entries ordered by loggedAt`() = runTest {
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "Second", rawKcal = 100, bufferedKcal = 110, source = "QUICK", loggedAt = 200L))
        dao.insert(FoodEntryEntity(date = "2026-08-10", name = "First", rawKcal = 50, bufferedKcal = 55, source = "QUICK", loggedAt = 100L))

        val entries = dao.observeForDate("2026-08-10").first()
        assertEquals(listOf("First", "Second"), entries.map { it.name })
    }
}
```

- [ ] **Step 5: In `FoodRepository.kt`, drop `logOffProduct` and the `barcode` param on `log`**

Change:
```kotlin
    private suspend fun log(name: String, rawKcal: Int, source: String, barcode: String? = null): FoodEntryEntity {
        val entity = FoodEntryEntity(
            date = todayKey(),
            name = name,
            rawKcal = rawKcal,
            bufferedKcal = CalorieMath.bufferedKcal(rawKcal),
            source = source,
            offBarcode = barcode,
            loggedAt = nowMillis()
        )
        val id = foodEntryDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun logQuickAdd(name: String, rawKcal: Int): FoodEntryEntity = log(name, rawKcal, "QUICK")

    suspend fun logCustomFood(food: CustomFoodEntity, servings: Double): FoodEntryEntity =
        log(food.name, (food.kcalPerServing * servings).roundToInt(), "CUSTOM")

    suspend fun logOffProduct(name: String, rawKcal: Int, barcode: String): FoodEntryEntity =
        log(name, rawKcal, "OFF", barcode)

    suspend fun logGeminiEstimate(name: String, rawKcal: Int): FoodEntryEntity =
        log(name, rawKcal, "GEMINI")
```
to:
```kotlin
    private suspend fun log(name: String, rawKcal: Int, source: String): FoodEntryEntity {
        val entity = FoodEntryEntity(
            date = todayKey(),
            name = name,
            rawKcal = rawKcal,
            bufferedKcal = CalorieMath.bufferedKcal(rawKcal),
            source = source,
            loggedAt = nowMillis()
        )
        val id = foodEntryDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun logQuickAdd(name: String, rawKcal: Int): FoodEntryEntity = log(name, rawKcal, "QUICK")

    suspend fun logCustomFood(food: CustomFoodEntity, servings: Double): FoodEntryEntity =
        log(food.name, (food.kcalPerServing * servings).roundToInt(), "CUSTOM")

    suspend fun logGeminiEstimate(name: String, rawKcal: Int): FoodEntryEntity =
        log(name, rawKcal, "GEMINI")
```

- [ ] **Step 6: Bump `StrideDatabase.kt` to version 9, dropping `OffCacheEntity`/`OffCacheDao`**

Replace the file with:

```kotlin
package com.suprxsidh.stride.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.suprxsidh.stride.data.db.dao.AppSettingsDao
import com.suprxsidh.stride.data.db.dao.CustomFoodDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.dao.PendingDraftDao
import com.suprxsidh.stride.data.db.dao.SyncStateDao
import com.suprxsidh.stride.data.db.dao.UserProfileDao
import com.suprxsidh.stride.data.db.dao.WeighInDao
import com.suprxsidh.stride.data.db.entity.AppSettingsEntity
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import com.suprxsidh.stride.data.db.entity.SyncStateEntity
import com.suprxsidh.stride.data.db.entity.UserProfileEntity
import com.suprxsidh.stride.data.db.entity.WeighInEntity

@Database(
    entities = [
        UserProfileEntity::class,
        FoodEntryEntity::class,
        CustomFoodEntity::class,
        WeighInEntity::class,
        SyncStateEntity::class,
        AppSettingsEntity::class,
        PendingDraftEntity::class
    ],
    // v9: OffCacheEntity removed and FoodEntryEntity lost offBarcode -- Open Food Facts search is
    // cut, Gemini (photo or text) is now the only food-logging path. fallbackToDestructiveMigration()
    // below handles the upgrade, same convention as every prior version bump.
    version = 9,
    exportSchema = false
)
abstract class StrideDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun customFoodDao(): CustomFoodDao
    abstract fun weighInDao(): WeighInDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun pendingDraftDao(): PendingDraftDao

    companion object {
        @Volatile private var INSTANCE: StrideDatabase? = null

        fun getInstance(context: Context): StrideDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StrideDatabase::class.java,
                    "deficit.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 7: In `AppContainer.kt`, drop `openFoodFactsRepository`**

Change:
```kotlin
import com.suprxsidh.stride.food.off.OpenFoodFactsRepository
import com.suprxsidh.stride.food.off.OpenFoodFactsServiceFactory
```
Delete both import lines.

Change:
```kotlin
    val openFoodFactsRepository = OpenFoodFactsRepository(
        OpenFoodFactsServiceFactory.create(),
        database.offCacheDao()
    )
    val geminiFoodRepository = GeminiFoodRepository(
```
to:
```kotlin
    val geminiFoodRepository = GeminiFoodRepository(
```

- [ ] **Step 8: Replace `FoodLogViewModel.kt`, dropping `offRepository` and the OFF state/methods**

```kotlin
package com.suprxsidh.stride.ui.food

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimate
import com.suprxsidh.stride.data.db.dao.MAX_PINNED_SNACKS
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import com.suprxsidh.stride.data.db.entity.PendingDraftEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.GeminiEstimateResult
import com.suprxsidh.stride.data.repository.GeminiFoodRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FoodLogViewModel(
    private val foodRepository: FoodRepository,
    private val geminiFoodRepository: GeminiFoodRepository
) : ViewModel() {

    val todayEntries: StateFlow<List<FoodEntryEntity>> =
        foodRepository.observeTodayEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val todayBufferedTotal: StateFlow<Int> =
        foodRepository.observeTodayBufferedTotal().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val pinnedFoods: StateFlow<List<CustomFoodEntity>> =
        foodRepository.observePinnedCustomFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allCustomFoods: StateFlow<List<CustomFoodEntity>> =
        foodRepository.observeAllCustomFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var quickAddName by mutableStateOf("")
    var quickAddKcal by mutableStateOf("")

    /** Set when [logQuickAdd] rejects the current input; null once a submit attempt validates. */
    var quickAddError by mutableStateOf<String?>(null)
        private set

    var customFoodName by mutableStateOf("")
    var customFoodKcal by mutableStateOf("")
    var customFoodServingLabel by mutableStateOf("")

    /** Set when [saveCustomFood] rejects the current input; null once a submit attempt validates. */
    var customFoodError by mutableStateOf<String?>(null)
        private set

    /**
     * Set when [saveCustomFood] was asked to pin a food but the app already has
     * [MAX_PINNED_SNACKS] pinned — the food still saves (unpinned) rather than being dropped
     * entirely.
     */
    var pinCapMessage by mutableStateOf<String?>(null)
        private set

    var aiDescription by mutableStateOf("")
    var aiSubmitInFlight by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set
    var reviewEstimate by mutableStateOf<GeminiFoodEstimate?>(null)
        private set

    // Observes the stored key rather than sampling it once, so saving or clearing it in Settings
    // shows/hides the AI section deterministically instead of depending on collection timing.
    val aiEstimateAvailable: StateFlow<Boolean> = geminiFoodRepository.observeAvailability()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pendingDrafts: StateFlow<List<PendingDraftEntity>> = geminiFoodRepository.observePendingDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estimates only. On success, [reviewEstimate] is populated and the UI shows the itemized review sheet. */
    fun submitAiEstimate(photoFile: File?) {
        if (aiDescription.isBlank() && photoFile == null) return
        aiSubmitInFlight = true
        aiError = null
        viewModelScope.launch {
            when (val result = geminiFoodRepository.estimateMeal(aiDescription.ifBlank { null }, photoFile)) {
                is GeminiEstimateResult.Success -> { reviewEstimate = result.estimate; aiSubmitInFlight = false }
                is GeminiEstimateResult.Failed -> { aiError = "Couldn't reach Gemini — saved as a draft, will retry automatically. (${result.message})"; aiSubmitInFlight = false }
                GeminiEstimateResult.NoApiKey -> { aiError = "Set a Gemini API key in Settings first."; aiSubmitInFlight = false }
            }
        }
    }

    /** Called when the user taps "confirm" on the review sheet, with whatever they left in its editable fields. */
    fun confirmAiEstimate(editedName: String, editedTotalKcal: Int) {
        val estimate = reviewEstimate ?: return
        viewModelScope.launch {
            geminiFoodRepository.confirmEstimate(estimate, editedName, editedTotalKcal)
            reviewEstimate = null
            aiDescription = ""
        }
    }

    fun cancelAiReview() {
        reviewEstimate = null
    }

    fun retryDrafts() {
        viewModelScope.launch { geminiFoodRepository.retryPendingDrafts() }
    }

    fun discardDraft(id: Long, kcal: Int) {
        viewModelScope.launch { geminiFoodRepository.discardDraftAsQuickAdd(id, kcal) }
    }

    fun logQuickAdd() {
        val kcal = quickAddKcal.toIntOrNull()
        if (quickAddName.isBlank() || kcal == null || kcal <= 0) {
            quickAddError = when {
                quickAddName.isBlank() -> "Enter a food name."
                else -> "Enter a valid calorie amount."
            }
            return
        }
        quickAddError = null
        val name = quickAddName
        viewModelScope.launch {
            foodRepository.logQuickAdd(name, kcal)
            quickAddName = ""
            quickAddKcal = ""
        }
    }

    fun logPinned(food: CustomFoodEntity) {
        viewModelScope.launch { foodRepository.logCustomFood(food, servings = 1.0) }
    }

    fun saveCustomFood(isPinned: Boolean) {
        val kcal = customFoodKcal.toIntOrNull()
        if (customFoodName.isBlank() || kcal == null || kcal <= 0) {
            customFoodError = when {
                customFoodName.isBlank() -> "Enter a name."
                else -> "Enter a valid calorie amount."
            }
            return
        }
        customFoodError = null
        pinCapMessage = null
        val name = customFoodName
        val label = customFoodServingLabel.ifBlank { "1 serving" }
        viewModelScope.launch {
            // Match by name first so re-saving an existing food (e.g. correcting its calorie
            // count) updates that row instead of inserting a duplicate — the DB has no unique
            // constraint on name, so a bare insert here previously always created a new row.
            val existing = foodRepository.findCustomFoodByName(name)
            val alreadyPinned = existing?.isPinned == true
            val effectivePinned = when {
                !isPinned -> false
                alreadyPinned -> true
                foodRepository.countPinnedCustomFoods() >= MAX_PINNED_SNACKS -> {
                    pinCapMessage = "Saved \"$name\" without pinning — you already have $MAX_PINNED_SNACKS pinned snacks. Unpin one first."
                    false
                }
                else -> true
            }
            foodRepository.upsertCustomFood(
                CustomFoodEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    kcalPerServing = kcal,
                    servingLabel = label,
                    isPinned = effectivePinned,
                )
            )
            customFoodName = ""
            customFoodKcal = ""
            customFoodServingLabel = ""
        }
    }

    fun logCustomFoodWithServings(food: CustomFoodEntity, servings: Double) {
        viewModelScope.launch { foodRepository.logCustomFood(food, servings) }
    }

    fun deleteCustomFood(food: CustomFoodEntity) {
        viewModelScope.launch { foodRepository.deleteCustomFood(food) }
    }

    fun deleteFoodEntry(entry: FoodEntryEntity) {
        viewModelScope.launch { foodRepository.deleteFoodEntry(entry) }
    }
}
```

- [ ] **Step 9: Replace `FoodLogViewModelTest.kt`, dropping the OFF fixture and `searchOff` test**

```kotlin
package com.suprxsidh.stride.ui.food

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.stride.ai.gemini.GeminiApi
import com.suprxsidh.stride.ai.gemini.GeminiCandidate
import com.suprxsidh.stride.ai.gemini.GeminiContent
import com.suprxsidh.stride.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.stride.ai.gemini.GeminiGenerateContentRequest
import com.suprxsidh.stride.ai.gemini.GeminiGenerateContentResponse
import com.suprxsidh.stride.ai.gemini.GeminiPart
import com.suprxsidh.stride.data.db.StrideDatabase
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.repository.FoodRepository
import com.suprxsidh.stride.data.repository.GeminiFoodRepository
import com.suprxsidh.stride.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoodLogViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: StrideDatabase
    private lateinit var foodRepository: FoodRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: FoodLogViewModel

    private class FakeSucceedingGeminiApi : GeminiApi {
        override suspend fun generateContent(model: String, apiKey: String, request: GeminiGenerateContentRequest): GeminiGenerateContentResponse {
            val json = """{"items":[{"name":"2 rotis","kcal":180}],"totalKcal":180,"confidence":"medium"}"""
            return GeminiGenerateContentResponse(listOf(GeminiCandidate(GeminiContent(listOf(GeminiPart(text = json))))))
        }
    }

    private fun buildViewModel(geminiApi: GeminiApi = FakeSucceedingGeminiApi()): FoodLogViewModel {
        val geminiFoodRepository = GeminiFoodRepository(
            estimator = GeminiFoodEstimator(geminiApi),
            settingsRepository = settingsRepository,
            foodRepository = foodRepository,
            pendingDraftDao = db.pendingDraftDao()
        )
        return FoodLogViewModel(foodRepository, geminiFoodRepository)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StrideDatabase::class.java)
            .setQueryExecutor(java.util.concurrent.Executor { it.run() })
            .setTransactionExecutor(java.util.concurrent.Executor { it.run() })
            .allowMainThreadQueries().build()
        foodRepository = FoodRepository(db.foodEntryDao(), db.customFoodDao())
        settingsRepository = SettingsRepository(db.appSettingsDao())
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `logQuickAdd inserts a buffered entry and clears the input fields`() = runTest(testDispatcher) {
        viewModel.quickAddName = "Poha"
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.quickAddName)
        assertEquals("", viewModel.quickAddKcal)
        assertEquals(330, db.foodEntryDao().observeBufferedTotalForDate(
            com.suprxsidh.stride.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first())
    }

    @Test
    fun `logQuickAdd with blank name does nothing`() = runTest(testDispatcher) {
        viewModel.quickAddName = ""
        viewModel.quickAddKcal = "300"
        viewModel.logQuickAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.stride.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `logPinned logs one serving of the given custom food in a single call`() = runTest(testDispatcher) {
        val chaas = CustomFoodEntity(name = "Chaas", kcalPerServing = 100, servingLabel = "1 glass", isPinned = true)
        viewModel.logPinned(chaas)
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.foodEntryDao().observeForDate(
            com.suprxsidh.stride.data.calc.DayBoundary.logicalDate(java.time.LocalDateTime.now()).toString()
        ).first()
        assertEquals(1, all.size)
        assertEquals(110, all[0].bufferedKcal)
    }

    @Test
    fun `saveCustomFood with blank name is a no-op`() = runTest(testDispatcher) {
        viewModel.customFoodName = ""
        viewModel.customFoodKcal = "100"
        viewModel.saveCustomFood(isPinned = false)
        testDispatcher.scheduler.advanceUntilIdle()

        val all = db.customFoodDao().observeAll().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `aiEstimateAvailable reflects whether a Gemini key is set`() = runTest {
        settingsRepository.setGeminiApiKey(null)
        var viewModel = buildViewModel()
        backgroundScope.launch { viewModel.aiEstimateAvailable.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.aiEstimateAvailable.value)

        settingsRepository.setGeminiApiKey("a-key")
        viewModel = buildViewModel()
        backgroundScope.launch { viewModel.aiEstimateAvailable.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.aiEstimateAvailable.value)
    }

    @Test
    fun `aiEstimateAvailable updates when the key changes without rebuilding the view model`() = runTest {
        settingsRepository.setGeminiApiKey(null)
        val viewModel = buildViewModel()
        backgroundScope.launch { viewModel.aiEstimateAvailable.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.aiEstimateAvailable.value)

        settingsRepository.setGeminiApiKey("a-key")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.aiEstimateAvailable.value)

        settingsRepository.setGeminiApiKey(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.aiEstimateAvailable.value)
    }

    @Test
    fun `submitAiEstimate populates reviewEstimate without logging anything yet`() = runTest {
        settingsRepository.setGeminiApiKey("a-key")
        val viewModel = buildViewModel(geminiApi = FakeSucceedingGeminiApi())
        viewModel.aiDescription = "2 rotis, dal"
        viewModel.submitAiEstimate(photoFile = null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(180, viewModel.reviewEstimate?.totalKcal)
        assertEquals(0, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `confirmAiEstimate logs the entry and clears the review state`() = runTest {
        settingsRepository.setGeminiApiKey("a-key")
        val viewModel = buildViewModel(geminiApi = FakeSucceedingGeminiApi())
        viewModel.aiDescription = "2 rotis, dal"
        viewModel.submitAiEstimate(photoFile = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmAiEstimate(editedName = "2 rotis, dal", editedTotalKcal = 180)
        testDispatcher.scheduler.advanceUntilIdle()

        val entries = foodRepository.observeTodayEntries().first()
        assertEquals(1, entries.size)
        assertEquals("GEMINI", entries.first().source)
        assertNull(viewModel.reviewEstimate)
        assertEquals("", viewModel.aiDescription)
    }

    @Test
    fun `cancelAiReview clears the review state without logging`() = runTest {
        settingsRepository.setGeminiApiKey("a-key")
        val viewModel = buildViewModel(geminiApi = FakeSucceedingGeminiApi())
        viewModel.aiDescription = "2 rotis, dal"
        viewModel.submitAiEstimate(photoFile = null)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelAiReview()

        assertNull(viewModel.reviewEstimate)
        assertEquals(0, foodRepository.observeTodayEntries().first().size)
    }
}
```

- [ ] **Step 10: In `FoodLogScreen.kt`, drop `openFoodFactsRepository` from the viewModel factory and delete the OFF search UI section**

Change:
```kotlin
    val viewModel: FoodLogViewModel = viewModel(factory = viewModelFactory {
        initializer {
            FoodLogViewModel(
                app.container.foodRepository,
                app.container.openFoodFactsRepository,
                app.container.geminiFoodRepository
            )
        }
    })
```
to:
```kotlin
    val viewModel: FoodLogViewModel = viewModel(factory = viewModelFactory {
        initializer {
            FoodLogViewModel(
                app.container.foodRepository,
                app.container.geminiFoodRepository
            )
        }
    })
```

Delete this entire block (the "Search packaged foods (Open Food Facts)" section, sitting between the "Quick add" section and the "Custom foods" section):
```kotlin
        item { StartLineDivider(modifier = Modifier.padding(vertical = Spacing.sm)) }
        item { Text("Search packaged foods (Open Food Facts)".uppercase(), style = MaterialTheme.typography.titleMedium) }
        item {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedTextField(value = viewModel.offQuery, onValueChange = { viewModel.offQuery = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Button(onClick = { viewModel.searchOff() }) { Text("Search") } }
        if (viewModel.offSearchInFlight) {
            item { CircularProgressIndicator() }
        } else if (viewModel.offSearchedOnce && viewModel.offResults.isEmpty()) {
            item { Text("No results — check spelling or your connection.", color = StrideOnSurfaceMuted) }
        }
        items(viewModel.offResults) { result ->
            PunchCardRow(
                trailing = { Button(onClick = { viewModel.logOffResult(result) }) { Text("Log") } },
            ) {
                Text("${result.productName} (${result.kcalPerServing} kcal / ${result.servingLabel})", style = MaterialTheme.typography.bodyMedium)
            }
        }
```

Delete the now-unused import:
```kotlin
import androidx.compose.material3.CircularProgressIndicator
```

- [ ] **Step 11: In `DashboardViewModelTest.kt`, drop `offBarcode = null` from the `FoodEntryEntity(...)` call**

Change:
```kotlin
        db.foodEntryDao().insert(FoodEntryEntity(date = "2026-08-10", name = "Test", rawKcal = 200, bufferedKcal = 220, source = "QUICK", offBarcode = null, loggedAt = 1L))
```
to:
```kotlin
        db.foodEntryDao().insert(FoodEntryEntity(date = "2026-08-10", name = "Test", rawKcal = 200, bufferedKcal = 220, source = "QUICK", loggedAt = 1L))
```

- [ ] **Step 12: Run the build and tests**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "refactor: cut Open Food Facts search, Gemini is now the sole food-logging path"
```

---

## Task 4: Onboarding and Health Connect copy cleanup

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/stride/ui/onboarding/OnboardingScreen.kt`

**Interfaces:**
- Consumes: nothing new — text-only change, no signature changes.

- [ ] **Step 1: Update the Health Connect setup step's permission copy**

Change (around line 187):
```kotlin
        Text(
            "2. Grant this app the Exercise, Calories, Distance, Heart Rate, and Weight permissions when prompted.",
            style = MaterialTheme.typography.bodyMedium,
        )
```
to:
```kotlin
        Text(
            "2. Grant this app the Calories and Weight permissions when prompted.",
            style = MaterialTheme.typography.bodyMedium,
        )
```

Change (the line right above it, around line 183):
```kotlin
            "1. Open Samsung Health → Settings → Data management → Health Connect sync, and turn it on.",
```
stays as-is (no run-specific wording to fix here).

Change (around line 191, the sync-timing note):
```kotlin
        Text(
            "Sync can take 30–60 minutes after a run. Opening Samsung Health first speeds it up.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideOnSurfaceMuted,
        )
```
to:
```kotlin
        Text(
            "Sync can take 30–60 minutes to catch up. Opening Samsung Health first speeds it up.",
            style = MaterialTheme.typography.bodySmall,
            color = StrideOnSurfaceMuted,
        )
```

- [ ] **Step 2: Run the build and tests**

```bash
./gradlew testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "docs: update onboarding copy for the calories-burned-only Health Connect scope"
```

---

## Task 5: Final verification and project docs

**Files:**
- Modify: `future_plans.md`
- Modify: `CLAUDE.md` (Stride's own, not the portfolio-level one)

**Interfaces:** none — documentation and verification only.

- [ ] **Step 1: Run the full check**

```bash
cd /Users/Suprasidh/claudecode-projects/stride
./gradlew testDebugUnitTest assembleDebug
```
Expected: BUILD SUCCESSFUL, all tests pass. If anything fails here that passed at the end of Tasks 1-4, it means an earlier task's step was missed — go back and find the gap rather than patching around it in this task.

- [ ] **Step 2: Grep for any leftover references to removed symbols**

```bash
grep -rn "WeeklyCommitment\|ConsistencyRepository\|WeeklyReview\|MotivationLine\|MotivationCard\|RunAnalytics\|RunTypes\|ExerciseSession\|RunDetail\|OpenFoodFacts\|OffCache\|offBarcode\|offQuery\|offResults\|searchOff\|logOffResult\|logOffProduct" app/src/main/java app/src/test/java
```
Expected: no output. If anything prints, it's a missed edit from Tasks 1-3 — fix it and re-run Step 1.

- [ ] **Step 3: Update `future_plans.md`**

Replace the file's content with:

```markdown
# stride — parked ideas

- Gym/strength logging module — explicitly deferred v2.0.0 per SPEC.md §3.16.
- Exercise/running tracking (run detection, weekly commitment, consistency grid, weekly review,
  motivation lines) — cut from the app 2026-09-02 to focus on calorie counting alone. Still in
  git history on `master` (commits tagged "refactor: cut weekly commitment..." /
  "refactor: cut running/exercise-session tracking..."); re-scope from there rather than
  rebuilding from scratch when this comes back.
- Open Food Facts barcode/search logging — cut 2026-09-02 alongside the above; Gemini (photo or
  text) is the sole food-logging path for now.
- Phase 4 backlog (per SPEC.md, not yet built, now largely superseded by the above cut): all
  notifications, home-screen widget, guided routines, backup/export.
- Phase 1 Minor polish backlog: all 8 items fixed 2026-08-21 — see BUILD_PLAN.md.
- ~54MB stale Gradle-cache blobs in git history (from early Task 1 commit, reverted but not
  purged) — reclaim via history rewrite before ever pushing repo anywhere, not before.
```

- [ ] **Step 4: Add a status entry to `CLAUDE.md`**

Append this section to the end of `CLAUDE.md` (create an "## Status" section if one doesn't already serve this purpose, otherwise add to the existing running log):

```markdown
## Calorie-only scope-down (2026-09-02)

App re-scoped to a pure calorie counter per user direction: Gemini food logging (photo/text)
+ onboarding BMR/TDEE budget + weigh-ins + Health Connect calories-burned read + weekly adaptive
budget recompute. Cut: weekly running commitment, consistency grid, run analytics/detail,
weekly review, motivation lines, Open Food Facts search. See
`docs/superpowers/specs/2026-09-02-calorie-only-scope-down-design.md` and
`docs/superpowers/plans/2026-09-02-calorie-only-scope-down.md`. Exercise logging is deferred,
not deleted from history — see `future_plans.md`.
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: record the calorie-only scope-down in project docs"
```

---

## Self-Review Notes (for whoever executes this plan)

- **Spec coverage:** Task 1 covers spec §"Cut" items 1 (weekly commitment/consistency/review) and part of "MotivationCard"; Task 2 covers "Cut" item 2 (run tracking) and the "Health Connect... calories burned" keep-item (the new `getTodaysCaloriesBurned`); Task 3 covers "Cut" item 3 (OFF); Task 4 covers onboarding copy; Task 5 verifies build/tests and updates docs per spec §8. All spec sections have a task.
- **Type consistency:** `HealthConnectRepository`'s constructor is `(dataSource, syncStateDao, weighInDao, clock)` consistently across Task 2's Steps 6, 7, 10, and Task 2/3's dashboard test edits — verified no stale 4-arg-with-`exerciseSessionDao` call sites remain after Task 2 Step 10.
- **Ordering dependency:** Task 2 Step 15 explicitly calls out that its `FoodEntryEntity` test construction keeps `offBarcode = null` because Task 3 (which removes that field) runs after it — this is intentional, not an oversight.
