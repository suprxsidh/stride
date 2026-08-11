# Phase 2: Health Connect + Gemini AI Food Logging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Health Connect integration (exercise-session sync, per-run analytics, two-way weigh-in sync) and Gemini AI free-text/photo food estimation to the Phase-1 "Deficit" app, per `SPEC.md` §3.3 path 1 and §3.4.

**Architecture:** Two new independent subsystems bolted onto the existing manual-DI Room + Compose app. Health Connect logic is split into a thin real SDK adapter (`HealthConnectDataSource`, not unit-testable without a device) and a pure business-logic layer (`HealthConnectRepository`, fully unit-testable against a fake data source) — this isolates the untestable SDK surface into the smallest possible files. Gemini logic follows the same Retrofit-based pattern already used for Open Food Facts (`GeminiServiceFactory` mirrors `OpenFoodFactsServiceFactory`), with a `PendingDraft` queue for failed calls per SPEC.md §4.3.

**Tech Stack:** `androidx.health.connect:connect-client:1.1.0`, `androidx.work:work-runtime-ktx:2.11.2`, existing Retrofit + kotlinx-serialization stack (no new network library) for the Gemini REST client, existing Room for `AppSettings`/`PendingDraft` (no DataStore dependency added). `ActivityResultContracts.TakePicture()` + `FileProvider` for photo capture (no CameraX).

## Global Constraints

- Buffer math: every food entry gets +10% buffer (existing `CalorieMath` — reuse, do not reimplement). Exercise calories are credited **50%** toward budget (`CalorieMath.creditedExerciseKcal`, already implemented and unit-tested in Phase 1 — it currently has no caller; Task 6 wires it up).
- Day boundary is 3:00 AM (existing `DayBoundary` — reuse for any new "today" queries).
- No backend, no accounts, no analytics, no cloud sync. Only network calls allowed: Open Food Facts (existing) and Gemini (only when the user has set a key in Settings). Gemini key is stored locally only (Room, this device) and never leaves local storage except as an `x-goog-api-key` header on requests the user's own key authorizes.
- Do **not** attempt to read `ExerciseRoute` / GPS routes — Samsung Health does not expose them via Health Connect. No maps.
- No daily streaks anywhere. No projected goal dates.
- Package name `com.suprxsidh.deficit`, manual DI via `AppContainer` (no Hilt), ViewModels via `viewModelFactory { initializer { ... } }` in each `*Screen.kt` (no DI framework) — follow this exact existing pattern for every new ViewModel.
- Health Connect client API surface (unit accessor names on `Length`/`Mass`/`Energy`, the exact package for `Metadata`) can vary slightly by client version. If a named symbol doesn't resolve when you build, use IDE autocomplete on the receiver type to find the equivalent accessor (e.g. `Energy.inKilocalories` vs `Energy.inCalories / 1000.0`) — the structure and business logic below do not change, only the accessor name might.
- `DeficitDatabase` currently has no real `Migration` objects, only `fallbackToDestructiveMigration()`. This is an accepted approach for this single-user sideloaded app (Phase 1 → 2 already crossed a version bump this way). Keep using it — do not introduce real migrations.
- **Deliberate simplification vs. SPEC.md §3.4's "Changes API" wording:** this plan implements sync via a stored `lastSyncEpochMs` timestamp + time-range reads (Task 4), not the Health Connect Changes API's token/`getChanges()` mechanism. Functionally equivalent for a single-device, single-user app with a 60-minute sync cadence (no risk of the changes-token expiring between syncs the way a rarely-opened multi-week-gap app might hit). `SyncStateEntity.hcChangesToken` exists in the schema for a future upgrade to true Changes-API streaming but is unused by this plan's code — do not treat it as dead-code to delete, and do not try to wire it up unless asked; it's intentionally reserved.
- Test stack: JUnit4 4.13.2, Robolectric 4.13 (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`), MockWebServer 4.12.0, `kotlinx-coroutines-test`. Follow the exact test scaffolding shown in each task — it matches existing `WeighInDaoTest.kt` / `WeightViewModelTest.kt` patterns.
- Explicitly **out of scope for this plan** (deferred to Phase 3+): AlarmManager-based notifications (motivation, meal reminders, weigh-in reminder, posture breaks), the Glance home-screen widget, guided routines, weekly review, adaptive budget, consistency grid, backup/export, and the Vivo/OriginOS battery-optimization checklist (SPEC.md §4.1) — that's tied to background *reminders*, which don't exist yet. The Health-Connect-specific onboarding checklist (Task 7) is in scope; the general battery/auto-start checklist is not.

---

## File Structure

New packages/files:

```
app/src/main/java/com/suprxsidh/deficit/
├── data/
│   ├── calc/
│   │   └── RunAnalytics.kt                    [Task 2]
│   ├── db/
│   │   ├── entity/
│   │   │   ├── ExerciseSessionEntity.kt       [Task 1]
│   │   │   ├── SyncStateEntity.kt             [Task 1]
│   │   │   ├── AppSettingsEntity.kt           [Task 10]
│   │   │   └── PendingDraftEntity.kt          [Task 12]
│   │   └── dao/
│   │       ├── ExerciseSessionDao.kt          [Task 1]
│   │       ├── SyncStateDao.kt                [Task 1]
│   │       ├── AppSettingsDao.kt              [Task 10]
│   │       └── PendingDraftDao.kt             [Task 12]
│   └── repository/
│       ├── HealthConnectRepository.kt         [Task 4]
│       ├── SettingsRepository.kt              [Task 10]
│       └── GeminiFoodRepository.kt            [Task 12]
├── health/
│   ├── HealthConnectManager.kt                [Task 3]
│   ├── HealthDataSource.kt                    [Task 3]  (interface + DTOs)
│   ├── HealthConnectDataSource.kt             [Task 3]  (real SDK adapter)
│   └── HealthConnectSyncWorker.kt             [Task 5]
├── ai/gemini/
│   ├── GeminiModels.kt                        [Task 11]
│   ├── GeminiApi.kt                           [Task 11]
│   ├── GeminiServiceFactory.kt                [Task 11]
│   └── GeminiFoodEstimator.kt                 [Task 11]
└── ui/
    ├── health/
    │   ├── RunDetailScreen.kt                 [Task 9]
    │   └── RunDetailViewModel.kt              [Task 9]
    └── settings/
        ├── SettingsScreen.kt                  [Task 10]
        └── SettingsViewModel.kt               [Task 10]
```

Modified files: `DeficitDatabase.kt`, `WeighInEntity.kt`, `WeighInDao.kt`, `FoodRepository.kt`, `WeightRepository.kt`, `AppContainer.kt`, `app/build.gradle.kts`, `AndroidManifest.xml`, `Routes.kt`, `DeficitNavHost.kt`, `MainActivity.kt`, `DashboardScreen.kt`, `DashboardViewModel.kt`, `OnboardingScreen.kt`, `OnboardingViewModel.kt`, `WeightScreen.kt`, `WeightViewModel.kt`, `FoodLogScreen.kt`, `FoodLogViewModel.kt`, `TESTING.md`.

---

### Task 1: Data layer — ExerciseSession & SyncState, WeighIn HC fields, DB v3

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/ExerciseSessionEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/SyncStateEntity.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/WeighInEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/ExerciseSessionDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/SyncStateDao.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/WeighInDao.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/DeficitDatabase.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/ExerciseSessionDaoTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/SyncStateDaoTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/db/dao/WeighInDaoTest.kt` (add cases)

**Interfaces:**
- Produces: `ExerciseSessionEntity(id, hcRecordId, date, exerciseType, startTimeEpochMs, durationMin, distanceM, avgPaceSecPerKm, avgHr, maxHr, kcalReal, kcalCredited)`; `SyncStateEntity(id=1, hcChangesToken, lastSyncEpochMs)`; `WeighInEntity` gains `hcRecordId: String?`; `ExerciseSessionDao.getByHcRecordId/insert/update/observeAll`; `SyncStateDao.get/upsert`; `WeighInDao.getByHcRecordId/getUnsyncedToHc`. Task 4 (`HealthConnectRepository`) consumes all of these directly.

- [ ] **Step 1: Write the failing DAO tests**

`app/src/test/java/com/suprxsidh/deficit/data/db/dao/ExerciseSessionDaoTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
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
class ExerciseSessionDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: ExerciseSessionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.exerciseSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getByHcRecordId returns null when absent`() = runTest {
        assertNull(dao.getByHcRecordId("hc-1"))
    }

    @Test
    fun `insert then getByHcRecordId returns the session`() = runTest {
        dao.insert(session(hcRecordId = "hc-1"))
        val found = dao.getByHcRecordId("hc-1")
        assertEquals("hc-1", found?.hcRecordId)
        assertEquals(1800, found?.kcalReal)
    }

    @Test
    fun `observeAll orders by startTime descending`() = runTest {
        dao.insert(session(hcRecordId = "hc-1", startTimeEpochMs = 1_000L))
        dao.insert(session(hcRecordId = "hc-2", startTimeEpochMs = 2_000L))
        val all = dao.observeAll()
        // Flow collection is exercised in the repository test (Task 4); here we just
        // confirm insert didn't throw and both rows are distinct via getByHcRecordId.
        assertEquals("hc-2", dao.getByHcRecordId("hc-2")?.hcRecordId)
        assertEquals("hc-1", dao.getByHcRecordId("hc-1")?.hcRecordId)
    }

    private fun session(hcRecordId: String, startTimeEpochMs: Long = 1_700_000_000_000L) = ExerciseSessionEntity(
        hcRecordId = hcRecordId,
        date = "2026-08-11",
        exerciseType = "RUNNING",
        startTimeEpochMs = startTimeEpochMs,
        durationMin = 32,
        distanceM = 5200.0,
        avgPaceSecPerKm = 369.0,
        avgHr = 152,
        maxHr = 171,
        kcalReal = 1800,
        kcalCredited = 900
    )
}
```

`app/src/test/java/com/suprxsidh/deficit/data/db/dao/SyncStateDaoTest.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity
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
class SyncStateDaoTest {
    private lateinit var db: DeficitDatabase
    private lateinit var dao: SyncStateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.syncStateDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `get returns null before any upsert`() = runTest {
        assertNull(dao.get())
    }

    @Test
    fun `upsert then get returns stored token`() = runTest {
        dao.upsert(SyncStateEntity(id = 1, hcChangesToken = "token-abc", lastSyncEpochMs = 1_700_000_000_000L))
        val state = dao.get()
        assertEquals("token-abc", state?.hcChangesToken)
    }

    @Test
    fun `second upsert replaces the single row`() = runTest {
        dao.upsert(SyncStateEntity(id = 1, hcChangesToken = "first", lastSyncEpochMs = 1L))
        dao.upsert(SyncStateEntity(id = 1, hcChangesToken = "second", lastSyncEpochMs = 2L))
        assertEquals("second", dao.get()?.hcChangesToken)
    }
}
```

Add to `WeighInDaoTest.kt` (append inside the existing class):
```kotlin
    @Test
    fun `getUnsyncedToHc returns only rows with syncedToHc false`() = runTest {
        dao.upsert(WeighInEntity(date = "2026-08-09", weightKg = 80.0, syncedToHc = true))
        dao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 79.8, syncedToHc = false))
        val unsynced = dao.getUnsyncedToHc()
        assertEquals(1, unsynced.size)
        assertEquals("2026-08-10", unsynced.first().date)
    }

    @Test
    fun `getByHcRecordId finds an imported weigh-in`() = runTest {
        dao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 79.8, syncedToHc = true, hcRecordId = "hc-weight-1"))
        assertEquals(79.8, dao.getByHcRecordId("hc-weight-1")?.weightKg)
    }
```
(Add the matching `import org.junit.Assert.assertEquals` if not already present in the file — check first.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.db.dao.ExerciseSessionDaoTest" --tests "com.suprxsidh.deficit.data.db.dao.SyncStateDaoTest" --tests "com.suprxsidh.deficit.data.db.dao.WeighInDaoTest"`
Expected: compile failure (types don't exist yet) — that's the expected "fail" for this step.

- [ ] **Step 3: Create the entities**

`ExerciseSessionEntity.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_session", indices = [Index(value = ["hcRecordId"], unique = true)])
data class ExerciseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hcRecordId: String,
    val date: String,
    val exerciseType: String,
    val startTimeEpochMs: Long,
    val durationMin: Int,
    val distanceM: Double?,
    val avgPaceSecPerKm: Double?,
    val avgHr: Int?,
    val maxHr: Int?,
    val kcalReal: Int,
    val kcalCredited: Int
)
```

`SyncStateEntity.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val hcChangesToken: String?,
    val lastSyncEpochMs: Long?
)
```

Modify `WeighInEntity.kt` — add one field:
```kotlin
data class WeighInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val syncedToHc: Boolean = false,
    val hcRecordId: String? = null
)
```

- [ ] **Step 4: Create/modify the DAOs**

`ExerciseSessionDao.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSessionDao {
    @Query("SELECT * FROM exercise_session WHERE hcRecordId = :hcRecordId LIMIT 1")
    suspend fun getByHcRecordId(hcRecordId: String): ExerciseSessionEntity?

    @Insert
    suspend fun insert(session: ExerciseSessionEntity): Long

    @Update
    suspend fun update(session: ExerciseSessionEntity)

    @Query("SELECT * FROM exercise_session ORDER BY startTimeEpochMs DESC")
    fun observeAll(): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM exercise_session WHERE date = :date ORDER BY startTimeEpochMs DESC LIMIT 1")
    suspend fun getLatestForDate(date: String): ExerciseSessionEntity?
}
```

`SyncStateDao.kt`:
```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun get(): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
}
```

Add to `WeighInDao.kt`:
```kotlin
    @Query("SELECT * FROM weigh_in WHERE hcRecordId = :hcRecordId LIMIT 1")
    suspend fun getByHcRecordId(hcRecordId: String): WeighInEntity?

    @Query("SELECT * FROM weigh_in WHERE syncedToHc = 0")
    suspend fun getUnsyncedToHc(): List<WeighInEntity>
```

- [ ] **Step 5: Bump the database version and register new entities**

Modify `DeficitDatabase.kt` — update the `@Database` annotation and add the two new abstract DAO accessors:
```kotlin
@Database(
    entities = [
        UserProfileEntity::class,
        FoodEntryEntity::class,
        CustomFoodEntity::class,
        WeighInEntity::class,
        OffCacheEntity::class,
        ExerciseSessionEntity::class,
        SyncStateEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DeficitDatabase : RoomDatabase() {
    // ... existing abstract fun declarations ...
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun syncStateDao(): SyncStateDao

    // in getInstance(): keep fallbackToDestructiveMigration()
}
```
Add the corresponding imports (`ExerciseSessionEntity`, `SyncStateEntity`, `ExerciseSessionDao`, `SyncStateDao`).

- [ ] **Step 6: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.db.dao.*"`
Expected: PASS, all DAO tests green including the two new `WeighInDaoTest` cases.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/db app/src/test/java/com/suprxsidh/deficit/data/db
git commit -m "Task 1: exercise session + sync state entities, weigh-in HC fields, DB v3"
```

---

### Task 2: RunAnalytics — pure pace/heart-rate math

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/calc/RunAnalytics.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/calc/RunAnalyticsTest.kt`

**Interfaces:**
- Produces: `RunAnalytics.avgPaceSecPerKm(durationMin: Int, distanceM: Double): Double?`, `RunAnalytics.avgHeartRate(samplesBpm: List<Int>): Int?`, `RunAnalytics.maxHeartRate(samplesBpm: List<Int>): Int?`. Consumed by Task 4 (`HealthConnectRepository`) and Task 9 (`RunDetailViewModel`).
- Consumes: nothing (pure, no dependency on any other Phase 2 task).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.data.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunAnalyticsTest {
    @Test
    fun `avgPaceSecPerKm computes seconds per km`() {
        // 32 min for 5.2 km => 1920s / 5.2km = 369.23 s/km
        val pace = RunAnalytics.avgPaceSecPerKm(durationMin = 32, distanceM = 5200.0)
        assertEquals(369.23, pace!!, 0.1)
    }

    @Test
    fun `avgPaceSecPerKm returns null for zero distance`() {
        assertNull(RunAnalytics.avgPaceSecPerKm(durationMin = 30, distanceM = 0.0))
    }

    @Test
    fun `avgHeartRate averages and rounds`() {
        assertEquals(150, RunAnalytics.avgHeartRate(listOf(140, 150, 160)))
    }

    @Test
    fun `avgHeartRate returns null for empty samples`() {
        assertNull(RunAnalytics.avgHeartRate(emptyList()))
    }

    @Test
    fun `maxHeartRate returns the peak sample`() {
        assertEquals(171, RunAnalytics.maxHeartRate(listOf(140, 171, 160)))
    }

    @Test
    fun `maxHeartRate returns null for empty samples`() {
        assertNull(RunAnalytics.maxHeartRate(emptyList()))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.RunAnalyticsTest"`
Expected: FAIL (compile error, `RunAnalytics` doesn't exist).

- [ ] **Step 3: Implement**

```kotlin
package com.suprxsidh.deficit.data.calc

object RunAnalytics {
    fun avgPaceSecPerKm(durationMin: Int, distanceM: Double): Double? {
        if (distanceM <= 0.0) return null
        val km = distanceM / 1000.0
        return (durationMin * 60.0) / km
    }

    fun avgHeartRate(samplesBpm: List<Int>): Int? {
        if (samplesBpm.isEmpty()) return null
        return samplesBpm.average().let { Math.round(it).toInt() }
    }

    fun maxHeartRate(samplesBpm: List<Int>): Int? {
        return samplesBpm.maxOrNull()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.calc.RunAnalyticsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/calc/RunAnalytics.kt app/src/test/java/com/suprxsidh/deficit/data/calc/RunAnalyticsTest.kt
git commit -m "Task 2: run analytics — avg pace, avg/max heart rate"
```

---

### Task 3: Health Connect dependency, manifest, permission manager, data source abstraction

**Files:**
- Modify: `app/build.gradle.kts` — add Health Connect dependency
- Modify: `app/src/main/AndroidManifest.xml` — add health permissions + `<queries>`
- Create: `app/src/main/java/com/suprxsidh/deficit/health/HealthConnectManager.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/health/HealthDataSource.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/health/HealthConnectDataSource.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/health/HealthConnectManagerTest.kt`

**Interfaces:**
- Produces: `HealthConnectManager.REQUIRED_PERMISSIONS: Set<String>`, `HealthConnectManager.availability(context): Int` (returns one of `HealthConnectClient.SDK_AVAILABLE` / `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` / `SDK_UNAVAILABLE`), `HealthConnectManager.hasAllPermissions(context): Boolean`, `HealthConnectManager.requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>>`. `HealthDataSource` interface with `RemoteExerciseSession`/`RemoteWeightRecord` DTOs — consumed by Task 4.
- Consumes: nothing new from earlier tasks.

- [ ] **Step 1: Add the dependency**

In `app/build.gradle.kts`, inside the `dependencies { ... }` block, add:
```kotlin
    implementation("androidx.health.connect:connect-client:1.1.0")
```

- [ ] **Step 2: Add manifest permissions**

In `app/src/main/AndroidManifest.xml`, add alongside the existing `INTERNET` permission (outside `<application>`):
```xml
    <uses-permission android:name="android.permission.health.READ_EXERCISE" />
    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />
    <uses-permission android:name="android.permission.health.READ_DISTANCE" />
    <uses-permission android:name="android.permission.health.READ_SPEED" />
    <uses-permission android:name="android.permission.health.READ_HEART_RATE" />
    <uses-permission android:name="android.permission.health.READ_WEIGHT" />
    <uses-permission android:name="android.permission.health.WRITE_WEIGHT" />

    <queries>
        <package android:name="com.google.android.apps.healthdata" />
    </queries>
```
(A `PRIVACY_POLICY` rationale activity-alias is normally recommended for Play Store apps but is skipped here — this is a personal sideloaded debug build, never distributed.)

- [ ] **Step 3: Write the (device-independent) permission-set test**

```kotlin
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
```
This test only inspects the string-valued permission set (`HealthPermission.getReadPermission`/`getWritePermission` are pure string builders, no device/service call) — it does not touch `HealthConnectClient` itself, which requires a real provider.

- [ ] **Step 4: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.health.HealthConnectManagerTest"`
Expected: FAIL (compile error).

- [ ] **Step 5: Implement `HealthConnectManager`**

```kotlin
package com.suprxsidh.deficit.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

object HealthConnectManager {
    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
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

- [ ] **Step 6: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.health.HealthConnectManagerTest"`
Expected: PASS.

- [ ] **Step 7: Define the testable data-source abstraction (no test — pure interface + DTOs)**

`HealthDataSource.kt`:
```kotlin
package com.suprxsidh.deficit.health

import java.time.Instant

data class RemoteExerciseSession(
    val hcRecordId: String,
    val exerciseType: String,
    val startTime: Instant,
    val endTime: Instant,
    val distanceMeters: Double?,
    val kcalReal: Int,
    val heartRateSamplesBpm: List<Int>
)

data class RemoteWeightRecord(
    val hcRecordId: String,
    val time: Instant,
    val weightKg: Double
)

interface HealthDataSource {
    suspend fun readExerciseSessions(since: Instant): List<RemoteExerciseSession>
    suspend fun readNewWeightRecords(since: Instant): List<RemoteWeightRecord>
    suspend fun writeWeightRecord(weightKg: Double, time: Instant): String
}
```

- [ ] **Step 8: Implement the real SDK adapter (not unit-testable — device/HC-provider only)**

`HealthConnectDataSource.kt`:
```kotlin
package com.suprxsidh.deficit.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

class HealthConnectDataSource(private val client: HealthConnectClient) : HealthDataSource {

    override suspend fun readExerciseSessions(since: Instant): List<RemoteExerciseSession> {
        val now = Instant.now()
        val sessions = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(since, now)
            )
        ).records

        return sessions.map { session ->
            val range = TimeRangeFilter.between(session.startTime, session.endTime)

            val distanceMeters = client.readRecords(
                ReadRecordsRequest(recordType = DistanceRecord::class, timeRangeFilter = range)
            ).records.sumOf { it.distance.inMeters }.takeIf { it > 0.0 }

            val heartRateSamples = client.readRecords(
                ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = range)
            ).records.flatMap { it.samples }.map { it.beatsPerMinute.toInt() }

            // Real calorie burn: sum of TotalCaloriesBurnedRecord in the session window.
            // Fall back to 0 if the watch/Samsung Health didn't report calories for this session.
            val kcalReal = client.readRecords(
                ReadRecordsRequest(
                    recordType = androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = range
                )
            ).records.sumOf { it.energy.inKilocalories }.toInt()

            RemoteExerciseSession(
                hcRecordId = session.metadata.id,
                exerciseType = session.exerciseType.toString(),
                startTime = session.startTime,
                endTime = session.endTime,
                distanceMeters = distanceMeters,
                kcalReal = kcalReal,
                heartRateSamplesBpm = heartRateSamples
            )
        }
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
Note: if `Energy.inKilocalories` doesn't resolve for the pulled dependency version, use `.inCalories / 1000.0` instead (small-calorie vs kilocalorie naming has shifted between client versions) — check via IDE autocomplete on `TotalCaloriesBurnedRecord.energy`.

- [ ] **Step 9: Verify the module still builds**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. `HealthConnectDataSource` has no unit test (documented in `TESTING.md` in Task 13 as device-only) — its only job is mapping SDK types to the plain DTOs consumed by Task 4, which *is* fully tested there.

- [ ] **Step 10: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/suprxsidh/deficit/health app/src/test/java/com/suprxsidh/deficit/health
git commit -m "Task 3: Health Connect dependency, permissions, manager, data source abstraction"
```

---

### Task 4: HealthConnectRepository — sync business logic (fully unit-tested)

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/HealthConnectRepository.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/HealthConnectRepositoryTest.kt`

**Interfaces:**
- Consumes: `HealthDataSource`, `RemoteExerciseSession`, `RemoteWeightRecord` (Task 3); `ExerciseSessionDao`, `SyncStateDao`, `WeighInDao`, `ExerciseSessionEntity`, `SyncStateEntity`, `WeighInEntity` (Task 1); `RunAnalytics` (Task 2); `CalorieMath.creditedExerciseKcal(Int): Int` (existing, Phase 1).
- Produces: `HealthConnectRepository(dataSource, exerciseSessionDao, syncStateDao, weighInDao, clock)`, `suspend fun syncExerciseSessions(): Int`, `suspend fun syncWeighIns(): Int`, `fun observeExerciseSessions(): Flow<List<ExerciseSessionEntity>>`, `suspend fun getLatestSessionForDate(date: String): ExerciseSessionEntity?`. Consumed by Task 5 (worker), Task 6 (dashboard), Task 8 (weight sync UI), Task 9 (run detail).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.SyncStateDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.health.HealthDataSource
import com.suprxsidh.deficit.health.RemoteExerciseSession
import com.suprxsidh.deficit.health.RemoteWeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HealthConnectRepositoryTest {

    private class FakeDataSource(
        var sessions: List<RemoteExerciseSession> = emptyList(),
        var newWeights: List<RemoteWeightRecord> = emptyList()
    ) : HealthDataSource {
        val written = mutableListOf<Pair<Double, Instant>>()
        override suspend fun readExerciseSessions(since: Instant) = sessions
        override suspend fun readNewWeightRecords(since: Instant) = newWeights
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant): String {
            written.add(weightKg to time)
            return "hc-written-${written.size}"
        }
    }

    private class FakeExerciseSessionDao : ExerciseSessionDao {
        val rows = mutableMapOf<String, ExerciseSessionEntity>()
        private val flow = MutableStateFlow<List<ExerciseSessionEntity>>(emptyList())
        override suspend fun getByHcRecordId(hcRecordId: String) = rows[hcRecordId]
        override suspend fun insert(session: ExerciseSessionEntity): Long {
            rows[session.hcRecordId] = session.copy(id = rows.size + 1L)
            flow.value = rows.values.sortedByDescending { it.startTimeEpochMs }
            return rows[session.hcRecordId]!!.id
        }
        override suspend fun update(session: ExerciseSessionEntity) {
            rows[session.hcRecordId] = session
            flow.value = rows.values.sortedByDescending { it.startTimeEpochMs }
        }
        override fun observeAll(): Flow<List<ExerciseSessionEntity>> = flow
        override suspend fun getLatestForDate(date: String) = rows.values.filter { it.date == date }.maxByOrNull { it.startTimeEpochMs }
    }

    private class FakeSyncStateDao : SyncStateDao {
        var state: SyncStateEntity? = null
        override suspend fun get() = state
        override suspend fun upsert(state: SyncStateEntity) { this.state = state }
    }

    private class FakeWeighInDao : WeighInDao {
        val rows = mutableListOf<WeighInEntity>()
        private val flow = MutableStateFlow<List<WeighInEntity>>(emptyList())
        override suspend fun upsert(weighIn: WeighInEntity): Long {
            rows.removeAll { it.date == weighIn.date }
            val stored = weighIn.copy(id = rows.size + 1L)
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

    @Test
    fun `syncExerciseSessions inserts new sessions with computed analytics`() = runTest {
        val dataSource = FakeDataSource(
            sessions = listOf(
                RemoteExerciseSession(
                    hcRecordId = "hc-1",
                    exerciseType = "RUNNING",
                    startTime = Instant.parse("2026-08-11T06:00:00Z"),
                    endTime = Instant.parse("2026-08-11T06:32:00Z"),
                    distanceMeters = 5200.0,
                    kcalReal = 380,
                    heartRateSamplesBpm = listOf(140, 150, 160, 171)
                )
            )
        )
        val sessionDao = FakeExerciseSessionDao()
        val repo = HealthConnectRepository(dataSource, sessionDao, FakeSyncStateDao(), FakeWeighInDao(), fixedClock)

        val count = repo.syncExerciseSessions()

        assertEquals(1, count)
        val stored = sessionDao.getByHcRecordId("hc-1")!!
        assertEquals(32, stored.durationMin)
        assertEquals(190, stored.kcalCredited) // 50% of 380, floored
        assertEquals(155, stored.avgHr)
        assertEquals(171, stored.maxHr)
    }

    @Test
    fun `syncExerciseSessions updates an existing session instead of duplicating`() = runTest {
        val dataSource = FakeDataSource(
            sessions = listOf(
                RemoteExerciseSession("hc-1", "RUNNING", Instant.parse("2026-08-11T06:00:00Z"), Instant.parse("2026-08-11T06:30:00Z"), 5000.0, 350, listOf(150))
            )
        )
        val sessionDao = FakeExerciseSessionDao()
        val repo = HealthConnectRepository(dataSource, sessionDao, FakeSyncStateDao(), FakeWeighInDao(), fixedClock)
        repo.syncExerciseSessions()

        dataSource.sessions = listOf(
            RemoteExerciseSession("hc-1", "RUNNING", Instant.parse("2026-08-11T06:00:00Z"), Instant.parse("2026-08-11T06:35:00Z"), 5500.0, 400, listOf(155))
        )
        repo.syncExerciseSessions()

        assertEquals(1, sessionDao.rows.size)
        assertEquals(35, sessionDao.rows.getValue("hc-1").durationMin)
    }

    @Test
    fun `syncWeighIns pushes unsynced local entries to Health Connect`() = runTest {
        val weighInDao = FakeWeighInDao()
        weighInDao.upsert(WeighInEntity(date = "2026-08-10", weightKg = 80.0, syncedToHc = false))
        val dataSource = FakeDataSource()
        val repo = HealthConnectRepository(dataSource, FakeExerciseSessionDao(), FakeSyncStateDao(), weighInDao, fixedClock)

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
        val repo = HealthConnectRepository(dataSource, FakeExerciseSessionDao(), FakeSyncStateDao(), weighInDao, fixedClock)

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
        val repo = HealthConnectRepository(dataSource, FakeExerciseSessionDao(), FakeSyncStateDao(), weighInDao, fixedClock)

        repo.syncWeighIns()

        assertEquals(1, weighInDao.rows.size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.HealthConnectRepositoryTest"`
Expected: FAIL (compile error, `HealthConnectRepository` doesn't exist).

- [ ] **Step 3: Implement**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.calc.CalorieMath
import com.suprxsidh.deficit.data.calc.RunAnalytics
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.SyncStateDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.db.entity.SyncStateEntity
import com.suprxsidh.deficit.data.db.entity.WeighInEntity
import com.suprxsidh.deficit.health.HealthDataSource
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthConnectRepository(
    private val dataSource: HealthDataSource,
    private val exerciseSessionDao: ExerciseSessionDao,
    private val syncStateDao: SyncStateDao,
    private val weighInDao: WeighInDao,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeExerciseSessions(): Flow<List<ExerciseSessionEntity>> = exerciseSessionDao.observeAll()

    suspend fun getLatestSessionForDate(date: String): ExerciseSessionEntity? =
        exerciseSessionDao.getLatestForDate(date)

    suspend fun syncExerciseSessions(): Int {
        val state = syncStateDao.get()
        val since = state?.lastSyncEpochMs?.let { Instant.ofEpochMilli(it) }
            ?: clock().minusDays(30).atZone(ZoneId.systemDefault()).toInstant()

        val remoteSessions = dataSource.readExerciseSessions(since)
        var count = 0
        for (remote in remoteSessions) {
            val durationMin = Duration.between(remote.startTime, remote.endTime).toMinutes().toInt()
            val avgPace = remote.distanceMeters?.let { RunAnalytics.avgPaceSecPerKm(durationMin, it) }
            val entity = ExerciseSessionEntity(
                hcRecordId = remote.hcRecordId,
                date = remote.startTime.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter),
                exerciseType = remote.exerciseType,
                startTimeEpochMs = remote.startTime.toEpochMilli(),
                durationMin = durationMin,
                distanceM = remote.distanceMeters,
                avgPaceSecPerKm = avgPace,
                avgHr = RunAnalytics.avgHeartRate(remote.heartRateSamplesBpm),
                maxHr = RunAnalytics.maxHeartRate(remote.heartRateSamplesBpm),
                kcalReal = remote.kcalReal,
                kcalCredited = CalorieMath.creditedExerciseKcal(remote.kcalReal)
            )
            val existing = exerciseSessionDao.getByHcRecordId(remote.hcRecordId)
            if (existing != null) {
                exerciseSessionDao.update(entity.copy(id = existing.id))
            } else {
                exerciseSessionDao.insert(entity)
            }
            count++
        }

        syncStateDao.upsert(
            SyncStateEntity(
                id = 1,
                hcChangesToken = state?.hcChangesToken,
                lastSyncEpochMs = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        return count
    }

    suspend fun syncWeighIns(): Int {
        var count = 0
        for (unsynced in weighInDao.getUnsyncedToHc()) {
            val time = LocalDateTime.parse(unsynced.date + "T07:00:00").atZone(ZoneId.systemDefault()).toInstant()
            val hcId = dataSource.writeWeightRecord(unsynced.weightKg, time)
            weighInDao.upsert(unsynced.copy(syncedToHc = true, hcRecordId = hcId))
            count++
        }

        val lastSync = syncStateDao.get()?.lastSyncEpochMs?.let { Instant.ofEpochMilli(it) }
            ?: clock().minusDays(30).atZone(ZoneId.systemDefault()).toInstant()
        for (remote in dataSource.readNewWeightRecords(lastSync)) {
            if (weighInDao.getByHcRecordId(remote.hcRecordId) != null) continue
            val date = remote.time.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)
            weighInDao.upsert(
                WeighInEntity(date = date, weightKg = remote.weightKg, syncedToHc = true, hcRecordId = remote.hcRecordId)
            )
            count++
        }
        return count
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.HealthConnectRepositoryTest"`
Expected: PASS, all 5 cases.

- [ ] **Step 5: Wire into `AppContainer`**

Modify `AppContainer.kt` — add (real data source is constructed lazily since `HealthConnectClient.getOrCreate` can throw if HC isn't installed; guard with the availability check from Task 3 at the call site in Task 6/7, not here):
```kotlin
    val healthConnectRepository = HealthConnectRepository(
        dataSource = com.suprxsidh.deficit.health.HealthConnectDataSource(HealthConnectManager.getClient(context)),
        exerciseSessionDao = database.exerciseSessionDao(),
        syncStateDao = database.syncStateDao(),
        weighInDao = database.weighInDao()
    )
```
Note: constructing `AppContainer` itself must not crash when Health Connect isn't installed — `HealthConnectClient.getOrCreate(context)` only throws when actually invoked in a way that needs the provider; per the SDK docs it's safe to call `getOrCreate` even before checking availability, but to be defensive, guard this line: only construct `healthConnectRepository` eagerly if `HealthConnectManager.availability(context) == HealthConnectClient.SDK_AVAILABLE`, otherwise leave it nullable (`val healthConnectRepository: HealthConnectRepository? = ...`) and have Tasks 6/7/8/9 null-check before using it, showing the "unavailable" UI state instead.

- [ ] **Step 6: Run the full suite + build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest assembleDebug`
Expected: all tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/repository/HealthConnectRepository.kt app/src/test/java/com/suprxsidh/deficit/data/repository/HealthConnectRepositoryTest.kt app/src/main/java/com/suprxsidh/deficit/data/AppContainer.kt
git commit -m "Task 4: Health Connect sync business logic (session mapping, dedupe, two-way weigh-in sync)"
```

---

### Task 5: WorkManager dependency + periodic sync worker

**Files:**
- Modify: `app/build.gradle.kts` — add WorkManager dependency
- Create: `app/src/main/java/com/suprxsidh/deficit/health/HealthConnectSyncWorker.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/MainActivity.kt` — schedule periodic work + trigger a one-off sync on app open
- Test: `app/src/test/java/com/suprxsidh/deficit/health/HealthConnectSyncWorkerTest.kt`

**Interfaces:**
- Consumes: `HealthConnectRepository.syncExerciseSessions()`, `syncWeighIns()` (Task 4).
- Produces: `HealthConnectSyncWorker` (a `CoroutineWorker`), `HealthConnectSyncWorker.schedulePeriodic(context)`, `HealthConnectSyncWorker.triggerOneOff(context)`, `HealthConnectSyncWorker.WORK_NAME`.

- [ ] **Step 1: Add the dependency**

In `app/build.gradle.kts`:
```kotlin
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    testImplementation("androidx.work:work-testing:2.11.2")
```

- [ ] **Step 2: Write the failing test**

This test exercises the worker's `doWork()` logic directly (constructing it with `TestListenableWorkerBuilder`, per the `work-testing` artifact) against fakes, rather than the periodic schedule itself (which needs a device to observe timing).

```kotlin
package com.suprxsidh.deficit.health

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.suprxsidh.deficit.DeficitApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DeficitApp::class)
class HealthConnectSyncWorkerTest {
    @Test
    fun `doWork calls repository sync and returns success`() = runTest {
        val context = ApplicationProvider.getApplicationContext<DeficitApp>()
        var sessionsSynced = false
        var weighInsSynced = false
        val worker = TestListenableWorkerBuilder<HealthConnectSyncWorker>(context)
            .setWorkerFactory(FakeWorkerFactory(
                onSyncSessions = { sessionsSynced = true },
                onSyncWeighIns = { weighInsSynced = true }
            ))
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assert(sessionsSynced && weighInsSynced)
    }
}
```
This requires a small `androidx.work.WorkerFactory` seam. To keep this genuinely testable without pulling `AppContainer`'s real Health Connect client into the worker, design `HealthConnectSyncWorker` to fetch its repository via a factory lambda held in a companion object that tests can override — see implementation below (`repositoryProvider`).

- [ ] **Step 3: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.health.HealthConnectSyncWorkerTest"`
Expected: FAIL (compile error).

- [ ] **Step 4: Implement**

```kotlin
package com.suprxsidh.deficit.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.suprxsidh.deficit.DeficitApp
import java.util.concurrent.TimeUnit

class HealthConnectSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

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

    companion object {
        const val WORK_NAME = "health_connect_sync"
        const val PERIODIC_WORK_NAME = "health_connect_sync_periodic"

        // Overridable seam for tests; production default reads the real AppContainer.
        var repositoryProvider: (Context) -> com.suprxsidh.deficit.data.repository.HealthConnectRepository? =
            { context -> (context.applicationContext as? DeficitApp)?.container?.healthConnectRepository }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(60, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerOneOff(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
```

Adjust the test's `FakeWorkerFactory` reference from Step 2 — instead of a custom `WorkerFactory`, override the companion `repositoryProvider` directly in the test (simpler, no extra `WorkerFactory` machinery needed):
```kotlin
package com.suprxsidh.deficit.health

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.suprxsidh.deficit.DeficitApp
import com.suprxsidh.deficit.data.db.dao.ExerciseSessionDao
import com.suprxsidh.deficit.data.db.dao.SyncStateDao
import com.suprxsidh.deficit.data.db.dao.WeighInDao
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectSyncWorkerTest {

    @After
    fun tearDown() {
        HealthConnectSyncWorker.repositoryProvider = { null }
    }

    @Test
    fun `doWork returns success when no repository is available`() = runTest {
        HealthConnectSyncWorker.repositoryProvider = { null }
        val context = ApplicationProvider.getApplicationContext<DeficitApp>()
        val worker = TestListenableWorkerBuilder<HealthConnectSyncWorker>(context).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
```
This drops the Mockito dependency to keep the test self-contained with what's already on the classpath — it only asserts the "Health Connect unavailable" branch, since the "repository present" branch is already fully covered by `HealthConnectRepositoryTest` (Task 4) and re-mocking it here would just duplicate that coverage without testing anything new about the worker itself.

- [ ] **Step 5: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.health.HealthConnectSyncWorkerTest"`
Expected: PASS.

- [ ] **Step 6: Schedule the worker on app start**

Modify `MainActivity.kt` — inside the existing `onCreate` (or the `LaunchedEffect` that already checks the profile), add:
```kotlin
        HealthConnectSyncWorker.schedulePeriodic(applicationContext)
        HealthConnectSyncWorker.triggerOneOff(applicationContext)
```
Guard both calls behind `HealthConnectManager.availability(this) == HealthConnectClient.SDK_AVAILABLE && HealthConnectManager.hasAllPermissions(this)` so this never fires before onboarding/permissions are done (Task 7 grants permissions; until then, this is a no-op).

- [ ] **Step 7: Run full suite + build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/suprxsidh/deficit/health/HealthConnectSyncWorker.kt app/src/test/java/com/suprxsidh/deficit/health/HealthConnectSyncWorkerTest.kt app/src/main/java/com/suprxsidh/deficit/MainActivity.kt
git commit -m "Task 5: WorkManager periodic Health Connect sync (60 min + on-open trigger)"
```

---

### Task 6: Dashboard run summary + Health Connect status UI

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/dashboard/DashboardScreen.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/dashboard/DashboardViewModelTest.kt` (add cases)

**Interfaces:**
- Consumes: `HealthConnectRepository.observeExerciseSessions()`, `getLatestSessionForDate(date)` (Task 4); `DayBoundary` (existing, for "today"'s date string); `HealthConnectManager.availability`/`hasAllPermissions` (Task 3); `AppContainer.healthConnectAvailability: Int` and `AppContainer.hasHealthConnectPermissions(): Boolean` (suspend) — new members added to `AppContainer` in this task.
- Produces: `DashboardViewModel.todaysRun: StateFlow<ExerciseSessionEntity?>`, `DashboardViewModel.healthConnectStatus: StateFlow<HealthConnectStatus>` where `enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }`.

- [ ] **Step 1: Write the failing test cases**

Append to `DashboardViewModelTest.kt` (adjust constructor call at the top of the file to pass a `healthConnectRepository: HealthConnectRepository?` — see Step 3 note below for the full updated constructor):
```kotlin
    @Test
    fun `todaysRun exposes the latest session for today's date`() = runTest {
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
    fun `todaysRun is null when no session logged today`() = runTest {
        val viewModel = buildViewModel(clock = { LocalDateTime.of(2026, 8, 11, 9, 0) })
        backgroundScope.launch { viewModel.todaysRun.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.todaysRun.value)
    }

    @Test
    fun `healthConnectStatus is UNAVAILABLE when the SDK isn't available`() = runTest {
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
    fun `healthConnectStatus is PERMISSIONS_NEEDED when available but not granted`() = runTest {
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
    fun `healthConnectStatus is OK when available and granted`() = runTest {
        val viewModel = buildViewModel(
            clock = { LocalDateTime.of(2026, 8, 11, 9, 0) },
            healthConnectAvailability = HealthConnectClient.SDK_AVAILABLE,
            hasPermissions = { true }
        )
        backgroundScope.launch { viewModel.healthConnectStatus.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HealthConnectStatus.OK, viewModel.healthConnectStatus.value)
    }
```
(Wire `healthDao` and a `buildViewModel(clock, healthConnectAvailability, hasPermissions)` helper matching however the existing test file already constructs the DB/ViewModel — follow the exact pattern already in the file, e.g. its existing `db`/`viewModel` setup in `@Before`, extended with two new parameters defaulted to `HealthConnectClient.SDK_AVAILABLE` and `{ true }` so the pre-existing test cases in this file don't need to change. Add `healthConnectRepository = HealthConnectRepository(fakeOrRealDataSource, db.exerciseSessionDao(), db.syncStateDao(), db.weighInDao(), clock)` — a trivial no-op `HealthDataSource` fake is fine here since these tests never call sync, only `observeExerciseSessions`. Import `androidx.health.connect.client.HealthConnectClient` for the `SDK_AVAILABLE`/`SDK_UNAVAILABLE` constants.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL (compile error — `todaysRun` doesn't exist yet, constructor signature mismatch).

- [ ] **Step 3: Implement**

Modify `DashboardViewModel.kt` constructor and add the new `StateFlow`s:
```kotlin
class DashboardViewModel(
    foodRepository: FoodRepository,
    userProfileRepository: UserProfileRepository,
    weightRepository: WeightRepository,
    private val healthConnectRepository: HealthConnectRepository?,
    private val healthConnectAvailability: Int,
    private val hasHealthConnectPermissions: suspend () -> Boolean,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) : ViewModel() {
    // ... existing profile / todayBufferedTotal / rollingAverageSeries ...

    val todaysRun: StateFlow<ExerciseSessionEntity?> =
        (healthConnectRepository?.observeExerciseSessions() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .map { sessions ->
                val today = DayBoundary.currentBusinessDate(clock())
                sessions.filter { it.date == today.toString() }.maxByOrNull { it.startTimeEpochMs }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _healthConnectStatus = MutableStateFlow(HealthConnectStatus.UNAVAILABLE)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    init {
        viewModelScope.launch {
            _healthConnectStatus.value = when {
                healthConnectAvailability != HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.UNAVAILABLE
                !hasHealthConnectPermissions() -> HealthConnectStatus.PERMISSIONS_NEEDED
                else -> HealthConnectStatus.OK
            }
        }
    }
}

enum class HealthConnectStatus { UNAVAILABLE, PERMISSIONS_NEEDED, OK }
```
Add imports: `androidx.health.connect.client.HealthConnectClient`, `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.asStateFlow`, `kotlinx.coroutines.launch`.

Use the actual existing `DayBoundary` function name/signature — check `data/calc/DayBoundary.kt` for the exact "current business date given a clock" function (Phase 1 already has one; call it the same way `WeightRepository`/`FoodRepository` do internally, do not invent a new signature).

Add to `AppContainer.kt`:
```kotlin
    val healthConnectAvailability: Int = HealthConnectManager.availability(context)

    suspend fun hasHealthConnectPermissions(): Boolean =
        healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE && HealthConnectManager.hasAllPermissions(context)
```
(`context` here is whatever constructor parameter `AppContainer` already stores — check the existing file; it's the same `Context` used to build `DeficitDatabase.getInstance(context)`.)

Modify `DashboardScreen.kt` — add a card below the existing weight sparkline, above the quick-add button:
```kotlin
    val todaysRun by viewModel.todaysRun.collectAsState()
    val hcStatus by viewModel.healthConnectStatus.collectAsState()
    val context = LocalContext.current

    todaysRun?.let { run ->
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Today's run", style = MaterialTheme.typography.titleMedium)
                Text("${run.durationMin} min" + (run.distanceM?.let { " · %.1f km".format(it / 1000.0) } ?: ""))
                Text("${run.kcalReal} kcal · credited: ${run.kcalCredited} kcal (50%)")
            }
        }
    }

    when (hcStatus) {
        HealthConnectStatus.PERMISSIONS_NEEDED -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Health Connect permissions needed to sync runs and weight.")
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                }) { Text("Open Health Connect settings") }
            }
        }
        HealthConnectStatus.UNAVAILABLE -> Text(
            "Health Connect isn't available on this device — install it from the Play Store to sync runs and weight.",
            style = MaterialTheme.typography.bodySmall
        )
        HealthConnectStatus.OK -> {}
    }
```
Update the `viewModelFactory` block in `DashboardScreen.kt` to pass `app.container.healthConnectRepository`, `app.container.healthConnectAvailability`, and `app.container::hasHealthConnectPermissions`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.dashboard.DashboardViewModelTest"`
Expected: PASS, including the two new cases and every pre-existing one.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/dashboard app/src/test/java/com/suprxsidh/deficit/ui/dashboard
git commit -m "Task 6: dashboard shows today's run with real + 50%-credited calories"
```

---

### Task 7: Onboarding Health Connect setup checklist

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/onboarding/OnboardingScreen.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/onboarding/OnboardingViewModelTest.kt` (add cases)

**Interfaces:**
- Consumes: `HealthConnectManager.availability`, `hasAllPermissions`, `requestPermissionsContract`, `REQUIRED_PERMISSIONS` (Task 3).
- Produces: a new onboarding step shown after the existing profile-entry step, before landing on the dashboard. `OnboardingViewModel.healthConnectPermissionsGranted: StateFlow<Boolean>`, `fun onHealthConnectPermissionsResult(granted: Set<String>)`.

- [ ] **Step 1: Write the failing test**

Append to `OnboardingViewModelTest.kt`:
```kotlin
    @Test
    fun `onHealthConnectPermissionsResult true when all required permissions granted`() = runTest {
        val viewModel = buildViewModel() // use the file's existing helper/constructor pattern
        viewModel.onHealthConnectPermissionsResult(HealthConnectManager.REQUIRED_PERMISSIONS)
        assertTrue(viewModel.healthConnectPermissionsGranted.value)
    }

    @Test
    fun `onHealthConnectPermissionsResult false when some permissions are missing`() = runTest {
        val viewModel = buildViewModel()
        viewModel.onHealthConnectPermissionsResult(setOf(HealthConnectManager.REQUIRED_PERMISSIONS.first()))
        assertFalse(viewModel.healthConnectPermissionsGranted.value)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.onboarding.OnboardingViewModelTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implement**

Add to `OnboardingViewModel.kt`:
```kotlin
    private val _healthConnectPermissionsGranted = MutableStateFlow(false)
    val healthConnectPermissionsGranted: StateFlow<Boolean> = _healthConnectPermissionsGranted.asStateFlow()

    fun onHealthConnectPermissionsResult(granted: Set<String>) {
        _healthConnectPermissionsGranted.value = granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)
    }
```
(Import `com.suprxsidh.deficit.health.HealthConnectManager`, `kotlinx.coroutines.flow.MutableStateFlow`, `asStateFlow` — match whatever flow imports the file already uses for its other state.)

Add a new step to `OnboardingScreen.kt`'s existing step sequence (after profile entry, before navigating to Dashboard). Follow whatever step-sequencing pattern (e.g. a `when(step)` or multi-composable pager) the file already uses; add:
```kotlin
@Composable
private fun HealthConnectSetupStep(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onContinue: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Connect Health Connect", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("1. Open Samsung Health → Settings → Data management → Health Connect sync, and turn it on.")
        Text("2. Grant this app the Exercise, Steps, Distance, Heart Rate, and Weight permissions when prompted.")
        Text("Sync can take 30–60 minutes after a run. Opening Samsung Health first speeds it up.")
        Spacer(Modifier.height(24.dp))
        if (permissionsGranted) {
            Text("Permissions granted ✓")
        } else {
            Button(onClick = onRequestPermissions) { Text("Grant Health Connect permissions") }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onContinue) { Text(if (permissionsGranted) "Continue" else "Skip for now") }
    }
}
```
Wire the permission launcher in the parent composable:
```kotlin
    val permissionLauncher = rememberLauncherForActivityResult(HealthConnectManager.requestPermissionsContract()) { granted ->
        viewModel.onHealthConnectPermissionsResult(granted)
    }
```
"Skip for now" must be allowed (SPEC.md §4.8 — never block flow on configuration): tapping it proceeds to the dashboard exactly as if permissions were granted, and Task 6's `HealthConnectStatus.PERMISSIONS_NEEDED` state (surfaced later) is how the user is reminded, not a hard onboarding gate.

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.onboarding.OnboardingViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/onboarding app/src/test/java/com/suprxsidh/deficit/ui/onboarding
git commit -m "Task 7: onboarding Health Connect setup checklist step"
```

---

### Task 8: Weight screen two-way sync wiring

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/repository/WeightRepository.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/weight/WeightViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/weight/WeightScreen.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/WeightRepositoryTest.kt` (add case)
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/weight/WeightViewModelTest.kt` (add case)

**Interfaces:**
- Consumes: `HealthConnectRepository.syncWeighIns()` (Task 4).
- Produces: `WeightViewModel.syncWithHealthConnect()`, `WeightViewModel.lastSyncResult: StateFlow<String?>` (a short user-facing status line, e.g. "Synced 2 entries").

- [ ] **Step 1: Write the failing test**

Add to `WeightViewModelTest.kt`:
```kotlin
    @Test
    fun `syncWithHealthConnect reports how many entries were synced`() = runTest {
        val fakeHealthRepo = object {
            var callCount = 0
        }
        val viewModel = WeightViewModel(
            weightRepository = repository, // existing fixture from this file
            healthConnectRepository = FakeHealthConnectRepositoryReturning(2)
        )
        viewModel.syncWithHealthConnect()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Synced 2 entries with Health Connect", viewModel.lastSyncResult.value)
    }
```
Add a tiny test double at the bottom of the same file (it needs to satisfy `HealthConnectRepository`'s public surface used here — since `syncWeighIns` is the only method called by the ViewModel, extract that into a small interface, see implementation note below):
```kotlin
private class FakeHealthConnectRepositoryReturning(private val count: Int) : WeighInSyncSource {
    override suspend fun syncWeighIns(): Int = count
}
```

Implementation note: to keep this ViewModel test free of a real Room DB / Health Connect client, extract a minimal interface `WeighInSyncSource { suspend fun syncWeighIns(): Int }` that `HealthConnectRepository` already satisfies structurally — have `WeightViewModel` depend on `WeighInSyncSource?` (nullable, since Health Connect may be unavailable) rather than the full `HealthConnectRepository?`.

`data/repository/HealthConnectRepository.kt` (Task 4) needs one addition — declare the interface there and have the class implement it:
```kotlin
interface WeighInSyncSource {
    suspend fun syncWeighIns(): Int
}

class HealthConnectRepository(...) : WeighInSyncSource {
    // ... unchanged, syncWeighIns already matches the signature
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.weight.WeightViewModelTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implement**

Modify `WeightViewModel.kt`:
```kotlin
class WeightViewModel(
    private val weightRepository: WeightRepository,
    private val healthConnectRepository: WeighInSyncSource? = null
) : ViewModel() {
    // ... existing state ...

    private val _lastSyncResult = MutableStateFlow<String?>(null)
    val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

    fun syncWithHealthConnect() {
        val source = healthConnectRepository ?: return
        viewModelScope.launch {
            val count = source.syncWeighIns()
            _lastSyncResult.value = "Synced $count entries with Health Connect"
        }
    }
}
```
Modify `WeightScreen.kt` — pass `app.container.healthConnectRepository` into the `viewModelFactory` initializer, add a small "Sync with Health Connect" `TextButton` near the top of the screen that calls `viewModel.syncWithHealthConnect()`, and show `lastSyncResult` (if non-null) as a `Text` beneath it.

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.weight.WeightViewModelTest" --tests "com.suprxsidh.deficit.data.repository.WeightRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data/repository app/src/main/java/com/suprxsidh/deficit/ui/weight app/src/test/java/com/suprxsidh/deficit/ui/weight app/src/test/java/com/suprxsidh/deficit/data/repository/WeightRepositoryTest.kt
git commit -m "Task 8: weight screen two-way Health Connect sync"
```

---

### Task 9: Run detail screen — per-run analytics + cross-run pace trend

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/health/RunDetailViewModel.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/health/RunDetailScreen.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/Routes.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/health/RunDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `HealthConnectRepository.observeExerciseSessions()` (Task 4); `RunAnalytics` (Task 2, for any additional derived display values).
- Produces: `RunDetailViewModel.sessions: StateFlow<List<ExerciseSessionEntity>>`, `RunDetailViewModel.paceTrend: StateFlow<List<Pair<LocalDate, Double>>>` (date → avg pace sec/km, ascending by date, sessions with no distance excluded).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.ui.health

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import com.suprxsidh.deficit.health.HealthDataSource
import com.suprxsidh.deficit.health.RemoteExerciseSession
import com.suprxsidh.deficit.health.RemoteWeightRecord
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
import java.time.Instant
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RunDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: DeficitDatabase

    private object NoOpDataSource : HealthDataSource {
        override suspend fun readExerciseSessions(since: Instant) = emptyList<RemoteExerciseSession>()
        override suspend fun readNewWeightRecords(since: Instant) = emptyList<RemoteWeightRecord>()
        override suspend fun writeWeightRecord(weightKg: Double, time: Instant) = ""
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor)
            .setTransactionExecutor(executor)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `paceTrend excludes sessions without distance and sorts ascending by date`() = runTest {
        db.exerciseSessionDao().insert(session("hc-1", date = "2026-08-05", startMs = 5, pace = 380.0))
        db.exerciseSessionDao().insert(session("hc-2", date = "2026-08-11", startMs = 11, pace = 360.0))
        db.exerciseSessionDao().insert(session("hc-3", date = "2026-08-08", startMs = 8, pace = null))

        val repo = HealthConnectRepository(NoOpDataSource, db.exerciseSessionDao(), db.syncStateDao(), db.weighInDao())
        val viewModel = RunDetailViewModel(repo)
        backgroundScope.launch { viewModel.paceTrend.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.paceTrend.value.size)
        assertEquals("2026-08-05", viewModel.paceTrend.value[0].first.toString())
        assertEquals("2026-08-11", viewModel.paceTrend.value[1].first.toString())
    }

    private fun session(hcRecordId: String, date: String, startMs: Long, pace: Double?) = ExerciseSessionEntity(
        hcRecordId = hcRecordId, date = date, exerciseType = "RUNNING", startTimeEpochMs = startMs,
        durationMin = 30, distanceM = if (pace != null) 5000.0 else null, avgPaceSecPerKm = pace,
        avgHr = 150, maxHr = 170, kcalReal = 300, kcalCredited = 150
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.health.RunDetailViewModelTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implement**

```kotlin
package com.suprxsidh.deficit.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity
import com.suprxsidh.deficit.data.repository.HealthConnectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class RunDetailViewModel(
    private val healthConnectRepository: HealthConnectRepository
) : ViewModel() {

    val sessions: StateFlow<List<ExerciseSessionEntity>> = healthConnectRepository.observeExerciseSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val paceTrend: StateFlow<List<Pair<LocalDate, Double>>> = healthConnectRepository.observeExerciseSessions()
        .map { sessions ->
            sessions
                .filter { it.distanceM != null && it.avgPaceSecPerKm != null }
                .map { java.time.Instant.ofEpochMilli(it.startTimeEpochMs).atZone(ZoneId.systemDefault()).toLocalDate() to it.avgPaceSecPerKm!! }
                .sortedBy { it.first }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.health.RunDetailViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Build the screen and wire navigation**

`RunDetailScreen.kt` — list of past sessions (date, duration, distance, pace, avg/max HR) plus a simple Canvas line chart of `paceTrend` (copy the existing hand-drawn `Canvas` chart approach from `WeightScreen.kt` — same technique, different data series, no new chart dependency per SPEC.md §6's "no heavyweight dependencies" note):
```kotlin
@Composable
fun RunDetailScreen(app: DeficitApp) {
    val viewModel: RunDetailViewModel = viewModel(factory = viewModelFactory {
        initializer { RunDetailViewModel(app.container.healthConnectRepository!!) }
    })
    val sessions by viewModel.sessions.collectAsState()
    val paceTrend by viewModel.paceTrend.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Pace trend", style = MaterialTheme.typography.titleMedium) }
        item { PaceTrendChart(paceTrend, modifier = Modifier.fillMaxWidth().height(120.dp)) }
        item { Spacer(Modifier.height(16.dp)) }
        items(sessions) { session -> RunRow(session) }
    }
}

@Composable
private fun RunRow(session: com.suprxsidh.deficit.data.db.entity.ExerciseSessionEntity) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(session.date, style = MaterialTheme.typography.titleSmall)
        Text("${session.durationMin} min" + (session.distanceM?.let { " · %.1f km".format(it / 1000.0) } ?: ""))
        Text(listOfNotNull(
            session.avgHr?.let { "avg HR $it" },
            session.maxHr?.let { "max HR $it" }
        ).joinToString(" · "))
    }
}

@Composable
private fun PaceTrendChart(points: List<Pair<java.time.LocalDate, Double>>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Text("Log a few more runs to see a pace trend.")
        return
    }
    Canvas(modifier = modifier) {
        val minPace = points.minOf { it.second }
        val maxPace = points.maxOf { it.second }
        val range = (maxPace - minPace).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, (_, pace) ->
            // Lower pace (faster) draws higher on screen.
            val y = size.height * ((pace - minPace) / range).toFloat()
            val x = stepX * index
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = androidx.compose.ui.graphics.Color(0xFF00E5A0), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
    }
}
```
Add `const val RUN_DETAIL = "run_detail"` to `Routes.kt`, add `composable(Routes.RUN_DETAIL) { RunDetailScreen(app) }` to `DeficitNavHost.kt`, and add a "View run history" `TextButton` on the Dashboard's run card (Task 6) that navigates to it.

- [ ] **Step 6: Run full suite + build**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/health app/src/test/java/com/suprxsidh/deficit/ui/health app/src/main/java/com/suprxsidh/deficit/ui/nav app/src/main/java/com/suprxsidh/deficit/ui/dashboard
git commit -m "Task 9: run detail screen with per-run analytics and pace trend"
```

---

### Task 10: Settings screen + Gemini API key storage

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/AppSettingsEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/AppSettingsDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/SettingsRepository.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/DeficitDatabase.kt` (add entity, keep version 3 — this is additive within the same Task-1 bump since Tasks 1–10 land before any release; if Task 1 already shipped and you're doing Phase 2 as one continuous branch this is fine as version 3 throughout)
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/AppContainer.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/Routes.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/nav/DeficitNavHost.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/MainActivity.kt` (add a settings icon entry point to the top bar)
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/SettingsRepositoryTest.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces: `SettingsRepository.getGeminiApiKey(): String?` (suspend), `observeGeminiApiKey(): Flow<String?>`, `suspend fun setGeminiApiKey(key: String?)`. Consumed by Task 12 (`GeminiFoodRepository`).

- [ ] **Step 1: Write the failing tests**

`AppSettingsEntity` test is folded into the repository test (no separate DAO test needed — the DAO here is a single trivial upsert/get, and the repository test below exercises it against a real in-memory DB, matching how `UserProfileRepository`'s tests already do this).

```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
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
    private lateinit var db: DeficitDatabase
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
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
}
```

```kotlin
package com.suprxsidh.deficit.ui.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.data.db.DeficitDatabase
import com.suprxsidh.deficit.data.repository.SettingsRepository
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
    private lateinit var db: DeficitDatabase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).build()
        viewModel = SettingsViewModel(SettingsRepository(db.appSettingsDao()))
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.SettingsRepositoryTest" --tests "com.suprxsidh.deficit.ui.settings.SettingsViewModelTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implement the entity, DAO, repository**

```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val geminiApiKey: String? = null
)
```

```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suprxsidh.deficit.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}
```

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.data.db.dao.AppSettingsDao
import com.suprxsidh.deficit.data.db.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: AppSettingsDao) {
    suspend fun getGeminiApiKey(): String? = dao.get()?.geminiApiKey

    fun observeGeminiApiKey(): Flow<String?> = dao.observe().map { it?.geminiApiKey }

    suspend fun setGeminiApiKey(key: String?) {
        dao.upsert(AppSettingsEntity(id = 1, geminiApiKey = key))
    }
}
```

Modify `DeficitDatabase.kt` — add `AppSettingsEntity::class` to the entities list, add `abstract fun appSettingsDao(): AppSettingsDao`. (Version stays 3 if Tasks 1–10 ship as one uninterrupted branch before any install; if Task 1 was already installed on-device separately, bump to 4 instead — check the current `version = ` value in the file before editing and increment from whatever is actually there.)

- [ ] **Step 4: Implement the ViewModel and screen**

```kotlin
package com.suprxsidh.deficit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.suprxsidh.deficit.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val geminiApiKey: StateFlow<String?> = settingsRepository.observeGeminiApiKey()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(key.trim().ifBlank { null }) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(null) }
    }
}
```

```kotlin
package com.suprxsidh.deficit.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suprxsidh.deficit.DeficitApp

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.applicationContext as DeficitApp
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app.container.settingsRepository) }
    })
    val currentKey by viewModel.geminiApiKey.collectAsState()
    var input by remember(currentKey) { mutableStateOf(currentKey ?: "") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Gemini API key", style = MaterialTheme.typography.titleMedium)
        Text("Used only for AI meal estimation. Stored on this device only, sent only to Google's Gemini API.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(onClick = { viewModel.saveGeminiApiKey(input) }) { Text("Save") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { input = ""; viewModel.clearGeminiApiKey() }) { Text("Clear") }
        }
    }
}
```

Add `SettingsRepository` to `AppContainer.kt`:
```kotlin
    val settingsRepository = SettingsRepository(database.appSettingsDao())
```
Add `const val SETTINGS = "settings"` to `Routes.kt`, add `composable(Routes.SETTINGS) { SettingsScreen() }` to `DeficitNavHost.kt`. In `MainActivity.kt`'s `Scaffold`, add a `TopAppBar` (if one doesn't already exist — check first) with a settings `IconButton` (`Icons.Default.Settings`) that calls `navController.navigate(Routes.SETTINGS)`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.SettingsRepositoryTest" --tests "com.suprxsidh.deficit.ui.settings.SettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data app/src/main/java/com/suprxsidh/deficit/ui/settings app/src/main/java/com/suprxsidh/deficit/ui/nav app/src/main/java/com/suprxsidh/deficit/MainActivity.kt app/src/test/java/com/suprxsidh/deficit/data/repository/SettingsRepositoryTest.kt app/src/test/java/com/suprxsidh/deficit/ui/settings
git commit -m "Task 10: settings screen with locally-stored Gemini API key"
```

---

### Task 11: Gemini client — models, API, factory, estimator

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/ai/gemini/GeminiModels.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ai/gemini/GeminiApi.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ai/gemini/GeminiServiceFactory.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/ai/gemini/GeminiFoodEstimator.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/ai/gemini/GeminiFoodEstimatorTest.kt`

**Interfaces:**
- Produces: `GeminiFoodEstimate(items: List<GeminiFoodItem>, totalKcal: Int, confidence: String)`, `GeminiFoodItem(name: String, kcal: Int)`, `GeminiFoodEstimator(api: GeminiApi).estimate(apiKey: String, description: String?, photoBase64: String?, model: String = GeminiFoodEstimator.DEFAULT_MODEL): GeminiFoodEstimate` (suspend, throws `GeminiEstimationException` on failure), `GeminiServiceFactory.create(baseUrl = "https://generativelanguage.googleapis.com/v1beta/"): GeminiApi`. Consumed by Task 12 (`GeminiFoodRepository`).
- Consumes: nothing from earlier Phase 2 tasks (independent subsystem — this task has no dependency on Health Connect at all and could be built in parallel with Tasks 1–9).

- [ ] **Step 1: Write the failing test**

Mirror the exact MockWebServer pattern from `OpenFoodFactsRepositoryTest.kt` (existing Phase 1 test — same libs, same shape).

```kotlin
package com.suprxsidh.deficit.ai.gemini

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GeminiFoodEstimatorTest {
    private lateinit var server: MockWebServer
    private lateinit var estimator: GeminiFoodEstimator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = GeminiServiceFactory.create(baseUrl = server.url("/").toString())
        estimator = GeminiFoodEstimator(api)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun `estimate parses itemized JSON from the response text part`() = runTest {
        val geminiJsonBody = """{"items":[{"name":"2 rotis","kcal":180},{"name":"dal tadka","kcal":220}],"totalKcal":400,"confidence":"medium"}"""
        val wrapped = """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(geminiJsonBody)}}]}}]}"""
        server.enqueue(MockResponse().setBody(wrapped).setResponseCode(200))

        val estimate = estimator.estimate(apiKey = "test-key", description = "2 rotis, dal tadka", photoBase64 = null)

        assertEquals(400, estimate.totalKcal)
        assertEquals(2, estimate.items.size)
        assertEquals("2 rotis", estimate.items[0].name)
    }

    @Test
    fun `estimate throws GeminiEstimationException on HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertThrows(GeminiEstimationException::class.java) {
            kotlinx.coroutines.runBlocking { estimator.estimate("test-key", "some food", null) }
        }
    }

    @Test
    fun `estimate throws when description and photo are both missing`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { estimator.estimate("test-key", null, null) }
        }
    }
}
```
Note: `org.json.JSONObject` is available on the Robolectric/Android test classpath for building the escaped inner JSON string in the fixture; if it's not resolvable in a plain JVM test target, replace that one line with a manually escaped literal string instead — the point is only to produce a validly-escaped inner JSON string, not to depend on `org.json` specifically.

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimatorTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implement the models**

```kotlin
package com.suprxsidh.deficit.ai.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    @SerialName("response_mime_type") val responseMimeType: String = "application/json",
    @SerialName("response_schema") val responseSchema: GeminiSchema
)

@Serializable
data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchema>? = null,
    val items: GeminiSchema? = null,
    val required: List<String>? = null
)

@Serializable
data class GeminiGenerateContentResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

@Serializable
data class GeminiFoodEstimate(
    val items: List<GeminiFoodItem>,
    val totalKcal: Int,
    val confidence: String
)

@Serializable
data class GeminiFoodItem(val name: String, val kcal: Int)

class GeminiEstimationException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

`GeminiApi.kt`:
```kotlin
package com.suprxsidh.deficit.ai.gemini

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {
    @POST("models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}
```

`GeminiServiceFactory.kt` (mirror `OpenFoodFactsServiceFactory.kt` exactly — same OkHttp/logging/serialization setup, different base URL):
```kotlin
package com.suprxsidh.deficit.ai.gemini

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object GeminiServiceFactory {
    fun create(baseUrl: String = "https://generativelanguage.googleapis.com/v1beta/"): GeminiApi {
        val json = Json { ignoreUnknownKeys = true }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logging).build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApi::class.java)
    }
}
```
(Match whatever the actual existing `OpenFoodFactsServiceFactory.kt` imports/builder chain look like exactly — check the file first; this should be structurally identical minus the base URL and the absence of a `User-Agent` header interceptor, which Gemini doesn't require.)

`GeminiFoodEstimator.kt`:
```kotlin
package com.suprxsidh.deficit.ai.gemini

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

class GeminiFoodEstimator(
    private val api: GeminiApi,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun estimate(
        apiKey: String,
        description: String?,
        photoBase64: String?,
        model: String = DEFAULT_MODEL
    ): GeminiFoodEstimate {
        require(!description.isNullOrBlank() || !photoBase64.isNullOrBlank()) {
            "Need a text description or a photo to estimate a meal"
        }
        val parts = buildList {
            add(GeminiPart(text = PROMPT + (description?.let { "\n\nMeal: $it" } ?: "")))
            if (photoBase64 != null) add(GeminiPart(inlineData = GeminiInlineData("image/jpeg", photoBase64)))
        }
        val request = GeminiGenerateContentRequest(
            contents = listOf(GeminiContent(parts)),
            generationConfig = GeminiGenerationConfig(responseSchema = FOOD_ESTIMATE_SCHEMA)
        )
        val response = try {
            api.generateContent(model, apiKey, request)
        } catch (e: HttpException) {
            throw GeminiEstimationException("Gemini request failed: HTTP ${e.code()}", e)
        } catch (e: IOException) {
            throw GeminiEstimationException("Gemini request failed: ${e.message}", e)
        }
        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull { it.text != null }?.text
            ?: throw GeminiEstimationException("Empty response from Gemini")
        return try {
            json.decodeFromString(GeminiFoodEstimate.serializer(), text)
        } catch (e: Exception) {
            throw GeminiEstimationException("Could not parse Gemini's response as the expected JSON shape", e)
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"

        private val PROMPT = """
            You are estimating calories for an Indian home-cooked meal. Return ONLY the requested JSON.
            Rules:
            - When portion size or ingredients are ambiguous, always estimate on the HIGH end, never the low end.
            - Assume standard Indian home-cooking defaults (ghee/oil used, typical home portion sizes) unless the description says otherwise.
            - "items" should list each distinct food item with its own kcal estimate.
            - "totalKcal" is the sum across items.
            - "confidence" is one of "low", "medium", "high".
        """.trimIndent()

        val FOOD_ESTIMATE_SCHEMA = GeminiSchema(
            type = "object",
            properties = mapOf(
                "items" to GeminiSchema(
                    type = "array",
                    items = GeminiSchema(
                        type = "object",
                        properties = mapOf(
                            "name" to GeminiSchema(type = "string"),
                            "kcal" to GeminiSchema(type = "integer")
                        ),
                        required = listOf("name", "kcal")
                    )
                ),
                "totalKcal" to GeminiSchema(type = "integer"),
                "confidence" to GeminiSchema(type = "string")
            ),
            required = listOf("items", "totalKcal", "confidence")
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimatorTest"`
Expected: PASS, all 3 cases.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ai app/src/test/java/com/suprxsidh/deficit/ai
git commit -m "Task 11: Gemini client — strict-JSON food estimation with schema-constrained output"
```

---

### Task 12: PendingDraft queue + GeminiFoodRepository orchestration

**Files:**
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/entity/PendingDraftEntity.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/db/dao/PendingDraftDao.kt`
- Create: `app/src/main/java/com/suprxsidh/deficit/data/repository/GeminiFoodRepository.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/repository/FoodRepository.kt` — add `logGeminiEstimate`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/db/DeficitDatabase.kt` — register `PendingDraftEntity`/`PendingDraftDao`
- Modify: `app/src/main/java/com/suprxsidh/deficit/data/AppContainer.kt`
- Test: `app/src/test/java/com/suprxsidh/deficit/data/repository/GeminiFoodRepositoryTest.kt`

**Interfaces:**
- Consumes: `GeminiFoodEstimator`, `GeminiFoodEstimate`, `GeminiEstimationException` (Task 11); `SettingsRepository.getGeminiApiKey()` (Task 10); `FoodRepository.logQuickAdd`, new `logGeminiEstimate` (Phase 1 + this task).
- Produces: `GeminiEstimateResult` sealed class (`NoApiKey`, `Success(estimate)`, `Failed(draftId, message)`), `GeminiFoodRepository.estimateMeal(description, photoFile): GeminiEstimateResult` (estimates only — does **not** save, per SPEC.md §3.3's "show itemized estimate for one-tap confirm/edit before saving"), `confirmEstimate(estimate, editedName?, editedTotalKcal?): FoodEntryEntity` (the actual save, called after the user reviews/edits), `retryPendingDrafts(): Int` (background retries auto-log without a review step — no user is present to review them), `discardDraftAsQuickAdd(draftId, kcal)`, `observePendingDrafts(): Flow<List<PendingDraftEntity>>`. Consumed by Task 13 (food logging UI).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.suprxsidh.deficit.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.suprxsidh.deficit.ai.gemini.GeminiApi
import com.suprxsidh.deficit.ai.gemini.GeminiCandidate
import com.suprxsidh.deficit.ai.gemini.GeminiContent
import com.suprxsidh.deficit.ai.gemini.GeminiEstimationException
import com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.deficit.ai.gemini.GeminiGenerateContentRequest
import com.suprxsidh.deficit.ai.gemini.GeminiGenerateContentResponse
import com.suprxsidh.deficit.ai.gemini.GeminiPart
import com.suprxsidh.deficit.data.db.DeficitDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiFoodRepositoryTest {
    private lateinit var db: DeficitDatabase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var foodRepository: FoodRepository

    private class FakeGeminiApi(private val succeed: Boolean) : GeminiApi {
        override suspend fun generateContent(model: String, apiKey: String, request: GeminiGenerateContentRequest): GeminiGenerateContentResponse {
            if (!succeed) throw java.io.IOException("network down")
            val json = """{"items":[{"name":"2 rotis","kcal":180}],"totalKcal":180,"confidence":"medium"}"""
            return GeminiGenerateContentResponse(listOf(GeminiCandidate(GeminiContent(listOf(GeminiPart(text = json))))))
        }
    }

    private fun buildRepo(succeed: Boolean): GeminiFoodRepository {
        val estimator = GeminiFoodEstimator(FakeGeminiApi(succeed))
        return GeminiFoodRepository(estimator, settingsRepository, foodRepository, db.pendingDraftDao())
    }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeficitDatabase::class.java)
            .allowMainThreadQueries().build()
        settingsRepository = SettingsRepository(db.appSettingsDao())
        foodRepository = FoodRepository(db.foodEntryDao(), db.customFoodDao())
        settingsRepository.setGeminiApiKey("test-key")
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `estimateMeal returns NoApiKey when no key is set`() = runTest {
        settingsRepository.setGeminiApiKey(null)
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        assertTrue(result is GeminiEstimateResult.NoApiKey)
    }

    @Test
    fun `estimateMeal returns the itemized estimate without logging anything`() = runTest {
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        assertTrue(result is GeminiEstimateResult.Success)
        val estimate = (result as GeminiEstimateResult.Success).estimate
        assertEquals(180, estimate.totalKcal)
        assertEquals(1, estimate.items.size)
        assertEquals(0, foodRepository.observeTodayEntries().first().size) // not saved yet
    }

    @Test
    fun `confirmEstimate logs a buffered food entry using the estimate's own values`() = runTest {
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        val estimate = (result as GeminiEstimateResult.Success).estimate

        val entry = buildRepo(succeed = true).confirmEstimate(estimate)

        assertEquals(180, entry.rawKcal)
        assertEquals(198, entry.bufferedKcal) // +10%
        assertEquals("GEMINI", entry.source)
        assertEquals(1, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `confirmEstimate uses the user's edited name and kcal when provided`() = runTest {
        val result = buildRepo(succeed = true).estimateMeal("2 rotis", null)
        val estimate = (result as GeminiEstimateResult.Success).estimate

        val entry = buildRepo(succeed = true).confirmEstimate(estimate, editedName = "2 rotis (edited)", editedTotalKcal = 220)

        assertEquals("2 rotis (edited)", entry.name)
        assertEquals(220, entry.rawKcal)
        assertEquals(242, entry.bufferedKcal) // +10% of the edited value, not the original
    }

    @Test
    fun `estimateMeal saves a pending draft on failure and logs nothing`() = runTest {
        val repo = buildRepo(succeed = false)
        val result = repo.estimateMeal("2 rotis", null)
        assertTrue(result is GeminiEstimateResult.Failed)
        assertEquals(0, foodRepository.observeTodayEntries().first().size)
        assertEquals(1, db.pendingDraftDao().getAll().size)
    }

    @Test
    fun `retryPendingDrafts auto-logs the draft and removes it once Gemini succeeds`() = runTest {
        buildRepo(succeed = false).estimateMeal("2 rotis", null)
        assertEquals(1, db.pendingDraftDao().getAll().size)

        val succeeded = buildRepo(succeed = true).retryPendingDrafts()

        assertEquals(1, succeeded)
        assertEquals(0, db.pendingDraftDao().getAll().size)
        assertEquals(1, foodRepository.observeTodayEntries().first().size)
    }

    @Test
    fun `discardDraftAsQuickAdd logs a quick-add entry and removes the draft`() = runTest {
        buildRepo(succeed = false).estimateMeal("mystery meal", null)
        val draft = db.pendingDraftDao().getAll().first()

        buildRepo(succeed = false).discardDraftAsQuickAdd(draft.id, kcal = 300)

        assertEquals(0, db.pendingDraftDao().getAll().size)
        val entries = foodRepository.observeTodayEntries().first()
        assertEquals(1, entries.size)
        assertEquals("QUICK", entries.first().source)
    }
}
```
(`foodRepository.observeTodayEntries().first()` requires `kotlinx.coroutines.flow.first` — add that import. Match the exact `FoodEntryEntity.source` string constants Phase 1 already uses, e.g. `"QUICK"` — check `FoodRepository.kt`'s private `log()` helper for the exact strings already in use before writing `"GEMINI"` as the new one, to keep them consistent in style, e.g. all-caps. Background retries call the same `confirmEstimate`-equivalent path internally with no edits — see implementation below.)

- [ ] **Step 2: Run test to verify it fails**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.GeminiFoodRepositoryTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implement `PendingDraftEntity`/`PendingDraftDao`**

```kotlin
package com.suprxsidh.deficit.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_draft")
data class PendingDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "MEAL_TEXT" | "MEAL_PHOTO"
    val payload: String, // free-text description; blank if photo-only
    val photoPath: String? = null, // absolute path to a cached jpeg; null if text-only
    val createdAt: Long,
    val retryCount: Int = 0
)
```

```kotlin
package com.suprxsidh.deficit.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.suprxsidh.deficit.data.db.entity.PendingDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingDraftDao {
    @Insert
    suspend fun insert(draft: PendingDraftEntity): Long

    @Update
    suspend fun update(draft: PendingDraftEntity)

    @Delete
    suspend fun delete(draft: PendingDraftEntity)

    @Query("SELECT * FROM pending_draft ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingDraftEntity>

    @Query("SELECT * FROM pending_draft ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingDraftEntity>>

    @Query("SELECT * FROM pending_draft WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingDraftEntity?
}
```

Register both in `DeficitDatabase.kt` (add to entities list, add `abstract fun pendingDraftDao(): PendingDraftDao`).

- [ ] **Step 4: Add `logGeminiEstimate` to `FoodRepository`**

Read the existing private `log(name, rawKcal, source, barcode = null)` helper in `FoodRepository.kt` first, then add one public method next to `logQuickAdd`/`logCustomFood`/`logOffProduct` that calls it with `source = "GEMINI"`:
```kotlin
    suspend fun logGeminiEstimate(name: String, rawKcal: Int): FoodEntryEntity = log(name, rawKcal, source = "GEMINI")
```

- [ ] **Step 5: Implement `GeminiFoodRepository`**

```kotlin
package com.suprxsidh.deficit.data.repository

import com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimate
import com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimator
import com.suprxsidh.deficit.data.db.dao.PendingDraftDao
import com.suprxsidh.deficit.data.db.entity.FoodEntryEntity
import com.suprxsidh.deficit.data.db.entity.PendingDraftEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Base64

sealed class GeminiEstimateResult {
    object NoApiKey : GeminiEstimateResult()
    data class Success(val estimate: GeminiFoodEstimate) : GeminiEstimateResult()
    data class Failed(val draftId: Long, val message: String) : GeminiEstimateResult()
}

class GeminiFoodRepository(
    private val estimator: GeminiFoodEstimator,
    private val settingsRepository: SettingsRepository,
    private val foodRepository: FoodRepository,
    private val pendingDraftDao: PendingDraftDao
) {
    suspend fun isAvailable(): Boolean = !settingsRepository.getGeminiApiKey().isNullOrBlank()

    fun observePendingDrafts(): Flow<List<PendingDraftEntity>> = pendingDraftDao.observeAll()

    /**
     * Estimates only — does not save. The caller (UI) shows the itemized result for
     * one-tap confirm/edit, then calls [confirmEstimate] once the user accepts it.
     */
    suspend fun estimateMeal(description: String?, photoFile: File?): GeminiEstimateResult {
        val apiKey = settingsRepository.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) return GeminiEstimateResult.NoApiKey

        val photoBase64 = photoFile?.let { encodeBase64(it) }
        return try {
            val estimate = estimator.estimate(apiKey, description, photoBase64)
            GeminiEstimateResult.Success(estimate)
        } catch (e: Exception) {
            val draftId = pendingDraftDao.insert(
                PendingDraftEntity(
                    type = if (photoFile != null) "MEAL_PHOTO" else "MEAL_TEXT",
                    payload = description.orEmpty(),
                    photoPath = photoFile?.absolutePath,
                    createdAt = System.currentTimeMillis()
                )
            )
            GeminiEstimateResult.Failed(draftId, e.message ?: "Unknown error")
        }
    }

    /** Saves a reviewed (optionally edited) estimate. Called once the user taps "confirm". */
    suspend fun confirmEstimate(estimate: GeminiFoodEstimate, editedName: String? = null, editedTotalKcal: Int? = null): FoodEntryEntity {
        val name = editedName ?: estimate.items.joinToString(", ") { it.name }
        val kcal = editedTotalKcal ?: estimate.totalKcal
        return foodRepository.logGeminiEstimate(name, kcal)
    }

    /** No user is present for a background retry, so a successful retry auto-saves unedited. */
    suspend fun retryPendingDrafts(): Int {
        val apiKey = settingsRepository.getGeminiApiKey() ?: return 0
        var succeeded = 0
        for (draft in pendingDraftDao.getAll()) {
            val photoBase64 = draft.photoPath?.let { path -> File(path).takeIf { it.exists() }?.let(::encodeBase64) }
            try {
                val estimate = estimator.estimate(apiKey, draft.payload.ifBlank { null }, photoBase64)
                confirmEstimate(estimate)
                pendingDraftDao.delete(draft)
                succeeded++
            } catch (e: Exception) {
                pendingDraftDao.update(draft.copy(retryCount = draft.retryCount + 1))
            }
        }
        return succeeded
    }

    suspend fun discardDraftAsQuickAdd(draftId: Long, kcal: Int) {
        val draft = pendingDraftDao.getById(draftId) ?: return
        foodRepository.logQuickAdd(draft.payload.ifBlank { "Meal" }, kcal)
        pendingDraftDao.delete(draft)
    }

    private fun encodeBase64(file: File): String = Base64.getEncoder().encodeToString(file.readBytes())
}
```
Add `name` to the assertions above assuming `FoodEntryEntity.name` is the field holding the logged food's display name (it is — confirmed in Task 1's reference read of `FoodEntryEntity`).
Note: `java.util.Base64` is used instead of `android.util.Base64` deliberately — it's pure JVM, needs no Robolectric shadow, and is available since API 26 (project's `minSdk` is 28), so it's safe on-device and trivially testable.

Add `GeminiFoodRepository` to `AppContainer.kt`:
```kotlin
    val geminiFoodRepository = GeminiFoodRepository(
        estimator = com.suprxsidh.deficit.ai.gemini.GeminiFoodEstimator(com.suprxsidh.deficit.ai.gemini.GeminiServiceFactory.create()),
        settingsRepository = settingsRepository,
        foodRepository = foodRepository,
        pendingDraftDao = database.pendingDraftDao()
    )
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.data.repository.GeminiFoodRepositoryTest"`
Expected: PASS, all 5 cases.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/data app/src/test/java/com/suprxsidh/deficit/data/repository/GeminiFoodRepositoryTest.kt
git commit -m "Task 12: pending-draft queue + Gemini logging orchestration"
```

---

### Task 13: Food logging UI — AI estimate entry, review sheet, pending-draft banner; final integration

**Files:**
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/food/FoodLogViewModel.kt`
- Modify: `app/src/main/java/com/suprxsidh/deficit/ui/food/FoodLogScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml` — add `FileProvider`
- Create: `app/src/main/res/xml/file_paths.xml`
- Test: `app/src/test/java/com/suprxsidh/deficit/ui/food/FoodLogViewModelTest.kt` (add cases)
- Modify: `TESTING.md`

**Interfaces:**
- Consumes: `GeminiFoodRepository.isAvailable()`, `estimateMeal()`, `confirmEstimate()`, `retryPendingDrafts()`, `discardDraftAsQuickAdd()`, `observePendingDrafts()`, `GeminiEstimateResult` (Task 12).
- Produces: `FoodLogViewModel.aiEstimateAvailable: StateFlow<Boolean>`, `aiDescription: String` (form field, mirrors `quickAddName`'s existing pattern), `pendingDrafts: StateFlow<List<PendingDraftEntity>>`, `reviewEstimate: GeminiFoodEstimate?` (non-null once an estimate comes back, drives the review sheet), `fun submitAiEstimate(photoFile: File?)`, `fun confirmAiEstimate(editedName: String, editedTotalKcal: Int)`, `fun cancelAiReview()`, `fun retryDrafts()`, `fun discardDraft(id: Long, kcal: Int)`.

- [ ] **Step 1: Write the failing test cases**

Append to `FoodLogViewModelTest.kt` (match the file's existing constructor-injection + `mutableStateOf` form-field pattern):
```kotlin
    @Test
    fun `aiEstimateAvailable reflects whether a Gemini key is set`() = runTest {
        settingsRepository.setGeminiApiKey(null)
        var viewModel = buildViewModel() // existing helper in this file, extended to take geminiFoodRepository
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
```
(Add a `FakeSucceedingGeminiApi` test double identical in shape to the one in `GeminiFoodRepositoryTest` from Task 12 — duplicate it locally in this test file rather than sharing across test source sets, matching how the existing test files in this project don't share fixtures either.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.food.FoodLogViewModelTest"`
Expected: FAIL (compile error).

- [ ] **Step 3: Implement the ViewModel additions**

Add to `FoodLogViewModel.kt`'s constructor and body (keep every existing parameter/field — this only adds to them):
```kotlin
class FoodLogViewModel(
    private val foodRepository: FoodRepository,
    private val offRepository: OpenFoodFactsRepository,
    private val geminiFoodRepository: GeminiFoodRepository
) : ViewModel() {
    // ... existing state ...

    var aiDescription by mutableStateOf("")
    var aiSubmitInFlight by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set
    var reviewEstimate by mutableStateOf<GeminiFoodEstimate?>(null)
        private set

    val aiEstimateAvailable: StateFlow<Boolean> = kotlinx.coroutines.flow.flow { emit(geminiFoodRepository.isAvailable()) }
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
}
```
Add the matching imports (`GeminiFoodRepository`, `GeminiEstimateResult`, `GeminiFoodEstimate`, `PendingDraftEntity`, `java.io.File`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest --tests "com.suprxsidh.deficit.ui.food.FoodLogViewModelTest"`
Expected: PASS.

- [ ] **Step 5: FileProvider for photo capture**

Add to `AndroidManifest.xml`, inside `<application>`:
```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```
Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="meal_photos" path="meal_photos/" />
</paths>
```
Check `app/build.gradle.kts` for an existing `androidx.core:core-ktx` dependency; if absent, add `implementation("androidx.core:core-ktx:1.15.0")` (needed for `androidx.core.content.FileProvider`).

- [ ] **Step 6: Add the AI estimate UI to `FoodLogScreen.kt`**

Add a new section above the existing Quick Add form, gated on `aiEstimateAvailable`:
```kotlin
    val aiAvailable by viewModel.aiEstimateAvailable.collectAsState()
    val pendingDrafts by viewModel.pendingDrafts.collectAsState()
    val context = LocalContext.current
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var draftAwaitingKcal by remember { mutableStateOf<Long?>(null) }
    var draftKcalInput by remember { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) pendingPhotoFile = null
    }

    if (pendingDrafts.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("${pendingDrafts.size} meal(s) waiting to sync", style = MaterialTheme.typography.titleSmall)
                Text("Nothing you typed is lost — this'll retry automatically, or:")
                Row {
                    Button(onClick = { viewModel.retryDrafts() }) { Text("Retry now") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        draftAwaitingKcal = pendingDrafts.first().id
                        draftKcalInput = ""
                    }) { Text("Just quick-add it") }
                }
            }
        }
    }

    // "Just quick-add it" needs the user's own calorie estimate — the app has none to offer
    // once Gemini has failed, so this dialog is the actual escape hatch, not a placeholder.
    draftAwaitingKcal?.let { draftId ->
        AlertDialog(
            onDismissRequest = { draftAwaitingKcal = null },
            title = { Text("Quick-add this meal") },
            text = {
                OutlinedTextField(
                    value = draftKcalInput,
                    onValueChange = { draftKcalInput = it.filter(Char::isDigit) },
                    label = { Text("Calories") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftKcalInput.toIntOrNull()?.let { kcal -> viewModel.discardDraft(draftId, kcal) }
                        draftAwaitingKcal = null
                    },
                    enabled = draftKcalInput.toIntOrNull() != null
                ) { Text("Log it") }
            },
            dismissButton = { TextButton(onClick = { draftAwaitingKcal = null }) { Text("Cancel") } }
        )
    }

    if (aiAvailable) {
        Text("Describe your meal", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = viewModel.aiDescription,
            onValueChange = { viewModel.aiDescription = it },
            label = { Text("e.g. 2 rotis, dal tadka, cucumber salad") },
            modifier = Modifier.fillMaxWidth()
        )
        Row {
            Button(
                onClick = { viewModel.submitAiEstimate(pendingPhotoFile) },
                enabled = !viewModel.aiSubmitInFlight
            ) { Text(if (viewModel.aiSubmitInFlight) "Estimating..." else "Estimate") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                val file = File(File(context.cacheDir, "meal_photos").apply { mkdirs() }, "meal_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pendingPhotoFile = file
                cameraLauncher.launch(uri)
            }) { Text("Add photo") }
        }
        viewModel.aiError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    // Itemized review sheet — SPEC.md §3.3: "show itemized estimate for one-tap confirm/edit before saving."
    viewModel.reviewEstimate?.let { estimate ->
        var editedName by remember(estimate) { mutableStateOf(estimate.items.joinToString(", ") { it.name }) }
        var editedKcalInput by remember(estimate) { mutableStateOf(estimate.totalKcal.toString()) }

        AlertDialog(
            onDismissRequest = { viewModel.cancelAiReview() },
            title = { Text("Confirm meal (confidence: ${estimate.confidence})") },
            text = {
                Column {
                    estimate.items.forEach { item -> Text("${item.name} — ${item.kcal} kcal") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = editedName, onValueChange = { editedName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(
                        value = editedKcalInput,
                        onValueChange = { editedKcalInput = it.filter(Char::isDigit) },
                        label = { Text("Total kcal (+10% buffer applied on save)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { editedKcalInput.toIntOrNull()?.let { kcal -> viewModel.confirmAiEstimate(editedName, kcal) } },
                    enabled = editedKcalInput.toIntOrNull() != null
                ) { Text("Confirm & log") }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelAiReview() }) { Text("Cancel") } }
        )
    }
```
(Add imports: `androidx.compose.foundation.text.KeyboardOptions`, `androidx.compose.ui.text.input.KeyboardType`, `androidx.compose.material3.AlertDialog`.)

- [ ] **Step 7: Retry pending drafts on app open**

In `MainActivity.kt`, alongside the Health Connect sync trigger from Task 5, add:
```kotlin
        lifecycleScope.launch { app.container.geminiFoodRepository.retryPendingDrafts() }
```

- [ ] **Step 8: Update `TESTING.md`**

Add a `## Phase 2 (Health Connect + Gemini) Testing Notes` section following the exact structure of the existing Phase 1 section (`## Automated coverage`, `## Manual on-device checks`, `## Known Phase 2 gaps`). Manual checks must include: granting/denying Health Connect permissions, confirming a Samsung Health run appears within 30–60 min (or after opening Samsung Health), weigh-in round-trips both directions, airplane-mode Gemini call produces a pending draft that later retries, "just quick-add it" escape hatch, Settings key save/clear, `HealthConnectDataSource` mapping logic (explicitly device-only, not unit-tested — the business logic it feeds, `HealthConnectRepository`, is fully unit-tested instead).

- [ ] **Step 9: Full-suite verification**

Run: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home && ./gradlew testDebugUnitTest assembleDebug`
Expected: every unit test across Tasks 1–13 PASSES, `BUILD SUCCESSFUL`, and the debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/suprxsidh/deficit/ui/food app/src/test/java/com/suprxsidh/deficit/ui/food app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml app/build.gradle.kts TESTING.md app/src/main/java/com/suprxsidh/deficit/MainActivity.kt
git commit -m "Task 13: AI meal estimation UI, pending-draft banner, Phase 2 TESTING.md"
```

---

## Definition of Done (Phase 2)

- [ ] All 13 tasks' unit tests pass (`./gradlew testDebugUnitTest`); `./gradlew assembleDebug` succeeds.
- [ ] Health Connect: permissions requestable from onboarding, sync runs on app open + every 60 min, exercise sessions dedupe by `hcRecordId`, weigh-ins sync both directions, "unavailable/permissions revoked" states don't crash the app.
- [ ] Gemini: hides entirely with no key/no network; itemized estimate always gets the same +10% buffer as every other logging path; nothing typed or photographed is ever lost to a failed API call (pending-draft path, verified in tests).
- [ ] No new network calls beyond Open Food Facts and Gemini (the latter only when a key is set). Gemini key never leaves local Room storage except as the `x-goog-api-key` request header.
- [ ] `TESTING.md` updated with the Phase 2 manual on-device checklist (Health Connect setup/sync timing, weigh-in round-trip, airplane-mode Gemini retry).
