package com.voiceexpense.ai.parsing

import com.voiceexpense.ai.model.ModelManager
import org.json.JSONObject
import java.math.BigDecimal

class TransactionParser(
    private val modelManager: ModelManager = ModelManager(),
    private val mlKit: MlKitClient = MlKitClient()
) {
    // Placeholder for ML Kit GenAI (Gemini Nano) integration. Structured for easy swap-in.
    suspend fun parse(text: String, context: ParsingContext = ParsingContext()): ParsedResult {
        // Attempt GenAI path when model is ready (future hook)
        if (modelManager.isModelReady()) {
            val json = runCatching { runGenAi(text, context) }.getOrNull()
            if (!json.isNullOrBlank()) {
                val vr = StructuredOutputValidator.validateTransactionJson(json)
                if (vr.valid) return mapJsonToParsedResult(json, context)
            }
        }

        // Fallback heuristic parser (keeps app functional pre-GenAI)
        val lower = text.lowercase()
        val isIncome = lower.contains("income") || lower.contains("paycheck")
        val isTransfer = lower.contains("transfer")
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
            confidence = 0.5f
        )
        return StructuredOutputValidator.sanitizeAmounts(base)
    }

    // Build prompt and call on-device GenAI via MlKitClient
    private suspend fun runGenAi(text: String, context: ParsingContext): String {
        val status = mlKit.ensureReady()
        if (status !is MlKitClient.Status.Available) return ""

        val system = TransactionPrompts.SYSTEM_INSTRUCTION
        val examples = TransactionPrompts.EXAMPLE_UTTERANCES.joinToString("\n") { "- $it" }
        val composedPrompt = buildString {
            appendLine(system)
            appendLine()
            appendLine("Examples:")
            appendLine(examples)
            appendLine()
            append("Input: ")
            append(text)
        }

        val result = mlKit.rewrite(composedPrompt)
        return result.getOrNull() ?: ""
    }

    private fun mapJsonToParsedResult(json: String, context: ParsingContext): ParsedResult {
        val o = JSONObject(json)
        fun dec(name: String): BigDecimal? =
            if (o.has(name) && !o.isNull(name)) o.optDouble(name).let { d ->
                if (d.isNaN()) null else BigDecimal(d.toString())
            } else null
        fun optStringOrNull(name: String): String? =
            if (o.has(name) && !o.isNull(name)) o.getString(name) else null

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
            confidence = o.optDouble("confidence", 0.7).toFloat()
        )
        return StructuredOutputValidator.sanitizeAmounts(result)
    }

    private fun guessMerchant(text: String, context: ParsingContext): String? {
        return context.recentMerchants.firstOrNull { text.contains(it, ignoreCase = true) }
    }

    private fun extractTags(text: String): List<String> {
        val idx = text.indexOf("tags:", ignoreCase = true)
        if (idx == -1) return emptyList()
        return text.substring(idx + 5).split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun extractAccount(text: String, context: ParsingContext): String? {
        return context.knownAccounts.firstOrNull { text.contains(it.filter { ch -> ch.isDigit() }, ignoreCase = true) || text.contains(it, ignoreCase = true) }
    }

    private fun extractOverall(text: String): BigDecimal? {
        val match = Regex("overall charged (\\d+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text)
        return match?.groups?.get(1)?.value?.let { BigDecimal(it) }
    }
}
