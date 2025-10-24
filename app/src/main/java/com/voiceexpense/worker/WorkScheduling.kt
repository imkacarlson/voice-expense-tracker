package com.voiceexpense.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import java.util.concurrent.TimeUnit

private const val UNIQUE_SYNC_WORK = "voice_expense_sync"

fun enqueueSyncNow(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val work = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(UNIQUE_SYNC_WORK, ExistingWorkPolicy.REPLACE, work)
}
