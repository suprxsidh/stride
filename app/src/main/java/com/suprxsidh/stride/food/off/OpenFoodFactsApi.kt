package com.suprxsidh.stride.food.off

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenFoodFactsApi {
    @GET("api/v2/search")
    suspend fun search(
        @Query("search_terms") searchTerms: String,
        @Query("countries_tags_en") countriesTagsEn: String = "india",
        @Query("fields") fields: String = "code,product_name,nutriments",
        @Query("page_size") pageSize: Int = 20
    ): OffSearchResponse
}
