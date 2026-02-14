package com.example.neumusic.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// API 响应格式
data class SplashResponse(
    @SerializedName("color") val color: String?,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("state") val state: String? // "0" 可能表示启用
)

// Retrofit 接口
interface SplashApi {
    @GET("client/cgi-bin/Splash")
    suspend fun getSplashConfig(): SplashResponse
}

// 单例网络客户端
object NetworkClient {
    private const val BASE_URL = "http://110.42.44.95:8657/"

    val splashApi: SplashApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SplashApi::class.java)
    }
}
