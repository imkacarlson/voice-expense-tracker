package com.voiceexpense.ai.parsing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate

class TransactionParserTest {
    private val parser = TransactionParser()

    @Test
    fun parsesIncomeKeyword() = runBlocking {
        val res = parser.parse("Income, paycheck two thousand", ParsingContext(defaultDate = LocalDate.parse("2025-07-01")))
        assertThat(res.type).isEqualTo("Income")
    }

    @Test
    fun parsesTransferKeywordAndOmitsAmount() = runBlocking {
        val res = parser.parse("Transfer fifty from checking to savings")
        assertThat(res.type).isEqualTo("Transfer")
        assertThat(res.amountUsd).isNull()
    }

    @Test
    fun validatorRejectsCurrencySymbol() {
        val res = ParsedResult(
            amountUsd = null,
            merchant = "M",
            description = "$10 at M",
            type = "Expense",
            expenseCategory = "Dining",
            incomeCategory = null,
            tags = emptyList(),
            userLocalDate = LocalDate.now(),
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.8f
        )
        val v = StructuredOutputValidator.validate(res)
        assertThat(v.valid).isFalse()
    }

    @Test
    fun validatorSplitShareNotExceedOverall() {
        val res = ParsedResult(
            amountUsd = java.math.BigDecimal("30.00"),
            merchant = "M",
            description = null,
            type = "Expense",
            expenseCategory = "Dining",
            incomeCategory = null,
            tags = emptyList(),
            userLocalDate = LocalDate.now(),
            account = null,
            splitOverallChargedUsd = java.math.BigDecimal("20.00"),
            note = null,
            confidence = 0.8f
        )
        val v = StructuredOutputValidator.validate(res)
        assertThat(v.valid).isFalse()
    }
}

