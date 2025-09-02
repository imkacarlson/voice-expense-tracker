package com.voiceexpense.ui.confirmation

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FakeDao2 : TransactionDao by com.voiceexpense.worker.FakeDao()

class ConfirmationViewModelTest {
    @Test
    fun applyCorrection_updatesAmount() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val vm = ConfirmationViewModel(repo, TransactionParser())
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
        vm.applyCorrection("actually 2.50")
        val updated = vm.transaction.value!!
        assertThat(updated.amountUsd?.toPlainString()).isEqualTo("2.50")
        assertThat(updated.correctionsCount).isEqualTo(1)
    }

    @Test
    fun confirm_enqueues() = runBlocking {
        val dao = com.voiceexpense.worker.FakeDao()
        val repo = TransactionRepository(dao)
        val vm = ConfirmationViewModel(repo, TransactionParser())
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
        vm.confirm()
        val updated = dao.getById(t.id)!!
        // confirm() marks CONFIRMED then enqueues for sync
        assertThat(updated.status).isEqualTo(TransactionStatus.QUEUED)
    }
}
