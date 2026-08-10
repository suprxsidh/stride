package com.suprxsidh.deficit.food.off

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object OpenFoodFactsServiceFactory {
    private const val USER_AGENT = "Deficit-Android/0.1 (personal-use; contact: none)"

    fun create(baseUrl: String = "https://world.openfoodfacts.org/"): OpenFoodFactsApi {
        val json = Json { ignoreUnknownKeys = true }
        val loggingInterceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsApi::class.java)
    }
}
