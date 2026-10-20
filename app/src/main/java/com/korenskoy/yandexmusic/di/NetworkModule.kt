package com.korenskoy.yandexmusic.di

import android.content.Context
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.korenskoy.yandexmusic.BuildConfig
import com.korenskoy.yandexmusic.data.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        @ApplicationContext context: Context
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
            // Never print the OAuth token, even in debug logcat
            redactHeader("Authorization")
            redactHeader("Cookie")
        }

        val cacheSize = 20L * 1024 * 1024 // 20 MB
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.music.yandex.net/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideYandexMusicApi(retrofit: Retrofit): com.korenskoy.yandexmusic.data.network.YandexMusicApi {
        return retrofit.create(com.korenskoy.yandexmusic.data.network.YandexMusicApi::class.java)
    }
}
