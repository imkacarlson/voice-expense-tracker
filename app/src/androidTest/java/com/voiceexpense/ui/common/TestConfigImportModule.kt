package com.voiceexpense.ui.common

import android.content.Context
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.auth.StaticTokenProvider
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.data.config.ConfigDao
import com.voiceexpense.data.config.ConfigImporter
import com.voiceexpense.data.config.ConfigOption
import com.voiceexpense.data.config.ConfigRepository
import com.voiceexpense.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TestConfigImportModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    @Singleton
    fun provideDelegatingDao(db: AppDatabase): DelegatingConfigDao = DelegatingConfigDao(db.configDao())

    @Provides
    fun provideConfigDao(delegate: DelegatingConfigDao): ConfigDao = delegate

    @Provides
    @Singleton
    fun provideConfigRepository(dao: ConfigDao): ConfigRepository = ConfigRepository(dao)

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideConfigImporter(repo: ConfigRepository, moshi: Moshi): ConfigImporter = ConfigImporter(repo, moshi)

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository = AuthRepository(InMemoryStore())

    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = StaticTokenProvider()
}

class DelegatingConfigDao(private val delegate: ConfigDao) : ConfigDao by delegate {
    @Volatile var failOnUpsert: Boolean = false

    override suspend fun upsertOptions(options: List<ConfigOption>) {
        if (failOnUpsert) {
            throw android.database.sqlite.SQLiteException("Simulated failure")
        }
        delegate.upsertOptions(options)
    }
}
