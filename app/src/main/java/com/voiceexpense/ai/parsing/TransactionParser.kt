package com.voiceexpense.ai.parsing

import com.voiceexpense.ai.model.ModelManager
import org.json.JSONObject
import java.math.BigDecimal

class TransactionParser(
    private val modelManager: ModelManager = ModelManager(),
    private val mlKit: MlKitClient
) {
    private val hybrid by lazy {
        com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser(
            object : com.voiceexpense.ai.parsing.hybrid.GenAiGateway {
                override fun isAvailable(): Boolean = mlKit.isAvailable()
                override suspend fun structured(prompt: String): Result<String> = mlKit.structured(prompt)
            }
        )
    }
    // Placeholder for ML Kit GenAI (Gemini Nano) integration. Structured for easy swap-in.
    suspend fun parse(text: String, context: ParsingContext = ParsingContext()): ParsedResult {
        // Attempt hybrid GenAI path when model is ready
        if (modelManager.isModelReady()) {
            val hybridRes = runCatching { hybrid.parse(text, context) }.getOrNull()
            if (hybridRes != null && hybridRes.validated && hybridRes.method == com.voiceexpense.ai.parsing.hybrid.ProcessingMethod.AI) {
                return hybridRes.result
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

    // Legacy GenAI path removed in favor of hybrid orchestrator

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
