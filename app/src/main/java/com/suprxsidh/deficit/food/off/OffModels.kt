package com.suprxsidh.deficit.food.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OffSearchResponse(val products: List<OffProduct> = emptyList())

@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val nutriments: OffNutriments? = null
)

@Serializable
data class OffNutriments(
    @SerialName("energy-kcal_serving") val energyKcalServing: Double? = null,
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null
)
