package com.fridge.main.data.remote.recipe

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 单例。
 *
 * 只在这里组装 OkHttp/Retrofit/Gson，Repository 通过构造参数接收 [RecipeApi]，
 * 便于后续替换实现或在测试中注入假接口。
 */
internal object RecipeApiClient {

    private const val BASE_URL = "https://www.themealdb.com/"

    private val gson = GsonBuilder()
        // 接口字段较多且允许 null；默认配置即可解析，缺失字段会落为 null。
        .create()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            // 部分公共接口会拒绝空 User-Agent；显式标识客户端，便于服务端限流排查。
            val request = chain.request().newBuilder()
                .header("User-Agent", "FridgeApp/1.0 (Android)")
                .build()
            chain.proceed(request)
        }
        .build()

    val api: RecipeApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(RecipeApi::class.java)
    }
}
