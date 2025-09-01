package com.voiceexpense.worker

import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.WorkerFactory
import androidx.work.ProgressUpdater
import androidx.work.ForegroundUpdater
import androidx.work.ForegroundInfo
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.google.common.util.concurrent.Futures
import java.util.UUID
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.remote.AppendResponse
import com.voiceexpense.data.remote.SheetsClient
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

class FakeDao : TransactionDao {
    val list = mutableListOf<Transaction>()
    override suspend fun upsert(transaction: Transaction) { list.removeAll { it.id == transaction.id }; list.add(transaction) }
    override suspend fun upsertAll(transactions: List<Transaction>) { transactions.forEach { upsert(it) } }
    override suspend fun update(transaction: Transaction): Int { upsert(transaction); return 1 }
    override suspend fun getById(id: String) = list.find { it.id == id }
    override suspend fun deleteById(id: String) = if (list.removeIf { it.id == id }) 1 else 0
    override fun observeAll() = throw UnsupportedOperationException()
    override fun observeByStatus(status: TransactionStatus) = throw UnsupportedOperationException()
    override suspend fun getByStatus(status: TransactionStatus) = list.filter { it.status == status }
    override suspend fun updateStatus(id: String, newStatus: TransactionStatus): Int { val t = getById(id) ?: return 0; upsert(t.copy(status = newStatus)); return 1 }
    override suspend fun setPosted(id: String, ref: com.voiceexpense.data.model.SheetReference, newStatus: TransactionStatus) = updateStatus(id, newStatus)
}

class FakeSheetsClient(var succeed: Boolean = true) : SheetsClient() {
    override suspend fun appendRow(
        accessToken: String,
        spreadsheetId: String,
        sheetName: String,
        values: List<String>
    ): Result<AppendResponse> {
        return if (succeed) Result.success(AppendResponse(spreadsheetId, null, null)) else Result.failure(IllegalStateException("fail"))
    }
}

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    @Test
    fun queuedTransactions_postedOnSuccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val dao = FakeDao()
        val sheets = FakeSheetsClient(succeed = true)
        val auth = AuthRepository(InMemoryStore()).apply { setAccessToken("t") }
        val repo = TransactionRepository(dao, sheets, auth).apply {
            spreadsheetId = "id"
            sheetName = "Sheet1"
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
        dao.upsert(txn)

        // Build WorkerParameters manually with synchronous executors
        val exec = SynchronousExecutor()
        val taskExecutor = object : TaskExecutor {
            override fun getMainThreadExecutor() = exec
            override fun getBackgroundExecutor() = exec
            override fun postToMainThread(runnable: Runnable) { runnable.run() }
            override fun isMainThread(): Boolean = true
        }
        val params = WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            emptySet(),
            WorkerParameters.RuntimeExtras(),
            1,
            exec,
            taskExecutor,
            WorkerFactory.getDefaultWorkerFactory(),
            object : ProgressUpdater { override fun updateProgress(id: UUID, data: Data) {} },
            object : ForegroundUpdater {
                override fun setForegroundAsync(
                    context: android.content.Context,
                    id: UUID,
                    foregroundInfo: ForegroundInfo
                ) = Futures.immediateVoidFuture()
            }
        )
        val worker = SyncWorker(context, params, repo)
        val result = worker.doWork()
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val updated = dao.getById(txn.id)!!
        assertThat(updated.status).isEqualTo(TransactionStatus.POSTED)
    }
}
