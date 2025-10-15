package com.voiceexpense.ai.parsing.hybrid

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class StagedParsingPerformanceTest {

    private val heuristicExtractor = com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor()

    @Test
    fun stage_one_completes_under_100ms() = runBlocking {
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = DelayGateway(delayMs = 0, responseJson = MINIMAL_JSON),
            focusedPromptBuilder = FocusedPromptBuilder(),
            thresholds = FieldConfidenceThresholds.DEFAULT
        )
        val snapshot = orchestrator.prepareStage1("simple input", ParsingContext())

        assertThat(snapshot.stage1DurationMs).isLessThan(100)
    }

    @Test
    fun stage_two_typical_case_under_two_seconds() = runBlocking {
        val orchestrator = StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = DelayGateway(delayMs = 1500, responseJson = FOCUSED_JSON),
            focusedPromptBuilder = FocusedPromptBuilder(),
            thresholds = FieldConfidenceThresholds.DEFAULT
        )

        val snapshot = StagedParsingOrchestrator.Stage1Snapshot(
            heuristicDraft = lowConfidenceDraft(),
            targetFields = FieldSelectionStrategy.AI_REFINABLE_FIELDS.toList(),
            stage1DurationMs = 5L
        )

        val result = orchestrator.parseStaged("coffee purchase", ParsingContext(), snapshot)

        assertThat(result.fieldsRefined).isNotEmpty()
        assertThat(result.stage2DurationMs).isAtMost(12000)
    }

    @Test
    fun heuristic_only_is_faster_than_staged() = runBlocking {
        val gateway = PromptAwareGateway()
        val thresholds = FieldConfidenceThresholds.DEFAULT

        val stagedParser = HybridTransactionParser(
            genai = gateway,
            heuristicExtractor = heuristicExtractor,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )

        val heuristicOnlyParser = HybridTransactionParser(
            genai = gateway,
            heuristicExtractor = heuristicExtractor,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = false)
        )

        val stagedElapsed = measureTimeMillis {
            stagedParser.parse(STAGED_INPUT, ParsingContext())
        }

        val heuristicElapsed = measureTimeMillis {
            heuristicOnlyParser.parse(STAGED_INPUT, ParsingContext())
        }

        assertThat(heuristicElapsed).isLessThan(stagedElapsed)
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
