package com.voiceexpense.ui.confirmation

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import com.voiceexpense.ai.parsing.hybrid.StagedRefinementDispatcher
import io.mockk.mockk
import io.mockk.every
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
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
        val hybrid = HybridTransactionParser(
            dummyGateway,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )
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

    @Test
    fun low_confidence_flag_exposed() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String): Result<String> = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(
            dummyGateway,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid))
        val low = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("3.00"),
            merchant = "Cafe",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Other",
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.4f
        )
        vm.setDraft(low)
        mainRule.runCurrent()
        val state = vm.confidence.value
        assertThat(state.confidence).isEqualTo(0.4f)
        assertThat(state.isLowConfidence).isTrue()
    }

    @Test
    fun apply_manual_edits_updates_confidence_state() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String): Result<String> = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(
            dummyGateway,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid))
        val base = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("8.00"),
            merchant = "Cafe",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Other",
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.8f
        )
        vm.setDraft(base)
        mainRule.runCurrent()
        val updated = base.copy(confidence = 0.5f)
        vm.applyManualEdits(updated)
        mainRule.runCurrent()
        val state = vm.confidence.value
        assertThat(state.confidence).isEqualTo(0.5f)
        assertThat(state.isLowConfidence).isTrue()
    }

    @Test
    fun setHeuristicDraft_updates_loading_states() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String): Result<String> = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(
            dummyGateway,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid))
        val base = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("8.00"),
            merchant = "Cafe",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Other",
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.8f
        )
        vm.setHeuristicDraft(base, setOf(FieldKey.MERCHANT, FieldKey.DESCRIPTION))
        mainRule.runCurrent()
        val loading = vm.fieldLoadingStates.value
        assertThat(loading[FieldKey.MERCHANT]).isTrue()
        assertThat(loading[FieldKey.DESCRIPTION]).isTrue()
        assertThat(loading[FieldKey.EXPENSE_CATEGORY]).isFalse()
    }

    @Test
    fun staged_refinement_event_updates_fields_and_confidence() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String): Result<String> = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(
            dummyGateway,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid))
        val base = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("8.00"),
            merchant = "Unknown",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Other",
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.5f
        )
        vm.setHeuristicDraft(base, setOf(FieldKey.MERCHANT))
        mainRule.runCurrent()

        StagedRefinementDispatcher.emit(
            StagedRefinementDispatcher.RefinementEvent(
                transactionId = base.id,
                refinedFields = mapOf(FieldKey.MERCHANT to "Blue Bottle"),
                targetFields = setOf(FieldKey.MERCHANT),
                errors = emptyList(),
                stage1DurationMs = 10,
                stage2DurationMs = 100,
                confidence = 0.9f
            )
        )

        mainRule.runCurrent()

        val updated = vm.transaction.value!!
        assertThat(updated.merchant).isEqualTo("Blue Bottle")
        assertThat(vm.fieldLoadingStates.value[FieldKey.MERCHANT]).isFalse()
        assertThat(vm.confidence.value.confidence).isEqualTo(0.9f)
    }

    @Test
    fun applyAiRefinement_updates_transaction_when_not_user_modified() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String): Result<String> = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(
            dummyGateway,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid))
        val draft = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("45.00"),
            merchant = "Unknown",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = null,
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.6f
        )
        vm.setHeuristicDraft(draft, setOf(FieldKey.MERCHANT))
        mainRule.runCurrent()

        vm.applyAiRefinement(FieldKey.MERCHANT, "Trader Joe's")
        mainRule.runCurrent()

        val updated = vm.transaction.value!!
        assertThat(updated.merchant).isEqualTo("Trader Joe's")
        assertThat(vm.fieldLoadingStates.value[FieldKey.MERCHANT]).isFalse()
    }

    @Test
    fun applyAiRefinement_skips_when_user_modified() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply { every { isModelReady() } returns false }
        val dummyGateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = false
            override suspend fun structured(prompt: String): Result<String> = Result.failure(Exception("unavailable"))
        }
        val hybrid = HybridTransactionParser(
            dummyGateway,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )
        val vm = ConfirmationViewModel(repo, TransactionParser(mmDisabled, hybrid))
        val draft = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("45.00"),
            merchant = "Local Store",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = null,
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.6f
        )
        vm.setHeuristicDraft(draft, setOf(FieldKey.MERCHANT))
        mainRule.runCurrent()

        vm.markFieldUserModified(FieldKey.MERCHANT)
        vm.applyAiRefinement(FieldKey.MERCHANT, "Trader Joe's")
        mainRule.runCurrent()

        val updated = vm.transaction.value!!
        assertThat(updated.merchant).isEqualTo("Local Store")
        assertThat(vm.fieldLoadingStates.value[FieldKey.MERCHANT]).isFalse()
    }
}
