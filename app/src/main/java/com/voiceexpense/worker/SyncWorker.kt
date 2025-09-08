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

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun transactionRepository(): TransactionRepository
    }

    private fun repo(): TransactionRepository {
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
            val backupToken = prefs.getString(SettingsKeys.BACKUP_AUTH_TOKEN, null)

            repo.webAppUrl = webUrl
            repo.backupAuthToken = backupToken

            repo.syncPending()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
