package com.voiceexpense.di

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.EncryptedPrefsStore
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.auth.GoogleIdentityTokenProvider
import com.voiceexpense.auth.LazyEncryptedPrefsStore
import com.voiceexpense.data.local.AppDatabase
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.remote.AppsScriptClient
import com.voiceexpense.data.repository.TransactionRepository
import com.voiceexpense.data.config.ConfigDao
import com.voiceexpense.data.config.ConfigRepository
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
        Room.databaseBuilder(ctx, AppDatabase::class.java, "voice_expense.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides fun provideDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides fun provideConfigDao(db: AppDatabase): ConfigDao = db.configDao()
    @Provides @Singleton fun provideConfigRepository(dao: ConfigDao): ConfigRepository = ConfigRepository(dao)

    @Provides @Singleton
    fun provideRepo(
        dao: TransactionDao,
        apps: AppsScriptClient,
        auth: AuthRepository,
        tokenProvider: com.voiceexpense.auth.TokenProvider
    ): TransactionRepository = TransactionRepository(dao, apps, auth, tokenProvider)

    @Provides @Singleton fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideAppsScript(client: OkHttpClient, moshi: Moshi): AppsScriptClient = AppsScriptClient(client = client, moshi = moshi)

    @Provides @Singleton
    fun provideAuth(@ApplicationContext ctx: Context): AuthRepository = try {
        // Lazy store defers heavy Android Keystore + disk work until first use.
        AuthRepository(LazyEncryptedPrefsStore(ctx.applicationContext))
    } catch (t: Throwable) {
        // Robolectric/host unit tests don't have AndroidKeyStore; fall back to in-memory store
        AuthRepository(InMemoryStore())
    }

    @Provides @Singleton
    fun provideTokenProvider(@ApplicationContext ctx: Context, auth: com.voiceexpense.auth.AuthRepository): TokenProvider =
        GoogleIdentityTokenProvider(ctx, auth)

    // Voice correction loop removed in text-first refactor
}
