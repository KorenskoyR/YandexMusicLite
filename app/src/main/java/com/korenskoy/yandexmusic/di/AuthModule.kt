package com.korenskoy.yandexmusic.di

import android.content.Context
import com.korenskoy.yandexmusic.data.repository.AuthRepositoryImpl
import com.korenskoy.yandexmusic.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context
    ): AuthRepository {
        return AuthRepositoryImpl(context)
    }
}
