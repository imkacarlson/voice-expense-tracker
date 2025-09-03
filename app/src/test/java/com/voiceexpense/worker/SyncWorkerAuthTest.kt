package com.voiceexpense.worker

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.remote.AppendResponse
import com.voiceexpense.data.remote.SheetsClient
import com.voiceexpense.data.repository.TransactionRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

class FakeTokenProvider2(private val token: String) : TokenProvider {
    override suspend fun getAccessToken(accountEmail: String, scope: String): String = token
    override suspend fun invalidateToken(accountEmail: String, scope: String) { /* no-op */ }
}

class SheetsSuccess : SheetsClient() {
    override suspend fun appendRow(
        accessToken: String,
        spreadsheetId: String,
        sheetName: String,
        values: List<String>
    ) = Result.success(AppendResponse(spreadsheetId, null, null))
}

@RunWith(RobolectricTestRunner::class)
class SyncWorkerAuthTest {
    @Test
    fun queuedPosts_withTokenProvider_succeeds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val dao = FakeDao()
        val auth = AuthRepository(InMemoryStore()).apply { setAccount("user", "user@example.com"); setAccessToken("t") }
        val repo = TransactionRepository(dao, SheetsSuccess(), auth, FakeTokenProvider2("t")).apply {
            spreadsheetId = "id"; sheetName = "Sheet1"
        }

        val txn = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("10.00"),
            merchant = "M",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Cat",
            incomeCategory = null,
            tags = listOf("t"),
            account = "Card",
            splitOverallChargedUsd = null,
            note = null,
            confidence = 1.0f,
            status = TransactionStatus.QUEUED
        )
        runBlockingUnit { dao.upsert(txn) }

        val prefs = context.getSharedPreferences(com.voiceexpense.ui.common.SettingsKeys.PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(com.voiceexpense.ui.common.SettingsKeys.SPREADSHEET_ID, "id")
            .putString(com.voiceexpense.ui.common.SettingsKeys.SHEET_NAME, "Sheet1").apply()

        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: android.content.Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters
                ): androidx.work.ListenableWorker? {
                    return SyncWorker(appContext, workerParameters, repo)
                }
            }).build()
        val result = worker.startWork().get()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
    }
}

// Helper to call suspend in test without coroutines-test in this file
private fun runBlockingUnit(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

