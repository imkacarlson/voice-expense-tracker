package com.voiceexpense.ui.confirmation.voice

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PromptRendererTest {
    private fun draft(amount: BigDecimal? = BigDecimal("12.34"), type: TransactionType = TransactionType.Expense) = Transaction(
        userLocalDate = LocalDate.of(2025, 1, 1),
        amountUsd = amount,
        merchant = "Cafe",
        description = "latte",
        type = type,
        expenseCategory = if (type == TransactionType.Expense) "Food" else null,
        incomeCategory = if (type == TransactionType.Income) "Salary" else null,
        tags = emptyList(),
        account = null,
        splitOverallChargedUsd = null,
        note = null,
        confidence = 1f,
        status = TransactionStatus.DRAFT
    )

    @Test fun summary_contains_core_fields_and_hint() {
        val r = PromptRenderer()
        val s = r.summary(draft())
        assertThat(s).contains("12.34")
        assertThat(s).contains("Cafe")
        assertThat(s).contains("Say yes to save")
    }

    @Test fun askMissing_points_out_amount() {
        val r = PromptRenderer()
        val text = r.askMissing(setOf(Field.Amount))
        assertThat(text.lowercase()).contains("need amount")
    }

    @Test fun clarify_unknown_type_message() {
        val r = PromptRenderer()
        val c = r.clarify(Ambiguity.UnknownType)
        assertThat(c.lowercase()).contains("expense, income, or transfer")
    }
}

