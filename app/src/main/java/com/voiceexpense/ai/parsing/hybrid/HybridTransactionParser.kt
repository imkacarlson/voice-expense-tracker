package com.voiceexpense.ai.parsing.hybrid

import android.util.Log
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import com.voiceexpense.ai.parsing.heuristic.toParsedResult
import kotlin.system.measureTimeMillis

/**
 * Orchestrates hybrid parsing: AI-first with strict validation and a
 * reliable heuristic fallback.
 */
class HybridTransactionParser(
    private val genai: GenAiGateway,
    private val heuristicExtractor: HeuristicExtractor = HeuristicExtractor(),
    private val thresholds: FieldConfidenceThresholds = FieldConfidenceThresholds.DEFAULT,
    private val stagedConfig: StagedParsingConfig = StagedParsingConfig()
) {
    private val stagedOrchestrator: StagedParsingOrchestrator by lazy {
        StagedParsingOrchestrator(
            heuristicExtractor = heuristicExtractor,
            genAiGateway = genai,
            focusedPromptBuilder = FocusedPromptBuilder(),
            thresholds = thresholds
        )
    }

    private suspend fun parseHeuristicsOnly(
        input: String,
        context: ParsingContext
    ): HybridParsingResult {
        Log.i(TAG, "Heuristic-only parsing start text='${input.take(120)}'")
        var draft: HeuristicDraft
        val duration = measureTimeMillis {
            draft = heuristicExtractor.extract(input, context)
        }
        val parsed = draft.toParsedResult(context)
        val stats = ProcessingStatistics(durationMs = duration)
        val method = ProcessingMethod.HEURISTIC
        val validated = false
        val confidence = ConfidenceScorer.score(method, validated, parsed)
        val result = HybridParsingResult(
            result = parsed,
            method = method,
            validated = validated,
            confidence = confidence,
            stats = stats,
            rawJson = null,
            errors = emptyList(),
            staged = null
        )
        try { Log.d("AI.Debug", "HybridParsingResult (heuristic-only) ready; recording metrics") } catch (_: Throwable) {}
        ProcessingMonitor.record(result)
        try {
            Log.i(
                "AI.Parse",
                "method=${method.name} validated=$validated durationMs=${stats.durationMs} errors=0"
            )
            Log.i(
                "AI.Summary",
                "method=${method.name} validated=$validated refined='none' err='' stage1=${duration} stage2=0"
            )
        } catch (_: Throwable) { /* ignore logging issues */ }
        return result
    }

    suspend fun prepareStage1Snapshot(
        input: String,
        context: ParsingContext = ParsingContext()
    ): StagedParsingOrchestrator.Stage1Snapshot {
        val snapshot = stagedOrchestrator.prepareStage1(input, context)
        return if (stagedConfig.enabled) snapshot else snapshot.copy(targetFields = emptyList())
    }

    suspend fun completeStagedParsing(
        input: String,
        context: ParsingContext,
        stage1Snapshot: StagedParsingOrchestrator.Stage1Snapshot,
        listener: FieldRefinementListener? = null
    ): HybridParsingResult {
        return if (!stagedConfig.enabled) {
            parse(input, context)
        } else {
            parseStaged(input, context, stage1Snapshot, listener)
        }
    }
    suspend fun parse(input: String, context: ParsingContext = ParsingContext()): HybridParsingResult {
        Log.i(TAG, "HybridTransactionParser.parse() start text='${input.take(120)}'")
        return if (stagedConfig.enabled) {
            parseStaged(input, context)
        } else {
            parseHeuristicsOnly(input, context)
        }
    }

    private suspend fun parseStaged(
        input: String,
        context: ParsingContext,
        stage1Snapshot: StagedParsingOrchestrator.Stage1Snapshot? = null,
        listener: FieldRefinementListener? = null
    ): HybridParsingResult {
        val staged = stagedOrchestrator.parseStaged(input, context, stage1Snapshot, listener)
        val method = if (staged.fieldsRefined.isNotEmpty()) ProcessingMethod.AI else ProcessingMethod.HEURISTIC
        val validated = staged.fieldsRefined.isNotEmpty() && staged.refinementErrors.isEmpty()
        val confidence = ConfidenceScorer.score(method, validated, staged.mergedResult)
        val stats = ProcessingStatistics(durationMs = staged.totalDurationMs)
        Log.i(
            TAG,
            "Staged parsing result method=${method.name} refined=${staged.fieldsRefined.size} errors=${staged.refinementErrors.size}"
        )
        val result = HybridParsingResult(
            result = staged.mergedResult,
            method = method,
            validated = validated,
            confidence = confidence,
            stats = stats,
            rawJson = null,
            errors = staged.refinementErrors,
            staged = staged
        )
        try { Log.d("AI.Debug", "HybridParsingResult (staged) ready; recording metrics") } catch (_: Throwable) {}
        ProcessingMonitor.record(result)
        try {
            Log.i(
                "AI.Parse",
                "method=${method.name} validated=$validated durationMs=${stats.durationMs} errors=${staged.refinementErrors.size} target=${staged.targetFields.size} refined=${staged.fieldsRefined.size}"
            )
            val err = staged.refinementErrors.firstOrNull() ?: ""
            val refinedSummary = staged.fieldsRefined.joinToString()
            Log.i(
                "AI.Summary",
                "method=${method.name} validated=$validated refined='${refinedSummary}' err='${err}' stage1=${staged.stage1DurationMs} stage2=${staged.stage2DurationMs}"
            )
        } catch (_: Throwable) { /* ignore logging issues */ }
        return result
    }

    data class StagedParsingConfig(
        val enabled: Boolean = true
    )

    companion object {
        private const val TAG = "AI.Trace"
    }
}
