package com.voiceexpense.data.repository

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.remote.AppsScriptClient
import com.voiceexpense.data.remote.AppsScriptRequest
import com.voiceexpense.data.remote.AppsScriptResponse
import com.voiceexpense.data.remote.AppsScriptResponseData
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
        // Apps Script client that fails first with 401, then succeeds
        class Apps401 : AppsScriptClient(okhttp3.OkHttpClient(), com.squareup.moshi.Moshi.Builder().build()) {
            var calls = 0
            override suspend fun postExpense(url: String, request: AppsScriptRequest): Result<AppsScriptResponse> {
                calls++
                return if (calls == 1) Result.failure(IllegalStateException("AppsScript post failed: HTTP 401 Unauthorized"))
                else Result.success(AppsScriptResponse("success", null, null, AppsScriptResponseData(null, null, null, 2)))
            }
        }

        val dao = com.voiceexpense.worker.FakeDao()
        val auth = AuthRepository(InMemoryStore()).apply { setAccount("user", "user@example.com") }
        val tokenProvider = FakeTokenProvider("t1")
        val apps = Apps401()
        val repo = TransactionRepository(dao, apps, auth, tokenProvider).apply {
            webAppUrl = "https://script.example/exec"
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
        // Avoid real HTTP in unit tests; force a deterministic failure
        val apps = object : AppsScriptClient(okhttp3.OkHttpClient(), com.squareup.moshi.Moshi.Builder().build()) {
            override suspend fun postExpense(url: String, request: AppsScriptRequest): Result<AppsScriptResponse> =
                Result.failure(IllegalStateException("no account configured"))
        }
        val repo = TransactionRepository(dao, apps, auth, tokenProvider).apply {
            webAppUrl = "https://script.example/exec"
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
