package com.voiceexpense.ui.confirmation

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import com.voiceexpense.ai.parsing.hybrid.PromptBuilder
import io.mockk.mockk
import io.mockk.every
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.junit.Rule
import com.voiceexpense.testutil.MainDispatcherRule
import java.math.BigDecimal
import java.time.LocalDate

class FakeDao2 : TransactionDao by com.voiceexpense.worker.FakeDao()

class ConfirmationViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()
    @Test
    fun confirm_enqueues() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String): Result<String> =
                Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(dummyGateway, PromptBuilder())
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid))
        val t = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("5.00"),
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
            status = TransactionStatus.DRAFT
        )
        dao.upsert(t)
        vm.setDraft(t)
        mainRule.runCurrent()
        vm.confirm()
        mainRule.runCurrent()
        val updated = dao.getById(t.id)!!
        // confirm() marks CONFIRMED then enqueues for sync
        assertThat(updated.status).isEqualTo(TransactionStatus.QUEUED)
    }
}
