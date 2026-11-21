package com.nayama.music.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://discoveryprovider.audius.co/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "NayamaMusic/1.0 (Android)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .build()
    }

    val audiusApiService: AudiusApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AudiusApiService::class.java)
    }

    fun getStreamUrl(trackId: String): String {
        return "${BASE_URL}v1/tracks/$trackId/stream?app_name=NayamaMusic"
    }
}

