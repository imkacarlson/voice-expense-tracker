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
                try { Log.d("AI.Debug", "Building prompt for input: ${input.take(100)}") } catch (_: Throwable) {}
                val prompt = promptBuilder.build(input, context)
                try { Log.d("AI.Debug", "Calling genai.structured()") } catch (_: Throwable) {}
                val ai = genai.structured(prompt)
                val ok = ai.getOrNull()
                try {
                    val len = ok?.length ?: 0
                    val snippet = (ok ?: "").replace("\n", " ").take(200)
                    Log.d("AI.Debug", "AI response received, length=${len}")
                    Log.d("AI.Debug", "AI response snippet: '${snippet}${if (len > snippet.length) "…" else ""}'")
                } catch (_: Throwable) {}
                if (!ok.isNullOrBlank()) {
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
                        // Log normalized JSON and key fields on success for debugging/verification
                        try {
                            val snippet = outcome.normalizedJson.replace("\n", " ").take(500)
                            Log.i("AI.Output", snippet)
                            val p = parsed
                            if (p != null) {
                                Log.i(
                                    "AI.Fields",
                                    "amount=${p.amountUsd} merchant='${p.merchant}' type=${p.type} expCat=${p.expenseCategory} incCat=${p.incomeCategory} tags=${p.tags.joinToString(";")} account=${p.account} overall=${p.splitOverallChargedUsd}"
                                )
                            }
                        } catch (_: Throwable) {}
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
            try { Log.d("AI.Debug", "Calling heuristicParse() fallback") } catch (_: Throwable) {}
            parsed = heuristicParse(input, context)
            try { Log.d("AI.Debug", "heuristicParse() completed") } catch (_: Throwable) {}
        }

        try { Log.d("AI.Debug", "Creating final result, usedAi=$usedAi, validated=$validated") } catch (_: Throwable) {}
        val method = if (usedAi) ProcessingMethod.AI else ProcessingMethod.HEURISTIC
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
