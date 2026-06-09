package com.bolang.android.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object BoLangClient {

    // AVD emulator  → 10.0.2.2
    // Genymotion    → 10.0.3.2
    // Real device   → IP mesin host di jaringan yang sama, misal 192.168.1.x
    private var baseUrl: String = "http://10.0.3.2:3000/api/"

    private var retrofit: Retrofit? = null

    fun init(baseUrl: String) {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        this.baseUrl = url
        retrofit = null
    }

    fun api(): BoLangApiService = getRetrofit().create(BoLangApiService::class.java)

    private fun getRetrofit(): Retrofit {
        return retrofit ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(buildOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .also { retrofit = it }
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
