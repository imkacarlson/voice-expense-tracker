package com.voiceexpense.di

import android.content.Context
import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.MlKitClient
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.PromptBuilder
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
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

    @Provides @Singleton
    fun provideMlKitClient(@ApplicationContext context: Context, mm: ModelManager): MlKitClient = MlKitClient(context, mm)

    @Provides @Singleton
    fun providePromptBuilder(): PromptBuilder = PromptBuilder()

    @Provides
    fun provideGenAiGateway(ml: MlKitClient): GenAiGateway = object : GenAiGateway {
        override fun isAvailable(): Boolean = ml.isAvailable()
        override suspend fun structured(prompt: String) = ml.structured(prompt)
    }

    @Provides
    fun provideHybridParser(gateway: GenAiGateway, pb: PromptBuilder): HybridTransactionParser = HybridTransactionParser(gateway, pb)

    @Provides
    fun provideParser(mm: ModelManager, ml: MlKitClient): TransactionParser = TransactionParser(mm, ml)
}
