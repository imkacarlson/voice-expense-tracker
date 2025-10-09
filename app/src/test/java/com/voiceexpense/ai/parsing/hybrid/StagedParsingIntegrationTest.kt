package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import com.voiceexpense.ai.parsing.hybrid.ProcessingMethod
import kotlinx.coroutines.runBlocking
import org.junit.Test

class StagedParsingIntegrationTest {

    private val heuristicExtractor = HeuristicExtractor()

    @Test
    fun staged_enabled_refines_low_confidence_fields() = runBlocking {
        val response = """{"merchant":"Trader Joe's","description":"Grocery run","expenseCategory":"Groceries","account":"Bilt Card"}"""
        val gateway = IntegrationFakeGateway(Result.success(response))
        val thresholds = FieldConfidenceThresholds(
            mandatoryThresholds = mapOf(
                FieldKey.MERCHANT to 0.95f,
                FieldKey.DESCRIPTION to 0.95f,
                FieldKey.EXPENSE_CATEGORY to 0.95f
            ),
            defaultThreshold = 0.9f
        )
        val parser = HybridTransactionParser(
            genai = gateway,
            promptBuilder = PromptBuilder(),
            heuristicExtractor = heuristicExtractor,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )

        val context = ParsingContext(
            allowedExpenseCategories = listOf("Groceries"),
            allowedAccounts = listOf("Bilt Card", "Checking")
        )

        val result = parser.parse("Bought groceries for 45 dollars at Trader Joes", context)

        assertThat(gateway.calls).isEqualTo(5)
        assertThat(result.method).isEqualTo(ProcessingMethod.AI)
        assertThat(result.validated).isTrue()
        assertThat(result.result.merchant).isEqualTo("Trader Joe's")
        assertThat(result.result.expenseCategory).isEqualTo("Groceries")
        assertThat(result.result.account).isEqualTo("Bilt Card")
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun staged_disabled_uses_legacy_pipeline() = runBlocking {
        val response = "{" +
            "\"merchant\":\"Blue Bottle\"," +
            "\"description\":\"Coffee run\"," +
            "\"type\":\"Expense\"," +
            "\"expenseCategory\":\"Dining\"," +
            "\"tags\":[\"coffee\"]," +
            "\"userLocalDate\":\"2025-10-07\"," +
            "\"confidence\":0.8}"
        val gateway = IntegrationFakeGateway(Result.success(response))
        val parser = HybridTransactionParser(
            genai = gateway,
            promptBuilder = PromptBuilder(),
            heuristicExtractor = heuristicExtractor,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )

        val result = parser.parse("Coffee 6 dollars at Blue Bottle", ParsingContext())

        assertThat(gateway.calls).isEqualTo(1)
        assertThat(result.method).isEqualTo(ProcessingMethod.AI)
        assertThat(result.rawJson).isNotNull()
        assertThat(result.result.merchant).isEqualTo("Blue Bottle")
        assertThat(result.result.description).isEqualTo("Coffee run")
    }

    private class IntegrationFakeGateway(var result: Result<String>) : GenAiGateway {
        var calls: Int = 0

        override fun isAvailable(): Boolean = true

        override suspend fun structured(prompt: String): Result<String> {
            calls += 1
            return result
        }
    }
}
