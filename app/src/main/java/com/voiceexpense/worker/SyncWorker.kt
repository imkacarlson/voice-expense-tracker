package com.voiceexpense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.ui.common.SettingsKeys
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.remote.SheetsClient
import com.voiceexpense.data.repository.TransactionRepository

// Simple service locator for now; will be replaced by Hilt modules later (Task 16)
object AppServices {
    lateinit var dao: TransactionDao
    lateinit var repo: TransactionRepository
    lateinit var sheets: SheetsClient
    lateinit var auth: AuthRepository

    var spreadsheetId: String = ""
    var sheetName: String = ""
}

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val queued = AppServices.dao.getByStatus(TransactionStatus.QUEUED)
            val accessToken = AppServices.auth.getAccessToken()

            val prefs = applicationContext.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
            val spreadsheetId = prefs.getString(SettingsKeys.SPREADSHEET_ID, AppServices.spreadsheetId) ?: AppServices.spreadsheetId
            val sheetName = prefs.getString(SettingsKeys.SHEET_NAME, AppServices.sheetName) ?: AppServices.sheetName
            var posted = 0
            for (t in queued) {
                val row = AppServices.repo.mapToSheetRow(t)
                val res = AppServices.sheets.appendRow(
                    accessToken = accessToken,
                    spreadsheetId = spreadsheetId,
                    sheetName = sheetName,
                    values = row
                )
                if (res.isSuccess) {
                    AppServices.dao.updateStatus(t.id, TransactionStatus.POSTED)
                    posted++
                } else {
                    // Leave in QUEUED; WorkManager backoff will retry later
                }
            }
            posted
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
