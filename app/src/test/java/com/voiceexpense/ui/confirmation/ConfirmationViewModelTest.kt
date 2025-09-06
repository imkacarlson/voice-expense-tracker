package com.voiceexpense.ui.confirmation

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import com.voiceexpense.ai.parsing.hybrid.PromptBuilder
import io.mockk.mockk
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import com.voiceexpense.ui.confirmation.voice.CorrectionIntentParser
import com.voiceexpense.ui.confirmation.voice.PromptRenderer
import com.voiceexpense.ui.confirmation.voice.TtsEngine
import com.voiceexpense.ui.confirmation.voice.VoiceCorrectionController
import org.junit.Test
import org.junit.Rule
import com.voiceexpense.testutil.MainDispatcherRule
import java.math.BigDecimal
import java.time.LocalDate

class FakeDao2 : TransactionDao by com.voiceexpense.worker.FakeDao()

class ConfirmationViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()
    @Test
    fun applyCorrection_updatesAmount() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val controller = VoiceCorrectionController(
            tts = object : TtsEngine() { override suspend fun speak(text: String) { /* no-op */ } },
            parser = CorrectionIntentParser(),
            renderer = PromptRenderer(),
            scope = CoroutineScope(mainRule.testDispatcher)
        )
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String) = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(dummyGateway, PromptBuilder())
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid), controller)
        val t = Transaction(
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
            status = TransactionStatus.DRAFT
        )
        vm.setDraft(t)
        mainRule.runCurrent()
        vm.applyCorrection("actually 2.50")
        mainRule.runCurrent()
        val updated = vm.transaction.value!!
        assertThat(updated.amountUsd?.toPlainString()).isEqualTo("2.50")
        assertThat(updated.correctionsCount).isEqualTo(1)
    }

    @Test
    fun confirm_enqueues() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val controller = VoiceCorrectionController(
            tts = object : TtsEngine() { override suspend fun speak(text: String) { /* no-op */ } },
            parser = CorrectionIntentParser(),
            renderer = PromptRenderer(),
            scope = CoroutineScope(mainRule.testDispatcher)
        )
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String) = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(dummyGateway, PromptBuilder())
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid), controller)
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
