package com.voiceexpense.di

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.EncryptedPrefsStore
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.speech.AudioRecordingManager
import com.voiceexpense.ai.speech.SpeechRecognitionService
import com.voiceexpense.data.local.AppDatabase
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.remote.SheetsClient
import com.voiceexpense.data.repository.TransactionRepository
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
    fun provideRepo(dao: TransactionDao): TransactionRepository = TransactionRepository(dao)

    @Provides @Singleton fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideSheets(client: OkHttpClient, moshi: Moshi): SheetsClient = SheetsClient(client = client, moshi = moshi)

    @Provides @Singleton
    fun provideAuth(@ApplicationContext ctx: Context): AuthRepository = AuthRepository(EncryptedPrefsStore(ctx))

    @Provides fun provideAudio(): AudioRecordingManager = AudioRecordingManager()
    @Provides fun provideAsr(): SpeechRecognitionService = SpeechRecognitionService()
    @Provides fun provideParser(): TransactionParser = TransactionParser()
}

