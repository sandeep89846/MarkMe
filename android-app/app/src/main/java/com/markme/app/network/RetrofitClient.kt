package com.markme.app.network

import com.markme.app.BuildConfig
import com.squareup.moshi.Moshi // <-- Make sure this is imported
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory // <-- Make sure this is imported
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitClient {

    private const val DEV_BASE_URL = "https://kangaroo-ultimate-correctly.ngrok-free.app/"
    private const val PROD_BASE_URL = "http://your-production-server.com/"

    // --- START FIX ---
    // Make this 'val' (public) instead of 'private val'
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // This is the adapter that fixes the crash
        .build()
    // --- END FIX ---

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEV_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi)) // Uses our correct instance
            .build()
            .create(ApiService::class.java)
    }
}