package com.voiceexpense.di

import android.content.Context
import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.speech.AudioRecordingManager
import com.voiceexpense.ai.speech.SpeechRecognitionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides @Singleton
    fun provideModelManager(): ModelManager = ModelManager()

    @Provides
    fun provideAudio(): AudioRecordingManager = AudioRecordingManager()

    @Provides
    fun provideAsr(@ApplicationContext ctx: Context): SpeechRecognitionService = SpeechRecognitionService(ctx)

    @Provides
    fun provideParser(mm: ModelManager): TransactionParser = TransactionParser(mm)
}

