package com.voiceexpense.ai.parsing.hybrid

import android.util.Log
import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.StructuredOutputValidator
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import com.voiceexpense.ai.parsing.heuristic.toParsedResult
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale
import kotlin.system.measureTimeMillis

/**
 * Orchestrates hybrid parsing: AI-first with strict validation and a
 * reliable heuristic fallback.
 */
class HybridTransactionParser(
    private val genai: GenAiGateway,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val heuristicExtractor: HeuristicExtractor = HeuristicExtractor(),
    private val thresholds: FieldConfidenceThresholds = FieldConfidenceThresholds.DEFAULT
) {
    suspend fun parse(input: String, context: ParsingContext = ParsingContext()): HybridParsingResult {
        Log.i(TAG, "HybridTransactionParser.parse() start text='${input.take(120)}'")
        val heuristicDraft = heuristicExtractor.extract(input, context)
        try {
            Log.d("AI.Debug", "Heuristic draft coverage=${heuristicDraft.coverageScore}")
        } catch (_: Throwable) {}
        Log.i(TAG, "Heuristic confidences=${heuristicDraft.confidences} coverage=${heuristicDraft.coverageScore}")

        var parsed: ParsedResult? = null
        var usedAi = false
        var validated = false
        var rawJson: String? = null
        var errors: List<String> = emptyList()

        val elapsed = measureTimeMillis {
            val shouldCallAi = heuristicDraft.requiresAi(thresholds)
            val genAiAvailable = genai.isAvailable()
            Log.i(TAG, "shouldCallAi=$shouldCallAi genaiAvailable=$genAiAvailable circuitOpen=${com.voiceexpense.ai.error.AiErrorHandler.isHybridCircuitOpen()}")
            try { Log.d("AI.Debug", "shouldCallAi=$shouldCallAi") } catch (_: Throwable) {}

            if (shouldCallAi && genAiAvailable) {
                try { Log.d("AI.Debug", "Building prompt for input: ${input.take(100)}") } catch (_: Throwable) {}
                val prompt = promptBuilder.build(input, context, heuristicDraft)
                try {
                    Log.d("AI.Debug", "Full prompt built, length=${prompt.length}")
                    logPrompt(prompt)
                } catch (_: Throwable) {}
                try { Log.d("AI.Debug", "Calling genai.structured()") } catch (_: Throwable) {}
                val ai = genai.structured(prompt)
                val ok = ai.getOrNull()
                try {
                    Log.d("AI.Debug", "AI response received, length=${ok?.length ?: 0}")
                    Log.d("AI.Debug", "Full AI response: '${ok}'")
                } catch (_: Throwable) {}
                if (!ok.isNullOrBlank()) {
                    Log.i(TAG, "LLM returned payload length=${ok.length}")
                    try { Log.d("AI.Debug", "Calling ValidationPipeline.validateRawResponse()") } catch (_: Throwable) {}
                    val outcome = ValidationPipeline.validateRawResponse(ok)
                    try { Log.d("AI.Debug", "Validation outcome: valid=${outcome.valid}, errors=${outcome.errors}") } catch (_: Throwable) {}
                    if (outcome.valid && !outcome.normalizedJson.isNullOrBlank()) {
                        try { Log.d("AI.Debug", "Calling mapJsonToParsedResult()") } catch (_: Throwable) {}
                        parsed = mapJsonToParsedResult(outcome.normalizedJson, context)
                        try { Log.d("AI.Debug", "mapJsonToParsedResult() completed successfully") } catch (_: Throwable) {}
                        usedAi = true
                        validated = true
                        rawJson = outcome.normalizedJson
                        try {
                            val snippet = outcome.normalizedJson.replace("\n", " ").take(500)
                            Log.i("AI.Output", snippet)
                        } catch (_: Throwable) {}
                        com.voiceexpense.ai.error.AiErrorHandler.resetHybridFailures()
                    } else {
                        errors = listOfNotNull(outcome.errors.joinToString("; ").ifBlank { null })
                        try {
                            val snippet = ok.replace("\n", " ").take(200)
                            Log.w(
                                "AI.Validate",
                                "invalid structured output: error='${errors.firstOrNull() ?: "unknown"}' snippet='$snippet'"
                            )
                        } catch (_: Throwable) {}
                        com.voiceexpense.ai.error.AiErrorHandler.recordHybridFailure()
                    }
                } else {
                    Log.w(TAG, "LLM response nullOrBlank; falling back to heuristics")
                    try { Log.w("AI.Validate", "LLM returned blank output") } catch (_: Throwable) {}
                    com.voiceexpense.ai.error.AiErrorHandler.recordHybridFailure()
                }
            } else {
                if (!shouldCallAi) {
                    Log.i(TAG, "Skipping AI call because heuristics met thresholds")
                    try { Log.d("AI.Debug", "Skipping AI call; heuristic coverage sufficient") } catch (_: Throwable) {}
                } else {
                    Log.w(TAG, "Skipping AI call because genai unavailable")
                    try { Log.d("AI.Debug", "Skipping AI call; genai unavailable") } catch (_: Throwable) {}
                    com.voiceexpense.ai.error.AiErrorHandler.recordHybridFailure()
                }
            }

            parsed = mergeResults(heuristicDraft, parsed, context)
        }

        Log.i(TAG, "HybridTransactionParser.parse() merging completed usedAi=$usedAi validated=$validated rawJsonBlank=${rawJson.isNullOrBlank()} errors=${errors.size}")
        try { Log.d("AI.Debug", "Creating final result, usedAi=$usedAi, validated=$validated") } catch (_: Throwable) {}
        val method = if (usedAi && validated) ProcessingMethod.AI else ProcessingMethod.HEURISTIC
        val stats = ProcessingStatistics(durationMs = elapsed)
        val confidence = ConfidenceScorer.score(method, validated, parsed)
        try { Log.d("AI.Debug", "About to create HybridParsingResult") } catch (_: Throwable) {}
        val result = HybridParsingResult(parsed!!, method, validated, confidence, stats, rawJson, errors)
        try { Log.d("AI.Debug", "HybridParsingResult created, calling ProcessingMonitor.record()") } catch (_: Throwable) {}
        ProcessingMonitor.record(result)
        try { Log.d("AI.Debug", "ProcessingMonitor.record() completed") } catch (_: Throwable) {}
        try {
            Log.i(
                "AI.Parse",
                "method=${method.name} validated=$validated durationMs=${stats.durationMs} errors=${errors.size}"
            )
            // Always log a concise summary with any first error and a small raw JSON snippet
            val err = errors.firstOrNull() ?: ""
            val rawSnippet = (rawJson ?: "").replace("\n", " ").take(200)
            Log.i("AI.Summary", "method=${method.name} validated=$validated err='${err}' raw='${rawSnippet}'")
        } catch (_: Throwable) { /* ignore logging issues */ }
        try { Log.d("AI.Debug", "About to return result") } catch (_: Throwable) {}
        Log.i(TAG, "HybridTransactionParser.parse() end method=${method.name} confidence=${confidence}")
        return result
    }

    private fun mapJsonToParsedResult(json: String, context: ParsingContext): ParsedResult {
        val o = JSONObject(json)
        fun dec(name: String): BigDecimal? =
            if (o.has(name) && !o.isNull(name)) o.optDouble(name).let { d -> if (d.isNaN()) null else BigDecimal(d.toString()) } else null
        fun decAlias(primary: String, alias: String? = null): BigDecimal? =
            dec(primary) ?: (alias?.let { dec(it) })
        fun optStringOrNull(name: String): String? = if (o.has(name) && !o.isNull(name)) o.getString(name) else null

        val result = ParsedResult(
            amountUsd = decAlias("amountUsd", alias = "amount"),
            merchant = o.optString("merchant", "").ifBlank { "Unknown" },
            description = optStringOrNull("description"),
            type = o.optString("type", "Expense"),
            expenseCategory = optStringOrNull("expenseCategory"),
            incomeCategory = optStringOrNull("incomeCategory"),
            tags = o.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(),
            userLocalDate = context.defaultDate,
            account = optStringOrNull("account"),
            splitOverallChargedUsd = decAlias("splitOverallChargedUsd", alias = "overall"),
            note = optStringOrNull("note"),
            confidence = o.optDouble("confidence", 0.75).toFloat()
        )
        return StructuredOutputValidator.sanitizeAmounts(result)
    }

    private fun mergeResults(
        heuristic: HeuristicDraft,
        aiParsed: ParsedResult?,
        context: ParsingContext
    ): ParsedResult {
        val base = aiParsed ?: heuristic.toParsedResult(context)

        val amountFromAi = aiParsed?.amountUsd
        val amountFromHeuristic = heuristic.amountUsd
        val preferHeuristicAmount = when {
            amountFromAi == null || amountFromHeuristic == null -> false
            heuristic.confidence(FieldKey.AMOUNT_USD) < thresholds.thresholdFor(FieldKey.AMOUNT_USD) -> false
            amountFromHeuristic < amountFromAi -> false
            else -> true
        }

        val merged = base.copy(
            amountUsd = when {
                preferHeuristicAmount -> amountFromHeuristic
                amountFromAi != null -> amountFromAi
                else -> amountFromHeuristic
            },
            merchant = when {
                !base.merchant.isNullOrBlank() -> base.merchant
                !heuristic.merchant.isNullOrBlank() -> heuristic.merchant ?: "Unknown"
                else -> "Unknown"
            },
            description = aiParsed?.description ?: heuristic.description,
            type = aiParsed?.type ?: heuristic.type ?: base.type,
            expenseCategory = aiParsed?.expenseCategory ?: heuristic.expenseCategory,
            incomeCategory = aiParsed?.incomeCategory ?: heuristic.incomeCategory,
            tags = (base.tags + heuristic.tags).distinct(),
            userLocalDate = heuristic.userLocalDate ?: base.userLocalDate,
            account = aiParsed?.account ?: heuristic.account,
            splitOverallChargedUsd = aiParsed?.splitOverallChargedUsd ?: heuristic.splitOverallChargedUsd,
            note = aiParsed?.note ?: heuristic.note,
            confidence = aiParsed?.confidence ?: heuristic.coverageScore.coerceIn(0f, 1f)
        )
        val normalized = normalizeToAllowedOptions(merged, context)
        return StructuredOutputValidator.sanitizeAmounts(normalized)
    }

    private fun normalizeToAllowedOptions(result: ParsedResult, context: ParsingContext): ParsedResult {
        fun matchOption(value: String?, options: List<String>): String? {
            if (value.isNullOrBlank()) return null
            if (options.isEmpty()) return value
            val trimmed = value.trim()
            val normalized = normalizeToken(trimmed)
            val matched = options.firstOrNull { normalizeToken(it) == normalized }
            return matched ?: if (options.isEmpty()) trimmed else null
        }

        val expenseCategory = matchOption(result.expenseCategory, context.allowedExpenseCategories)
        val incomeCategory = matchOption(result.incomeCategory, context.allowedIncomeCategories)

        val accountOptions = if (context.allowedAccounts.isNotEmpty()) context.allowedAccounts else context.knownAccounts
        val account = matchOption(result.account, accountOptions)

        val tags = if (context.allowedTags.isNotEmpty()) {
            result.tags.mapNotNull { matchOption(it, context.allowedTags) }.distinct()
        } else {
            result.tags
        }

        return result.copy(
            expenseCategory = expenseCategory,
            incomeCategory = incomeCategory,
            account = account,
            tags = tags
        )
    }

    private fun logPrompt(prompt: String) {
        if (prompt.isEmpty()) return
        val chunkSize = 3000
        Log.d("AI.Debug", "Prompt contents start >>>")
        var index = 0
        var chunk = 1
        while (index < prompt.length) {
            val end = (index + chunkSize).coerceAtMost(prompt.length)
            val segment = prompt.substring(index, end)
            Log.d("AI.Debug", "Prompt chunk $chunk:\n$segment")
            index = end
            chunk += 1
        }
        Log.d("AI.Debug", "<<< Prompt contents end (${prompt.length} chars)")
    }

    private fun normalizeToken(value: String): String = value.trim().lowercase(Locale.US)

    companion object {
        private const val TAG = "AI.Trace"
    }
}
