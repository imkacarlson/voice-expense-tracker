package com.voiceexpense.ai.parsing

import android.util.Log
import com.voiceexpense.ai.model.ModelManager
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import com.voiceexpense.ai.parsing.heuristic.toParsedResult
import com.voiceexpense.ai.parsing.hybrid.HybridParsingResult
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.ProcessingMethod
import com.voiceexpense.ai.parsing.hybrid.ProcessingStatistics

class TransactionParser(
    @Suppress("unused") private val mlKit: ModelManager = ModelManager(),
    private val hybrid: HybridTransactionParser,
    private val heuristicExtractor: HeuristicExtractor = HeuristicExtractor()
) {
    // MediaPipe LLM is used via HybridTransactionParser; falls back to heuristics when needed.
    suspend fun parse(text: String, context: ParsingContext = ParsingContext()): ParsedResult {
        return parseDetailed(text, context).result
    }

    suspend fun parseDetailed(text: String, context: ParsingContext = ParsingContext()): HybridParsingResult {
        Log.i(TAG, "TransactionParser.parse() start input='${text.take(120)}'")
        val hybridOutcome = runCatching { hybrid.parse(text, context) }
        val hybridRes = hybridOutcome.getOrNull()
        if (hybridRes != null) {
            Log.i(TAG, "TransactionParser.parse() hybrid.method=${hybridRes.method} validated=${hybridRes.validated}")
            return hybridRes
        }

        val throwable = hybridOutcome.exceptionOrNull()
        if (throwable != null) {
            Log.w(TAG, "TransactionParser.parse() hybrid failed: ${throwable.message}", throwable)
        }

        val fallbackDraft = heuristicExtractor.extract(text, context)
        Log.w(TAG, "TransactionParser.parse() hybrid returned null, using pure heuristic result")
        val parsed = fallbackDraft.toParsedResult(context)
        return HybridParsingResult(
            result = parsed,
            method = ProcessingMethod.HEURISTIC,
            validated = false,
            confidence = parsed.confidence,
            stats = ProcessingStatistics(durationMs = 0L),
            rawJson = null,
            errors = listOfNotNull(throwable?.message),
            staged = null
        )
    }

    companion object {
        private const val TAG = "AI.Trace"
    }
}
