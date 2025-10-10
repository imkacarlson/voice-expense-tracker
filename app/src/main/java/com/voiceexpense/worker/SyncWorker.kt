package com.voiceexpense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voiceexpense.ui.common.SettingsKeys
import com.voiceexpense.data.repository.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    // Optional test hook to inject repository directly
    private var injectedRepo: TransactionRepository? = null

    constructor(appContext: Context, params: WorkerParameters, repo: TransactionRepository) : this(appContext, params) {
        this.injectedRepo = repo
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun transactionRepository(): TransactionRepository
    }

    private fun repo(): TransactionRepository {
        injectedRepo?.let { return it }
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        return entryPoint.transactionRepository()
    }

    override suspend fun doWork(): Result {
        val repo = repo()
        return runCatching {
            val prefs = applicationContext.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
            val webUrl = prefs.getString(SettingsKeys.WEB_APP_URL, "") ?: ""

            repo.webAppUrl = webUrl

            repo.syncPending()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
