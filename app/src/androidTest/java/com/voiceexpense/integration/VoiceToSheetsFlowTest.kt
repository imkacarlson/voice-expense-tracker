package com.voiceexpense.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionParser
import io.mockk.mockk
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.SheetReference
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class VoiceToSheetsFlowTest {
    private class InMemoryDao : TransactionDao {
        private val store = LinkedHashMap<String, Transaction>()
        override suspend fun upsert(transaction: Transaction) { store[transaction.id] = transaction }
        override suspend fun upsertAll(transactions: List<Transaction>) { transactions.forEach { store[it.id] = it } }
        override suspend fun update(transaction: Transaction): Int { store[transaction.id] = transaction; return 1 }
        override suspend fun getById(id: String): Transaction? = store[id]
        override suspend fun deleteById(id: String): Int = if (store.remove(id) != null) 1 else 0
        override fun observeAll(): Flow<List<Transaction>> = flowOf(store.values.toList())
        override fun observeByStatus(status: TransactionStatus): Flow<List<Transaction>> =
            flowOf(store.values.filter { it.status == status })
        override suspend fun getByStatus(status: TransactionStatus): List<Transaction> = store.values.filter { it.status == status }
        override suspend fun updateStatus(id: String, newStatus: TransactionStatus): Int {
            val t = store[id] ?: return 0
            store[id] = t.copy(status = newStatus)
            return 1
        }
        override suspend fun setPosted(id: String, ref: SheetReference, newStatus: TransactionStatus): Int {
            val t = store[id] ?: return 0
            store[id] = t.copy(sheetRef = ref, status = newStatus)
            return 1
        }
    }

    @Test
    fun parse_and_map_to_apps_request() = runBlocking {
        val parser = TransactionParser(mlKit = mockk(relaxed = true))
        val parsed = parser.parse("I spent 23 at Starbucks for coffee", ParsingContext(defaultDate = LocalDate.parse("2025-07-02")))
        val txn = Transaction(
            userLocalDate = parsed.userLocalDate,
            amountUsd = parsed.amountUsd?.let { BigDecimal(it.toPlainString()) },
            merchant = parsed.merchant,
            description = parsed.description,
            type = TransactionType.Expense,
            expenseCategory = parsed.expenseCategory,
            incomeCategory = null,
            tags = parsed.tags,
            account = parsed.account,
            splitOverallChargedUsd = parsed.splitOverallChargedUsd,
            confidence = parsed.confidence
        )

        val repo = TransactionRepository(dao = InMemoryDao())
        val req = repo.mapToAppsScriptRequest(txn, token = "tok")
        // Fields: date, amount, description, type, expenseCategory, account, tags
        assertThat(req.date).isEqualTo("07/02/2025")
        assertThat(req.amount).isEqualTo("23.00")
        assertThat(req.description).contains("Starbucks")
        assertThat(req.type).isEqualTo("Expense")
    }
}
