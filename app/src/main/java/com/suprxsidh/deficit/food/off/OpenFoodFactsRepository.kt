package com.suprxsidh.deficit.food.off

import com.suprxsidh.deficit.data.db.dao.OffCacheDao
import com.suprxsidh.deficit.data.db.entity.OffCacheEntity
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

class OpenFoodFactsRepository(
    private val api: OpenFoodFactsApi,
    private val cacheDao: OffCacheDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun search(query: String): List<OffCacheEntity> {
        val response = try {
            api.search(searchTerms = query)
        } catch (e: IOException) {
            return cacheDao.searchCached(query)
        } catch (e: HttpException) {
            return cacheDao.searchCached(query)
        } catch (e: SerializationException) {
            return cacheDao.searchCached(query)
        }

        val now = clock()
        val results = response.products.mapNotNull { product ->
            val code = product.code ?: return@mapNotNull null
            val nutriments = product.nutriments
            val kcal = nutriments?.energyKcalServing?.toInt() ?: nutriments?.energyKcal100g?.toInt()
                ?: return@mapNotNull null
            val servingLabel = if (nutriments?.energyKcalServing != null) "1 serving" else "100g"
            OffCacheEntity(
                code = code,
                productName = product.productName ?: "Unknown product",
                kcalPerServing = kcal,
                servingLabel = servingLabel,
                cachedAt = now
            )
        }
        results.forEach { cacheDao.upsert(it) }
        return results
    }
}
