package com.openshield.di

import android.content.Context
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.SpamRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSpamDatabase(@ApplicationContext context: Context): SpamDatabase {
        return SpamDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSpamRepository(db: SpamDatabase): SpamRepository {
        return SpamRepository(db)
    }
}
