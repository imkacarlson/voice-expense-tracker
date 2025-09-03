package com.voiceexpense.data.repository

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.remote.AppendResponse
import com.voiceexpense.data.remote.SheetsClient
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RepoDaoFake2 : TransactionDao by com.voiceexpense.worker.FakeDao()

class FakeTokenProvider(var token: String) : TokenProvider {
    var invalidated = false
    override suspend fun getAccessToken(accountEmail: String, scope: String): String = token
    override suspend fun invalidateToken(accountEmail: String, scope: String) { invalidated = true }
}

class RepositoryAuthTests {
    @Test
    fun sync_401ThenSuccess_invalidatesAndRetries() = runBlocking {
        // Sheets client that fails first with 401, then succeeds
        class Sheets401 : SheetsClient() {
            var calls = 0
            override suspend fun appendRow(
                accessToken: String,
                spreadsheetId: String,
                sheetName: String,
                values: List<String>
            ): Result<AppendResponse> {
                calls++
                return if (calls == 1) Result.failure(IllegalStateException("Sheets append failed: HTTP 401 Unauthorized"))
                else Result.success(AppendResponse(spreadsheetId, null, AppendResponse.Updates("$sheetName!A2:M2", 1, 13, 13)))
            }
        }

        val dao = com.voiceexpense.worker.FakeDao()
        val auth = AuthRepository(InMemoryStore()).apply { setAccount("user", "user@example.com") }
        val tokenProvider = FakeTokenProvider("t1")
        val sheets = Sheets401()
        val repo = TransactionRepository(dao, sheets, auth, tokenProvider).apply {
            spreadsheetId = "id"; sheetName = "Sheet1"
        }

        val txn = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("1.00"),
            merchant = "M",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Cat",
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 1f,
            status = TransactionStatus.QUEUED
        )
        dao.upsert(txn)

        val res = repo.syncPending()
        assertThat(res.posted).isEqualTo(1)
        assertThat(tokenProvider.invalidated).isTrue()
    }

    @Test
    fun sync_withoutAccount_skipsAndFails() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val auth = AuthRepository(InMemoryStore()) // no account set
        val tokenProvider = FakeTokenProvider("t")
        val sheets = SheetsClient()
        val repo = TransactionRepository(dao, sheets, auth, tokenProvider).apply {
            spreadsheetId = "id"; sheetName = "Sheet1"
        }

        val txn = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("1.00"),
            merchant = "M",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Cat",
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 1f,
            status = TransactionStatus.QUEUED
        )
        dao.upsert(txn)

        val res = repo.syncPending()
        assertThat(res.attempted).isEqualTo(1)
        assertThat(res.posted).isEqualTo(0)
        assertThat(res.failed).isEqualTo(1)
    }
}

