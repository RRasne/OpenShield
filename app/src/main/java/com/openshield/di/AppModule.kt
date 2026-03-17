package com.openshield.di

import android.content.Context
import com.openshield.data.db.SpamDatabase
import com.openshield.data.repository.SpamNumberRepository
import com.openshield.data.repository.SpamRepository
import com.openshield.detection.engine.SpamDetectionEngine
import com.openshield.detection.ml.TFLiteClassifier
import com.openshield.util.ConsentManager
import com.openshield.worker.WifiSyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideSpamDatabase(@ApplicationContext context: Context): SpamDatabase =
        SpamDatabase.getInstance(context)

    @Provides @Singleton
    fun provideSpamNumberRepository(db: SpamDatabase): SpamNumberRepository =
        SpamNumberRepository(db)

    @Provides @Singleton
    fun provideSpamRepository(
        db: SpamDatabase,
        @ApplicationContext context: Context
    ): SpamRepository = SpamRepository(db, context)

    @Provides @Singleton
    fun provideConsentManager(@ApplicationContext context: Context): ConsentManager =
        ConsentManager(context)

    @Provides @Singleton
    fun provideWifiSyncManager(
        @ApplicationContext context: Context,
        consentManager: ConsentManager
    ): WifiSyncManager = WifiSyncManager(context, consentManager)

    @Provides @Singleton
    fun provideTFLiteClassifier(): TFLiteClassifier = TFLiteClassifier()

    @Provides @Singleton
    fun provideSpamDetectionEngine(repository: SpamRepository): SpamDetectionEngine =
        SpamDetectionEngine(repository)
}
