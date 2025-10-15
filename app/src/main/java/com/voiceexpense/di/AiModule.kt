package com.voiceexpense.di

import android.content.Context
import com.voiceexpense.ai.mediapipe.MediaPipeGenAiClient
import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides @Singleton
    fun provideModelManager(): ModelManager = ModelManager()

    @Provides @Singleton
    fun provideMediaPipeClient(@ApplicationContext context: Context): MediaPipeGenAiClient = MediaPipeGenAiClient(context)

    @Provides
    fun provideGenAiGateway(mp: MediaPipeGenAiClient): GenAiGateway = object : GenAiGateway {
        override fun isAvailable(): Boolean = mp.isAvailable()
        override suspend fun structured(prompt: String) = mp.structured(prompt)
    }

    @Provides
    fun provideHybridParser(gateway: GenAiGateway): HybridTransactionParser = HybridTransactionParser(gateway)

    @Provides
    fun provideParser(mm: ModelManager, hybrid: HybridTransactionParser): TransactionParser = TransactionParser(mm, hybrid)
}
