package com.voiceexpense.ui.confirmation

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.model.TransactionStatus
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ValidationEngineTest {
    @Test fun expense_requires_amount_and_category() {
        val t = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = null,
            merchant = "M",
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
        val res = ValidationEngine().validate(t)
        assertThat(res.formValid).isFalse()
        assertThat(res.fieldErrors.keys).containsAtLeast("amount", "expenseCategory")
    }

    @Test fun overall_must_be_gte_amount_when_present() {
        val t = Transaction(
            userLocalDate = LocalDate.now(),
            amountUsd = BigDecimal("10.00"),
            merchant = "M",
            description = null,
            type = TransactionType.Expense,
            expenseCategory = "Food",
            incomeCategory = null,
            tags = emptyList(),
            account = null,
            splitOverallChargedUsd = BigDecimal("5.00"),
            note = null,
            confidence = 1f,
            status = TransactionStatus.DRAFT
        )
        val res = ValidationEngine().validate(t)
        assertThat(res.formValid).isFalse()
        assertThat(res.fieldErrors["overall"]).isNotEmpty()
    }
}

