package com.voiceexpense.data.repository

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import kotlinx.coroutines.runBlocking
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.InMemoryStore
import com.voiceexpense.data.remote.AppendResponse
import com.voiceexpense.data.remote.SheetsClient
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class RepoDaoFake : TransactionDao by com.voiceexpense.worker.FakeDao()

class TransactionRepositoryTest {
    private val dao = RepoDaoFake()
    private val repo = TransactionRepository(dao)

    @Test
    fun mapToSheetRow_expense_hasAmountAndExpenseCategory() {
        val t = base().copy(
            type = TransactionType.Expense,
            amountUsd = BigDecimal("12.34"),
            expenseCategory = "Dining",
            incomeCategory = null,
            account = "Bilt Card (5217)",
            tags = listOf("Subscription", "Auto-Paid")
        )
        val row = repo.mapToSheetRow(t)
        assertThat(row[2]).isEqualTo("12.34")
        assertThat(row[5]).isEqualTo("Dining")
        assertThat(row[6]).isEqualTo("Subscription, Auto-Paid")
        assertThat(row[7]).isEqualTo("") // Income Category blank
    }

    @Test
    fun mapToSheetRow_income_hasIncomeCategoryAndAmount() {
        val t = base().copy(
            type = TransactionType.Income,
            amountUsd = BigDecimal("2000.00"),
            expenseCategory = null,
            incomeCategory = "Salary"
        )
        val row = repo.mapToSheetRow(t)
        assertThat(row[2]).isEqualTo("2000.00")
        assertThat(row[5]).isEqualTo("")
        assertThat(row[7]).isEqualTo("Salary")
    }

    @Test
    fun mapToSheetRow_transfer_blanksAmount() {
        val t = base().copy(
            type = TransactionType.Transfer,
            amountUsd = null
        )
        val row = repo.mapToSheetRow(t)
        assertThat(row[2]).isEqualTo("")
    }

    @Test
    fun syncPending_401ThenSuccess_postsAndSetsSheetRef() = runBlocking {
        class FakeSheets : SheetsClient() {
            var calls = 0
            override suspend fun appendRow(
                accessToken: String,
                spreadsheetId: String,
                sheetName: String,
                values: List<String>
            ): Result<AppendResponse> {
                calls++
                return if (calls == 1) {
                    Result.failure(IllegalStateException("Sheets append failed: HTTP 401 Unauthorized"))
                } else {
                    Result.success(
                        AppendResponse(
                            spreadsheetId = spreadsheetId,
                            tableRange = null,
                            updates = AppendResponse.Updates("$sheetName!A5:M5", 1, 13, 13)
                        )
                    )
                }
            }
        }

        val sheets = FakeSheets()
        val auth = AuthRepository(InMemoryStore()).apply { setAccessToken("token") }
        val repo2 = TransactionRepository(dao, sheets, auth).apply {
            spreadsheetId = "sheetId"
            sheetName = "Sheet1"
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
        note = null,
        confidence = 1f,
        status = TransactionStatus.DRAFT
    )
}
