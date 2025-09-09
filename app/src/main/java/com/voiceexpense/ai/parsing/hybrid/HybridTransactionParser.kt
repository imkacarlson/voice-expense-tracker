package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.*
import android.util.Log
import org.json.JSONObject
import java.math.BigDecimal
import kotlin.system.measureTimeMillis

/**
 * Orchestrates hybrid parsing: AI-first with strict validation and a
 * reliable heuristic fallback.
 */
class HybridTransactionParser(
    private val genai: GenAiGateway,
    private val promptBuilder: PromptBuilder = PromptBuilder()
) {
    suspend fun parse(input: String, context: ParsingContext = ParsingContext()): HybridParsingResult {
        var parsed: ParsedResult? = null
        var usedAi = false
        var validated = false
        var rawJson: String? = null
        var errors: List<String> = emptyList()

        val elapsed = measureTimeMillis {
            // Attempt AI path when model ready
            if (genai.isAvailable()) {
                val prompt = promptBuilder.build(input, context)
                val ai = genai.structured(prompt)
                val ok = ai.getOrNull()
                if (!ok.isNullOrBlank()) {
                    val outcome = ValidationPipeline.validateRawResponse(ok)
                    if (outcome.valid && !outcome.normalizedJson.isNullOrBlank()) {
                        parsed = mapJsonToParsedResult(outcome.normalizedJson, context)
                        usedAi = true
                        validated = true
                        rawJson = outcome.normalizedJson
                        com.voiceexpense.ai.error.AiErrorHandler.resetHybridFailures()
                        return@measureTimeMillis
                    } else {
                        errors = listOfNotNull(outcome.errors.joinToString("; ").ifBlank { null })
                        // Extra validation logging
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
                    try { Log.w("AI.Validate", "LLM returned blank output") } catch (_: Throwable) {}
                    com.voiceexpense.ai.error.AiErrorHandler.recordHybridFailure()
                }
            }

            // Heuristic fallback
            parsed = heuristicParse(input, context)
        }

        val method = if (usedAi) ProcessingMethod.AI else ProcessingMethod.HEURISTIC
        val stats = ProcessingStatistics(durationMs = elapsed)
        val confidence = ConfidenceScorer.score(method, validated, parsed)
        val result = HybridParsingResult(parsed!!, method, validated, confidence, stats, rawJson, errors)
        ProcessingMonitor.record(result)
        try {
            Log.i(
                "AI.Parse",
                "method=${method.name} validated=$validated durationMs=${stats.durationMs} errors=${errors.size}"
            )
        } catch (_: Throwable) { /* ignore logging issues */ }
        return result
    }

    private fun mapJsonToParsedResult(json: String, context: ParsingContext): ParsedResult {
        val o = JSONObject(json)
        fun dec(name: String): BigDecimal? =
            if (o.has(name) && !o.isNull(name)) o.optDouble(name).let { d -> if (d.isNaN()) null else BigDecimal(d.toString()) } else null
        fun optStringOrNull(name: String): String? = if (o.has(name) && !o.isNull(name)) o.getString(name) else null

        val result = ParsedResult(
            amountUsd = dec("amountUsd"),
            merchant = o.optString("merchant", "").ifBlank { "Unknown" },
            description = optStringOrNull("description"),
            type = o.optString("type", "Expense"),
            expenseCategory = optStringOrNull("expenseCategory"),
            incomeCategory = optStringOrNull("incomeCategory"),
            tags = o.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(),
            userLocalDate = context.defaultDate,
            account = optStringOrNull("account"),
            splitOverallChargedUsd = dec("splitOverallChargedUsd"),
            note = optStringOrNull("note"),
            confidence = o.optDouble("confidence", 0.75).toFloat()
        )
        return StructuredOutputValidator.sanitizeAmounts(result)
    }

    private fun heuristicParse(text: String, context: ParsingContext): ParsedResult {
        val lower = text.lowercase()
        val isIncome = lower.contains("income") || lower.contains("paycheck") || lower.contains("refund")
        val isTransfer = lower.contains("transfer") || lower.contains("moved")
        val type = when {
            isTransfer -> "Transfer"
            isIncome -> "Income"
            else -> "Expense"
        }
        val amountRegex = Regex("(\\d+)(?:\\.(\\d{1,2}))?")
        val amount = amountRegex.find(text)?.value?.let { BigDecimal(it) }
        val base = ParsedResult(
            amountUsd = if (type == "Transfer") null else amount,
            merchant = if (type == "Expense") guessMerchant(text, context) ?: "Unknown" else "",
            description = null,
            type = type,
            expenseCategory = if (type == "Expense") "Uncategorized" else null,
            incomeCategory = if (type == "Income") "Salary" else null,
            tags = extractTags(text),
            userLocalDate = context.defaultDate,
            account = extractAccount(text, context),
            splitOverallChargedUsd = extractOverall(text),
            note = null,
            confidence = 0.6f
        )
        return StructuredOutputValidator.sanitizeAmounts(base)
    }

    private fun guessMerchant(text: String, context: ParsingContext): String? =
        context.recentMerchants.firstOrNull { text.contains(it, ignoreCase = true) }

    private fun extractTags(text: String): List<String> {
        val idx = text.indexOf("tags:", ignoreCase = true)
        if (idx == -1) return emptyList()
        return text.substring(idx + 5).split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun extractAccount(text: String, context: ParsingContext): String? {
        return context.knownAccounts.firstOrNull {
            text.contains(it.filter { ch -> ch.isDigit() }, ignoreCase = true) || text.contains(it, ignoreCase = true)
        }
    }

    private fun extractOverall(text: String): BigDecimal? {
        val match = Regex("overall charged (\\d+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text)
        return match?.groups?.get(1)?.value?.let { BigDecimal(it) }
    }
}
