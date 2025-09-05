package com.voiceexpense.ai.parsing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.time.LocalDate

class TransactionParserTest {
    private val ml: MlKitClient = mockk(relaxed = true)
    private val parser = TransactionParser(mlKit = ml)

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

    @Test
    fun parsesExpenseExample() = runBlocking {
        val res = parser.parse("I spent 23 at Starbucks for coffee")
        assertThat(res.type).isEqualTo("Expense")
    }

    @Test
    fun jsonValidatorValidAndInvalidTags() {
        val valid = """
            {"amountUsd": 23.0, "merchant":"Starbucks", "type":"Expense", "tags":["Coffee"], "confidence":0.9}
        """.trimIndent()
        val invalid = """
            {"amountUsd": 23.0, "merchant":"Starbucks", "type":"Expense", "tags":"Coffee", "confidence":0.9}
        """.trimIndent()
        val v1 = StructuredOutputValidator.validateTransactionJson(valid)
        val v2 = StructuredOutputValidator.validateTransactionJson(invalid)
        println("jsonValidator: valid(valid)=${v1.valid} err=${v1.error}")
        println("jsonValidator: valid(invalid)=${v2.valid} err=${v2.error}")
        assertThat(v1.valid).isTrue()
        assertThat(v2.valid).isFalse()
    }
}

class TransactionParserGenAiTest {
    @Test
    fun genai_success_path_maps_json() = runBlocking {
        val mm = mockk<com.voiceexpense.ai.model.ModelManager>()
        every { mm.isModelReady() } returns true

        val ml = mockk<MlKitClient>()
        every { ml.isAvailable() } returns true
        val json = """
            {"amountUsd": 10.0, "merchant":"Cafe", "description":"latte", "type":"Expense", "expenseCategory":"Dining", "tags":["coffee"], "confidence":0.9}
        """.trimIndent()
        coEvery { ml.structured(any()) } returns Result.success(json)

        val parser = TransactionParser(mm, ml)
        val res = parser.parse("spent 10 at cafe")
        assertThat(res.type).isEqualTo("Expense")
        assertThat(res.merchant).isEqualTo("Cafe")
        assertThat(res.amountUsd?.toPlainString()).isEqualTo("10.00")
    }

    @Test
    fun genai_failure_falls_back_to_heuristic() = runBlocking {
        val mm = mockk<com.voiceexpense.ai.model.ModelManager>()
        every { mm.isModelReady() } returns true

        val ml = mockk<MlKitClient>()
        every { ml.isAvailable() } returns true
        coEvery { ml.structured(any()) } returns Result.failure(IllegalStateException("bad output"))

        val parser = TransactionParser(mm, ml)
        val res = parser.parse("Income paycheck two thousand")
        assertThat(res.type).isEqualTo("Income")
        // income heuristic chooses Salary category by default
        assertThat(res.incomeCategory).isEqualTo("Salary")
    }
}
