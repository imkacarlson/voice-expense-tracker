package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal

class StagedParsingOrchestratorTest {

    private val heuristicExtractor = HeuristicExtractor()
    private val focusedPromptBuilder = FocusedPromptBuilder()
    private val thresholds = FieldConfidenceThresholds.DEFAULT

    @Test
    fun skips_ai_when_no_fields_need_refinement() = runBlocking {
        val draft = HeuristicDraft(
            amountUsd = BigDecimal("12.00"),
            merchant = "Starbucks",
            description = "Latte",
            type = "Expense",
            expenseCategory = "Dining",
            incomeCategory = "Salary",
            tags = listOf("morning"),
            note = "routine",
            confidences = mapOf(
                FieldKey.MERCHANT to 0.9f,
                FieldKey.DESCRIPTION to 0.85f,
                FieldKey.EXPENSE_CATEGORY to 0.9f,
                FieldKey.INCOME_CATEGORY to 0.9f,
                FieldKey.TAGS to 0.9f,
                FieldKey.NOTE to 0.8f
            )
        )
        val gateway = FakeGenAiGateway()
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = gateway,
            focusedPromptBuilder = focusedPromptBuilder,
            thresholds = thresholds,
            heuristicProvider = { _, _ -> draft }
        )

        val result = orchestrator.parseStaged("coffee run", ParsingContext())

        assertThat(gateway.calls).isEqualTo(0)
        assertThat(result.fieldsRefined).isEmpty()
        assertThat(result.targetFields).isEmpty()
        assertThat(result.refinedFields).isEmpty()
        assertThat(result.refinementErrors).isEmpty()
        assertThat(result.mergedResult.merchant).isEqualTo("Starbucks")
    }

    @Test
    fun applies_ai_refinements_when_fields_selected() = runBlocking {
        val draft = HeuristicDraft(
            merchant = null,
            description = null,
            type = "Expense",
            expenseCategory = null,
            tags = emptyList(),
            note = null,
            confidences = mapOf(
                FieldKey.MERCHANT to 0.2f,
                FieldKey.DESCRIPTION to 0.1f,
                FieldKey.EXPENSE_CATEGORY to 0.1f,
                FieldKey.TAGS to 0.05f
            )
        )
        val gateway = FakeGenAiGateway().apply {
            result = Result.success(
                """{"merchant":"Blue Bottle","description":"Coffee run","expenseCategory":"Dining","tags":["coffee"]}"""
            )
        }
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = gateway,
            focusedPromptBuilder = focusedPromptBuilder,
            thresholds = thresholds,
            heuristicProvider = { _, _ -> draft }
        )

        val result = orchestrator.parseStaged("coffee at blue bottle", ParsingContext())

        assertThat(gateway.calls).isEqualTo(1)
        assertThat(result.targetFields).containsAtLeast(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.TAGS
        )
        assertThat(result.fieldsRefined).containsExactly(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.TAGS
        )
        assertThat(result.mergedResult.merchant).isEqualTo("Blue Bottle")
        assertThat(result.mergedResult.description).isEqualTo("Coffee run")
        assertThat(result.mergedResult.expenseCategory).isEqualTo("Dining")
        assertThat(result.mergedResult.tags).containsExactly("coffee")
        assertThat(result.refinementErrors).isEmpty()
    }

    @Test
    fun falls_back_to_heuristics_when_ai_fails() = runBlocking {
        val draft = HeuristicDraft(
            merchant = "Unknown",
            description = null,
            type = "Expense",
            expenseCategory = null,
            confidences = mapOf(
                FieldKey.MERCHANT to 0.2f,
                FieldKey.DESCRIPTION to 0.1f,
                FieldKey.EXPENSE_CATEGORY to 0.1f
            )
        )
        val gateway = FakeGenAiGateway().apply {
            result = Result.failure(IllegalStateException("model offline"))
        }
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = gateway,
            focusedPromptBuilder = focusedPromptBuilder,
            thresholds = thresholds,
            heuristicProvider = { _, _ -> draft }
        )

        val result = orchestrator.parseStaged("something", ParsingContext())

        assertThat(gateway.calls).isEqualTo(1)
        assertThat(result.fieldsRefined).isEmpty()
        assertThat(result.refinedFields).isEmpty()
        assertThat(result.mergedResult.merchant).isEqualTo("Unknown")
        assertThat(result.refinementErrors).isNotEmpty()
    }

    private class FakeGenAiGateway : GenAiGateway {
        var available: Boolean = true
        var result: Result<String> = Result.success("{}")
        var calls: Int = 0
        var lastPrompt: String? = null

        override fun isAvailable(): Boolean = available

        override suspend fun structured(prompt: String): Result<String> {
            calls += 1
            lastPrompt = prompt
            return result
        }
    }
}
