package com.voiceexpense.ai.parsing.hybrid

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class StagedParsingPerformanceTest {

    private val promptBuilder = PromptBuilder()
    private val heuristicExtractor = com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor()

    @Test
    fun stage_one_completes_under_100ms() = runBlocking {
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = DelayGateway(delayMs = 0, responseJson = MINIMAL_JSON),
            focusedPromptBuilder = FocusedPromptBuilder(),
            thresholds = FieldConfidenceThresholds.DEFAULT,
            heuristicProvider = { _, _ -> highConfidenceDraft() }
        )

        val result = orchestrator.parseStaged("simple input", ParsingContext())

        assertThat(result.stage1DurationMs).isLessThan(100)
        assertThat(result.stage2DurationMs).isEqualTo(0)
        assertThat(result.fieldsRefined).isEmpty()
    }

    @Test
    fun stage_two_typical_case_under_two_seconds() = runBlocking {
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = DelayGateway(delayMs = 1500, responseJson = FOCUSED_JSON),
            focusedPromptBuilder = FocusedPromptBuilder(),
            thresholds = FieldConfidenceThresholds.DEFAULT,
            heuristicProvider = { _, _ -> lowConfidenceDraft() }
        )

        val result = orchestrator.parseStaged("coffee purchase", ParsingContext())

        assertThat(result.fieldsRefined).isNotEmpty()
        assertThat(result.stage2DurationMs).isAtMost(2000)
    }

    @Test
    fun staged_total_latency_beats_legacy_prompt() = runBlocking {
        val gateway = PromptAwareGateway()
        val thresholds = FieldConfidenceThresholds.DEFAULT

        val stagedParser = HybridTransactionParser(
            genai = gateway,
            promptBuilder = promptBuilder,
            heuristicExtractor = heuristicExtractor,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )

        val legacyParser = HybridTransactionParser(
            genai = gateway,
            promptBuilder = promptBuilder,
            heuristicExtractor = heuristicExtractor,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )

        val stagedElapsed = measureTimeMillis {
            stagedParser.parse(STAGED_INPUT, ParsingContext())
        }

        val legacyElapsed = measureTimeMillis {
            legacyParser.parse(STAGED_INPUT, ParsingContext())
        }

        assertThat(stagedElapsed).isLessThan(legacyElapsed)
    }

    private class DelayGateway(
        private val delayMs: Long,
        private val responseJson: String
    ) : GenAiGateway {
        override fun isAvailable(): Boolean = true

        override suspend fun structured(prompt: String): Result<String> {
            delay(delayMs)
            return Result.success(responseJson)
        }
    }

    private class PromptAwareGateway : GenAiGateway {
        override fun isAvailable(): Boolean = true

        override suspend fun structured(prompt: String): Result<String> {
            val simulatedDelay = (prompt.length / 2).coerceAtLeast(100)
            delay(simulatedDelay.toLong())
            return Result.success(MINIMAL_JSON)
        }
    }

    private fun highConfidenceDraft(): HeuristicDraft = HeuristicDraft(
        merchant = "Trader Joe's",
        description = "Groceries",
        expenseCategory = "Groceries",
        incomeCategory = null,
        tags = listOf("food"),
        note = "",
        confidences = mapOf(
            FieldKey.MERCHANT to 0.95f,
            FieldKey.DESCRIPTION to 0.95f,
            FieldKey.EXPENSE_CATEGORY to 0.95f,
            FieldKey.TAGS to 0.9f,
            FieldKey.NOTE to 0.9f
        )
    )

    private fun lowConfidenceDraft(): HeuristicDraft = HeuristicDraft(
        merchant = null,
        description = null,
        expenseCategory = null,
        incomeCategory = null,
        tags = emptyList(),
        note = null,
        confidences = FieldSelectionStrategy.AI_REFINABLE_FIELDS.associateWith { 0.1f }
    )

    companion object {
        private const val MINIMAL_JSON = "{" +
            "\"merchant\":\"Trader Joe's\"," +
            "\"description\":\"Groceries\"," +
            "\"expenseCategory\":\"Groceries\"}"

        private const val FOCUSED_JSON = MINIMAL_JSON

        private const val STAGED_INPUT = "Dinner last night at Trader Joe's for 32 dollars"
    }
}
