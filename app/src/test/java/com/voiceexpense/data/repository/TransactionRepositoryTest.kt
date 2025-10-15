package com.voiceexpense.data.repository

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import kotlinx.coroutines.runBlocking
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.data.remote.AppsScriptClient
import com.voiceexpense.data.remote.AppsScriptRequest
import com.voiceexpense.data.remote.AppsScriptResponse
import com.voiceexpense.data.remote.AppsScriptResponseData
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class RepoDaoFake : TransactionDao {
    private val base = com.voiceexpense.worker.FakeDao()
    override suspend fun upsert(transaction: Transaction) = base.upsert(transaction)
    override suspend fun upsertAll(transactions: List<Transaction>) = base.upsertAll(transactions)
    override suspend fun update(transaction: Transaction): Int = base.update(transaction)
    override suspend fun getById(id: String): Transaction? = base.getById(id)
    override suspend fun deleteById(id: String): Int = base.deleteById(id)
    override fun observeAll() = base.observeAll()
    override fun observeByStatus(status: TransactionStatus) = base.observeByStatus(status)
    override suspend fun getByStatus(status: TransactionStatus): List<Transaction> = base.getByStatus(status)
    override suspend fun updateStatus(id: String, newStatus: TransactionStatus): Int = base.updateStatus(id, newStatus)
    override suspend fun setPosted(id: String, ref: com.voiceexpense.data.model.SheetReference, newStatus: TransactionStatus): Int {
        val current = base.getById(id) ?: return 0
        return base.update(current.copy(status = newStatus, sheetRef = ref))
    }
}

class TransactionRepositoryTest {
    private val dao = RepoDaoFake()
    private val repo = TransactionRepository(dao)

    @Test
    fun mapToAppsRequest_expense_hasAmountAndExpenseCategory() {
        val t = base().copy(
            type = TransactionType.Expense,
            amountUsd = BigDecimal("12.34"),
            expenseCategory = "Dining",
            incomeCategory = null,
            account = "Bilt Card",
            tags = listOf("Subscription", "Auto-Paid")
        )
        val req = repo.mapToAppsScriptRequest(t, token = "tok")
        assertThat(req.amount).isEqualTo("12.34")
        assertThat(req.expenseCategory).isEqualTo("Dining")
        assertThat(req.tags).isEqualTo("Subscription, Auto-Paid")
        assertThat(req.incomeCategory).isNull()
    }

    @Test
    fun mapToAppsRequest_income_hasIncomeCategoryAndAmount() {
        val t = base().copy(
            type = TransactionType.Income,
            amountUsd = BigDecimal("2000.00"),
            expenseCategory = null,
            incomeCategory = "Salary"
        )
        val req = repo.mapToAppsScriptRequest(t, token = "tok")
        assertThat(req.amount).isEqualTo("2000.00")
        assertThat(req.expenseCategory).isNull()
        assertThat(req.incomeCategory).isEqualTo("Salary")
    }

    @Test
    fun mapToAppsRequest_transfer_blanksAmount() {
        val t = base().copy(
            type = TransactionType.Transfer,
            amountUsd = null
        )
        val req = repo.mapToAppsScriptRequest(t, token = "tok")
        assertThat(req.amount).isNull()
    }

    @Test
    fun syncPending_401ThenSuccess_posts() = runBlocking {
        class FakeApps : AppsScriptClient(okhttp3.OkHttpClient(), com.squareup.moshi.Moshi.Builder().build()) {
            var calls = 0
            override suspend fun postExpense(url: String, request: AppsScriptRequest): Result<AppsScriptResponse> {
                calls++
                return if (calls == 1) Result.failure(IllegalStateException("AppsScript post failed: HTTP 401 Unauthorized"))
                else Result.success(AppsScriptResponse("success", null, null, AppsScriptResponseData(null, null, null, 5)))
            }
        }

        val apps = FakeApps()
        val auth = AuthRepository(InMemoryStore()).apply { setAccessToken("token"); setAccount("user","user@example.com") }
        val repo2 = TransactionRepository(dao, apps, auth, com.voiceexpense.auth.StaticTokenProvider("token")).apply {
            webAppUrl = "https://script.example/exec"
        }

        val txn = base().copy(
            type = TransactionType.Expense,
            amountUsd = BigDecimal("10.00"),
            expenseCategory = "Dining",
            status = TransactionStatus.QUEUED
        )
        dao.upsert(txn)

        val res = repo2.syncPending()
        assertThat(res.attempted).isAtLeast(1)
        val updated = dao.getById(txn.id)!!
        assertThat(updated.status).isEqualTo(TransactionStatus.POSTED)
        assertThat(updated.sheetRef?.rowIndex).isEqualTo(5)
    }

    private fun base(): Transaction = Transaction(
        createdAt = Instant.parse("2025-07-15T04:21:47Z"),
        userLocalDate = LocalDate.parse("2025-07-14"),
        amountUsd = null,
        merchant = "Test",
        description = null,
        type = TransactionType.Expense,
        expenseCategory = null,
        incomeCategory = null,
        tags = emptyList(),
        account = null,
        splitOverallChargedUsd = null,
        confidence = 1f,
        status = TransactionStatus.DRAFT
    )

    @Test
    fun getById_returnsInsertedDraftOrNull() = runBlocking {
        val t = base()
        dao.upsert(t)
        val found = repo.getById(t.id)
        assertThat(found?.id).isEqualTo(t.id)
        val missing = repo.getById("nope")
        assertThat(missing).isNull()
    }
}
