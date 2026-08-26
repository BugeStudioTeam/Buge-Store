package com.buge.store.data

import com.buge.store.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

interface StoreApi {
    @GET
    suspend fun apps(@Url path: String = "apps.json"): AppsResponse

    @GET
    suspend fun categories(@Url path: String = "categories.json"): CategoriesResponse

    @GET
    suspend fun trending(@Url path: String = "trending.json"): TrendingResponse
}

object StoreApiFactory {
    fun create(): StoreApi {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(StoreApi::class.java)
    }
}
