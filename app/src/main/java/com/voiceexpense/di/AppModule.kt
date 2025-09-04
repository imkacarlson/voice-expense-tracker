package com.voiceexpense.di

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.EncryptedPrefsStore
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.auth.GoogleIdentityTokenProvider
import com.voiceexpense.data.local.AppDatabase
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.remote.SheetsClient
import com.voiceexpense.data.repository.TransactionRepository
import com.voiceexpense.ui.confirmation.voice.CorrectionIntentParser
import com.voiceexpense.ui.confirmation.voice.PromptRenderer
import com.voiceexpense.ui.confirmation.voice.TtsEngine
import com.voiceexpense.ui.confirmation.voice.VoiceCorrectionController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "voice_expense.db").build()

    @Provides fun provideDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides @Singleton
    fun provideRepo(
        dao: TransactionDao,
        sheets: SheetsClient,
        auth: AuthRepository,
        tokenProvider: com.voiceexpense.auth.TokenProvider
    ): TransactionRepository = TransactionRepository(dao, sheets, auth, tokenProvider)

    @Provides @Singleton fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideSheets(client: OkHttpClient, moshi: Moshi): SheetsClient = SheetsClient(client = client, moshi = moshi)

    @Provides @Singleton
    fun provideAuth(@ApplicationContext ctx: Context): AuthRepository = try {
        AuthRepository(EncryptedPrefsStore(ctx))
    } catch (t: Throwable) {
        // Robolectric/host unit tests don't have AndroidKeyStore; fall back to in-memory store
        AuthRepository(InMemoryStore())
    }

    @Provides @Singleton
    fun provideTokenProvider(@ApplicationContext ctx: Context, auth: com.voiceexpense.auth.AuthRepository): TokenProvider =
        GoogleIdentityTokenProvider(ctx, auth)

    // Voice correction loop components
    @Provides fun provideCorrectionIntentParser(): CorrectionIntentParser = CorrectionIntentParser()
    @Provides fun providePromptRenderer(): PromptRenderer = PromptRenderer()
    @Provides fun provideTtsEngine(): TtsEngine = TtsEngine()
    @Provides fun provideVoiceCorrectionController(
        tts: TtsEngine,
        cip: CorrectionIntentParser,
        renderer: PromptRenderer
    ): VoiceCorrectionController = VoiceCorrectionController(tts = tts, parser = cip, renderer = renderer)
}
