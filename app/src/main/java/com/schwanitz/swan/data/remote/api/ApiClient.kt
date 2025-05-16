package com.schwanitz.swan.data.remote.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "https://www.theaudiodb.com/api/v1/json/2/"

    val theAudioDBService: TheAudioDBService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TheAudioDBService::class.java)
    }
}