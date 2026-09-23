package com.suprxsidh.stride.data.repository

import com.suprxsidh.stride.data.calc.CalorieMath
import com.suprxsidh.stride.data.calc.DayBoundary
import com.suprxsidh.stride.data.db.dao.CustomFoodDao
import com.suprxsidh.stride.data.db.dao.FoodEntryDao
import com.suprxsidh.stride.data.db.entity.CustomFoodEntity
import com.suprxsidh.stride.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class FoodRepository(
    private val foodEntryDao: FoodEntryDao,
    private val customFoodDao: CustomFoodDao,
    private val todayPollIntervalMs: Long = 60_000,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {
    /** The current logical day per [DayBoundary]'s 3am rule — exposed so callers (e.g. History's
     * default date) can anchor on the same "today" this repository already uses internally. */
    fun currentLogicalDate(): LocalDate = DayBoundary.logicalDate(clock())

    private fun todayKey(): String = currentLogicalDate().toString()
    private fun nowMillis(): Long = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun todayKeyFlow(): Flow<String> = flow {
        while (true) {
            emit(todayKey())
            delay(todayPollIntervalMs)
        }
    }.distinctUntilChanged()

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

    fun observeTodayEntries(): Flow<List<FoodEntryEntity>> =
        todayKeyFlow().flatMapLatest { foodEntryDao.observeForDate(it) }

    fun observeTodayBufferedTotal(): Flow<Int> =
        todayKeyFlow().flatMapLatest { foodEntryDao.observeBufferedTotalForDate(it) }

    /** History (Feature A): parallel to [observeTodayEntries] but for an arbitrary logical date,
     * so the dashboard's "today" flows above stay untouched. */
    fun observeEntriesForDate(date: LocalDate): Flow<List<FoodEntryEntity>> =
        foodEntryDao.observeForDate(date.toString())

    fun observeBufferedTotalForDate(date: LocalDate): Flow<Int> =
        foodEntryDao.observeBufferedTotalForDate(date.toString())

    suspend fun updateFoodEntry(entry: FoodEntryEntity) = foodEntryDao.update(entry)

    fun observeAllCustomFoods(): Flow<List<CustomFoodEntity>> = customFoodDao.observeAll()
    fun observePinnedCustomFoods(): Flow<List<CustomFoodEntity>> = customFoodDao.observePinned()
    suspend fun upsertCustomFood(food: CustomFoodEntity): Long = customFoodDao.upsert(food)
    suspend fun findCustomFoodByName(name: String): CustomFoodEntity? = customFoodDao.findByName(name)
    suspend fun countPinnedCustomFoods(): Int = customFoodDao.countPinned()
    suspend fun deleteCustomFood(food: CustomFoodEntity) = customFoodDao.delete(food)
    suspend fun deleteFoodEntry(entry: FoodEntryEntity) = foodEntryDao.delete(entry)
}
