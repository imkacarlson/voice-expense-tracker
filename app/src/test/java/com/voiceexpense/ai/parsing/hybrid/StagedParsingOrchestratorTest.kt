package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.hybrid.FieldRefinementUpdate
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
            thresholds = thresholds
        )
        val snapshot = StagedParsingOrchestrator.Stage1Snapshot(
            heuristicDraft = draft,
            targetFields = emptyList(),
            stage1DurationMs = 0L
        )

        val result = orchestrator.parseStaged("coffee run", ParsingContext(), snapshot)

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
            promptProvider = { prompt ->
                when {
                    prompt.contains("key \"merchant\"") -> Result.success("""{"merchant":"Blue Bottle"}""")
                    prompt.contains("key \"description\"") -> Result.success("""{"description":"Coffee run"}""")
                    prompt.contains("key \"expenseCategory\"") -> Result.success("""{"expenseCategory":"Dining"}""")
                    prompt.contains("key \"account\"") -> Result.success("""{"account":"Checking"}""")
                    prompt.contains("key \"tags\"") -> Result.success("""{"tags":["coffee"]}""")
                    else -> Result.success("{}")
                }
            }
        }
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = gateway,
            focusedPromptBuilder = focusedPromptBuilder,
            thresholds = thresholds
        )
        val target = listOf(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.TAGS,
            FieldKey.ACCOUNT
        )
        val snapshot = StagedParsingOrchestrator.Stage1Snapshot(
            heuristicDraft = draft,
            targetFields = target,
            stage1DurationMs = 0L
        )

        val result = orchestrator.parseStaged("coffee at blue bottle", ParsingContext(), snapshot)

        assertThat(gateway.calls).isEqualTo(5)
        assertThat(result.targetFields).containsExactly(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.TAGS,
            FieldKey.ACCOUNT
        ).inOrder()
        assertThat(result.fieldsRefined).containsExactly(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.TAGS,
            FieldKey.ACCOUNT
        ).inOrder()
        assertThat(result.mergedResult.merchant).isEqualTo("Blue Bottle")
        assertThat(result.mergedResult.description).isEqualTo("Coffee run")
        assertThat(result.mergedResult.expenseCategory).isEqualTo("Dining")
        assertThat(result.mergedResult.account).isEqualTo("Checking")
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
            thresholds = thresholds
        )
        val snapshot = StagedParsingOrchestrator.Stage1Snapshot(
            heuristicDraft = draft,
            targetFields = listOf(FieldKey.MERCHANT, FieldKey.DESCRIPTION, FieldKey.EXPENSE_CATEGORY, FieldKey.TAGS, FieldKey.ACCOUNT),
            stage1DurationMs = 0L
        )

        val result = orchestrator.parseStaged("something", ParsingContext(), snapshot)

        assertThat(gateway.calls).isEqualTo(5)
        assertThat(result.fieldsRefined).isEmpty()
        assertThat(result.refinedFields).isEmpty()
        assertThat(result.mergedResult.merchant).isEqualTo("Unknown")
        assertThat(result.refinementErrors).isNotEmpty()
    }

    @Test
    fun prioritizes_single_field_first() = runBlocking {
        val draft = HeuristicDraft(
            merchant = null,
            description = null,
            expenseCategory = null,
            confidences = mapOf(
                FieldKey.MERCHANT to 0.1f,
                FieldKey.DESCRIPTION to 0.2f,
                FieldKey.EXPENSE_CATEGORY to 0.2f
            )
        )
        val gateway = FakeGenAiGateway().apply {
            promptProvider = { prompt ->
                when {
                    prompt.contains("key \"merchant\"") -> Result.success("""{"merchant":"REI"}""")
                    prompt.contains("key \"description\"") -> Result.success("""{"description":"Tent"}""")
                    prompt.contains("key \"expenseCategory\"") -> Result.success("""{"expenseCategory":"Outdoors"}""")
                    prompt.contains("key \"account\"") -> Result.success("""{"account":"Gear Card"}""")
                    else -> Result.success("{}")
                }
            }
        }
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = gateway,
            focusedPromptBuilder = focusedPromptBuilder,
            thresholds = thresholds
        )
        val snapshot = StagedParsingOrchestrator.Stage1Snapshot(
            heuristicDraft = draft,
            targetFields = listOf(FieldKey.MERCHANT, FieldKey.DESCRIPTION, FieldKey.EXPENSE_CATEGORY, FieldKey.ACCOUNT),
            stage1DurationMs = 0L
        )

        val updates = mutableListOf<FieldRefinementUpdate>()

        val result = orchestrator.parseStaged(
            input = "bought a tent at rei",
            context = ParsingContext(),
            stage1Snapshot = snapshot,
            listener = { updates += it }
        )

        assertThat(gateway.calls).isEqualTo(4)
        assertThat(result.fieldsRefined).containsExactly(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.ACCOUNT
        )
        assertThat(result.mergedResult.merchant).isEqualTo("REI")
        assertThat(result.mergedResult.description).isEqualTo("Tent")
        assertThat(result.mergedResult.expenseCategory).isEqualTo("Outdoors")
        assertThat(result.mergedResult.account).isEqualTo("Gear Card")
        assertThat(result.refinementErrors).isEmpty()
        assertThat(gateway.promptsTried.first()).contains("Field: Merchant")
        assertThat(gateway.promptsTried[1]).contains("Field: Description")
        assertThat(updates.map(FieldRefinementUpdate::field)).containsExactly(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.ACCOUNT
        ).inOrder()
    }

    private class FakeGenAiGateway : GenAiGateway {
        var available: Boolean = true
        var result: Result<String> = Result.success("{}")
        var resultProvider: ((attempt: Int) -> Result<String>)? = null
        var promptProvider: ((String) -> Result<String>)? = null
        var calls: Int = 0
        var lastPrompt: String? = null
        val promptsTried = mutableListOf<String>()

        override fun isAvailable(): Boolean = available

        override suspend fun structured(prompt: String): Result<String> {
            calls += 1
            lastPrompt = prompt
            promptsTried += prompt
            return promptProvider?.invoke(prompt)
                ?: resultProvider?.invoke(calls)
                ?: result
        }
    }
}
