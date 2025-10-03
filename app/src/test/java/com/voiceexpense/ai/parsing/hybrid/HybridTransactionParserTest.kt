package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.ParsedResult
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeGateway(
    var available: Boolean = true,
    var response: Result<String> = Result.failure(IllegalStateException("no response"))
) : GenAiGateway {
    var callCount: Int = 0
    override fun isAvailable(): Boolean = available
    override suspend fun structured(prompt: String): Result<String> {
        callCount += 1
        return response
    }
}

class HybridTransactionParserTest {
    @Test
    fun ai_success_path_returns_validated_ai_result() = runBlocking {
        val json = "{" +
            "\"amountUsd\":4.75," +
            "\"merchant\":\"Starbucks\"," +
            "\"type\":\"Expense\"," +
            "\"tags\":[\"coffee\"]," +
            "\"userLocalDate\":\"2025-01-01\"," +
            "\"confidence\":0.8}"
        val gw = FakeGateway(available = true, response = Result.success(json))
        val parser = HybridTransactionParser(gw)
        val res = parser.parse("spent 4.75 at starbucks", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.AI)
        assertThat(res.validated).isTrue()
        assertThat(res.result.merchant).isEqualTo("Starbucks")
        assertThat(res.result.type).isEqualTo("Expense")
        assertThat(gw.callCount).isEqualTo(1)
    }

    @Test
    fun ai_invalid_falls_back_to_heuristic() = runBlocking {
        val invalid = "{\"tags\":\"coffee\"}" // invalid tags type
        val gw = FakeGateway(available = true, response = Result.success(invalid))
        val parser = HybridTransactionParser(gw)
        val res = parser.parse("transfer 100 from checking to savings", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.validated).isFalse()
        assertThat(res.result.type).isEqualTo("Transfer")
        assertThat(gw.callCount).isEqualTo(1)
    }

    @Test
    fun unavailable_gateway_uses_heuristic() = runBlocking {
        val gw = FakeGateway(available = false)
        val parser = HybridTransactionParser(gw)
        val res = parser.parse("coffee 3 at starbucks", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.result.type).isEqualTo("Expense")
        assertThat(res.validated).isFalse()
        assertThat(gw.callCount).isEqualTo(0)
    }

    @Test
    fun heuristic_coverage_skips_ai_call() = runBlocking {
        val gw = FakeGateway(available = true)
        val parser = HybridTransactionParser(gw)
        val context = ParsingContext(
            defaultDate = java.time.LocalDate.of(2025, 9, 13),
            allowedAccounts = listOf("Citi Double Cash Card")
        )

        val res = parser.parse(
            "On September 12th I spent 11.10 getting a takeout pizza from Domino's on my Citi Double Cash card",
            context
        )

        assertThat(gw.callCount).isEqualTo(0)
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.result.userLocalDate).isEqualTo(java.time.LocalDate.of(2025, 9, 12))
        assertThat(res.result.account).isEqualTo("Citi Double Cash Card")
        assertThat(res.result.amountUsd?.toPlainString()).isEqualTo("11.10")
    }

    @Test
    fun heuristic_smaller_amount_does_not_override_ai() = runBlocking {
        val json = "{\"amountUsd\":425,\"merchant\":\"Vanguard Cash Plus\",\"type\":\"Income\"}"
        val gw = FakeGateway(available = true, response = Result.success(json))
        val parser = HybridTransactionParser(gw)
        val context = ParsingContext(defaultDate = java.time.LocalDate.of(2025, 9, 13))

        val res = parser.parse(
            "On September 12th I got my paycheck deposit into my Vanguard Cash Plus account and it came out to 425 dollars",
            context
        )

        assertThat(res.method).isEqualTo(ProcessingMethod.AI)
        assertThat(res.result.amountUsd?.toPlainString()).isEqualTo("425.00")
    }

    @Test
    fun ai_outlier_amount_uses_heuristic_value() = runBlocking {
        val json = "{\"amountUsd\":1110000000000,\"merchant\":\"Domino's\"}"
        val gw = FakeGateway(available = true, response = Result.success(json))
        val parser = HybridTransactionParser(gw)
        val context = ParsingContext(
            defaultDate = java.time.LocalDate.of(2025, 9, 13),
            allowedAccounts = listOf("Citi Double Cash Card")
        )

        val res = parser.parse(
            "On September 12th I spent $11.10 getting a takeout pizza from Domino's on my Citi Double Cash card",
            context
        )

        assertThat(res.result.amountUsd?.toPlainString()).isEqualTo("11.10")
    }

    @Test
    fun normalizes_to_allowed_options() = runBlocking {
        val json = "{" +
            "\"amountUsd\":11.12," +
            "\"merchant\":\"Gas Bill\"," +
            "\"type\":\"Expense\"," +
            "\"expenseCategory\":\"utilities\"," +
            "\"tags\":[\"splitwise\"]," +
            "\"account\":\"vanguard cash plus (savings)\"," +
            "\"userLocalDate\":\"2025-09-11\"," +
            "\"confidence\":0.8}"
        val gw = FakeGateway(available = true, response = Result.success(json))
        val parser = HybridTransactionParser(gw)
        val context = ParsingContext(
            defaultDate = java.time.LocalDate.of(2025, 9, 13),
            allowedExpenseCategories = listOf("Utilities", "Groceries"),
            allowedTags = listOf("Auto-Paid", "Splitwise"),
            allowedAccounts = listOf("Vanguard Cash Plus (Savings)")
        )

        val res = parser.parse(
            "On September 11th the gas bill was charged to my Vanguard Cash Plus account for 22.24 and I owe 11.12",
            context
        )

        assertThat(res.validated).isTrue()
        assertThat(res.result.expenseCategory).isEqualTo("Utilities")
        assertThat(res.result.account).isEqualTo("Vanguard Cash Plus (Savings)")
        assertThat(res.result.tags).containsExactly("Splitwise")
    }
}
