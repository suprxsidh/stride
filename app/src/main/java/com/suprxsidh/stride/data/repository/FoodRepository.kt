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
    private fun todayKey(): String = DayBoundary.logicalDate(clock()).toString()
    private fun nowMillis(): Long = clock().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun todayKeyFlow(): Flow<String> = flow {
        while (true) {
            emit(todayKey())
            delay(todayPollIntervalMs)
        }
    }.distinctUntilChanged()

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

    fun observeTodayEntries(): Flow<List<FoodEntryEntity>> =
        todayKeyFlow().flatMapLatest { foodEntryDao.observeForDate(it) }

    fun observeTodayBufferedTotal(): Flow<Int> =
        todayKeyFlow().flatMapLatest { foodEntryDao.observeBufferedTotalForDate(it) }

    fun observeAllCustomFoods(): Flow<List<CustomFoodEntity>> = customFoodDao.observeAll()
    fun observePinnedCustomFoods(): Flow<List<CustomFoodEntity>> = customFoodDao.observePinned()
    suspend fun upsertCustomFood(food: CustomFoodEntity): Long = customFoodDao.upsert(food)
    suspend fun findCustomFoodByName(name: String): CustomFoodEntity? = customFoodDao.findByName(name)
    suspend fun countPinnedCustomFoods(): Int = customFoodDao.countPinned()
    suspend fun deleteCustomFood(food: CustomFoodEntity) = customFoodDao.delete(food)
    suspend fun deleteFoodEntry(entry: FoodEntryEntity) = foodEntryDao.delete(entry)
}
