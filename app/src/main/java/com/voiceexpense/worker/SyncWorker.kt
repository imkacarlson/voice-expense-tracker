package com.voiceexpense.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voiceexpense.ui.common.SettingsKeys
import com.voiceexpense.data.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: TransactionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val prefs = applicationContext.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
            val webUrl = prefs.getString(SettingsKeys.WEB_APP_URL, "") ?: ""
            val backupToken = prefs.getString(SettingsKeys.BACKUP_AUTH_TOKEN, null)

            repo.webAppUrl = webUrl
            repo.backupAuthToken = backupToken

            val result = repo.syncPending()
            result
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
