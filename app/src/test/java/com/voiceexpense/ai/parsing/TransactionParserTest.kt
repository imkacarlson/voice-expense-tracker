package com.voiceexpense.ai.parsing

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import kotlinx.coroutines.runBlocking
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.time.LocalDate

class TransactionParserTest {
    private val mmDisabled = mockk<com.voiceexpense.ai.model.ModelManager>().apply {
        every { isModelReady() } returns false
    }
    private val dummyGateway = object : GenAiGateway {
        override fun isAvailable(): Boolean = false
        override suspend fun structured(prompt: String): Result<String> =
            Result.failure(Exception("unavailable"))
    }
    private val hybrid = HybridTransactionParser(dummyGateway)
    private val parser = TransactionParser(mmDisabled, hybrid)

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
    fun sanitize_amounts_enforces_share_not_exceed_overall() {
        val res = ParsedResult(
            amountUsd = java.math.BigDecimal("15.00"),
            merchant = "Test",
            description = null,
            type = "Expense",
            expenseCategory = null,
            incomeCategory = null,
            tags = emptyList(),
            userLocalDate = LocalDate.now(),
            account = null,
            splitOverallChargedUsd = java.math.BigDecimal("10.00"),
            note = null,
            confidence = 0.6f
        )
        val sanitized = StructuredOutputValidator.sanitizeAmounts(res)
        assertThat(sanitized.splitOverallChargedUsd).isEqualTo(java.math.BigDecimal("15.00"))
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

        val json = """
            {"amountUsd": 10.0, "merchant":"Cafe", "description":"latte", "type":"Expense", "expenseCategory":"Dining", "tags":["coffee"], "confidence":0.9}
        """.trimIndent()
        val gateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = true
            override suspend fun structured(prompt: String): Result<String> = Result.success(json)
        }
        val hybrid = HybridTransactionParser(gateway)
        val parser = TransactionParser(mm, hybrid)
        val res = parser.parse("spent 10 at cafe")
        assertThat(res.type).isEqualTo("Expense")
        assertThat(res.merchant).isEqualTo("Cafe")
        assertThat(res.amountUsd?.toPlainString()).isEqualTo("10.00")
    }

    @Test
    fun genai_failure_falls_back_to_heuristic() = runBlocking {
        val mm = mockk<com.voiceexpense.ai.model.ModelManager>()
        every { mm.isModelReady() } returns true

        val gateway = object : GenAiGateway {
            override fun isAvailable(): Boolean = true
            override suspend fun structured(prompt: String): Result<String> = Result.failure(IllegalStateException("bad output"))
        }
        val hybrid = HybridTransactionParser(gateway)
        val parser = TransactionParser(mm, hybrid)
        val res = parser.parse("Income paycheck two thousand")
        assertThat(res.type).isEqualTo("Income")
        // income heuristic chooses Salary category by default
        assertThat(res.incomeCategory).isEqualTo("Salary")
    }
}
