package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.StructuredOutputValidator
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

data class ValidationOutcome(
    val valid: Boolean,
    val normalizedJson: String?,
    val errors: List<String>,
    val confidence: Float
)

/**
 * Validates ML Kit raw responses for schema and business constraints, producing
 * a normalized JSON payload and confidence score.
 */
object ValidationPipeline {
    fun validateRawResponse(text: String): ValidationOutcome {
        try { 
            android.util.Log.d("AI.Debug", "ValidationPipeline.validateRawResponse() started, text length=${text.length}")
            android.util.Log.d("AI.Debug", "Raw AI text being validated: '$text'")
        } catch (_: Throwable) {}
        val errs = mutableListOf<String>()

        // 1) Recover the JSON payload from potential prose or code fences
        try { android.util.Log.d("AI.Debug", "Calling normalizeMlKitJson()") } catch (_: Throwable) {}
        val normalized = StructuredOutputValidator.normalizeMlKitJson(text)
        try { 
            android.util.Log.d("AI.Debug", "normalizeMlKitJson() completed, normalized length=${normalized.length}")
            android.util.Log.d("AI.Debug", "Normalized JSON: '$normalized'")
        } catch (_: Throwable) {}

        // 2) Schema-level validation (tags array shape etc.)
        try { android.util.Log.d("AI.Debug", "Calling validateTransactionJson()") } catch (_: Throwable) {}
        val schemaRes = StructuredOutputValidator.validateTransactionJson(normalized)
        try { android.util.Log.d("AI.Debug", "validateTransactionJson() completed, valid=${schemaRes.valid}") } catch (_: Throwable) {}
        if (!schemaRes.valid) {
            // Record schema error but continue to attempt parsing and rule checks
            errs += schemaRes.error ?: "invalid schema"
            try { android.util.Log.d("AI.Debug", "Schema validation failed, continuing for robust handling") } catch (_: Throwable) {}
        }

        // 3) Business rule checks over parsed object
        try { android.util.Log.d("AI.Debug", "Creating JSONObject from normalized JSON") } catch (_: Throwable) {}
        val json = try { JSONObject(normalized) } catch (t: Throwable) {
            errs += "invalid json: ${t.message}"
            try { android.util.Log.d("AI.Debug", "JSONObject creation failed: ${t.message}") } catch (_: Throwable) {}
            return ValidationOutcome(false, null, errs, 0.0f)
        }
        try { android.util.Log.d("AI.Debug", "JSONObject created successfully") } catch (_: Throwable) {}

        // 3a) Field aliasing: tolerate common schema deviations from the LLM
        // - Some models emit "amount" instead of "amountUsd"
        // - Some may emit "overall"/"total" instead of "splitOverallChargedUsd"
        try {
            if (!json.has("amountUsd") && json.has("amount") && !json.isNull("amount")) {
                val amt = json.optDouble("amount")
                if (!amt.isNaN()) json.put("amountUsd", amt)
            }
            if (!json.has("splitOverallChargedUsd")) {
                val overall = when {
                    json.has("overall") && !json.isNull("overall") -> json.optDouble("overall")
                    json.has("total") && !json.isNull("total") -> json.optDouble("total")
                    else -> Double.NaN
                }
                if (!overall.isNaN()) json.put("splitOverallChargedUsd", overall)
            }
        } catch (_: Throwable) { /* best-effort normalization */ }

        // Required/typed fields
        // Be tolerant to non-string or null types; treat anything non-string as empty
        val rawTypeAny: Any? = try { json.opt("type") } catch (_: Throwable) { null }
        val typeTrimmed = (rawTypeAny as? String)?.trim().orEmpty()
        val typeNormalized = when (typeTrimmed.lowercase()) {
            "expense" -> "Expense"
            "income" -> "Income"
            "transfer" -> "Transfer"
            else -> typeTrimmed
        }
        val typeValid = typeNormalized in setOf("Expense", "Income", "Transfer")
        // Be forgiving: default unknown/missing type to Expense instead of erroring
        val type = if (typeValid) typeNormalized else "Expense"
        // Normalize casing/whitespace/default in the output JSON for downstream mapping
        try { json.put("type", type) } catch (_: Throwable) {}

        fun dec(name: String): Double? =
            if (json.has(name) && !json.isNull(name)) json.optDouble(name).let { if (it.isNaN()) null else it } else null

        val amount = dec("amountUsd")
        val overall = dec("splitOverallChargedUsd")
        if (amount != null && overall != null && amount > overall) {
            errs += "share exceeds overall"
        }

        if (type == "Transfer") {
            if (!json.isNull("expenseCategory")) errs += "transfer: expenseCategory must be null"
            if (!json.isNull("incomeCategory")) errs += "transfer: incomeCategory must be null"
        }

        // 4) Confidence scoring based on completeness and rule adherence
        var score = 0.4f // base when schema is valid
        if (typeValid) score += 0.15f
        if (!json.optString("merchant", "").isNullOrBlank()) score += 0.1f
        if (amount != null) score += 0.15f
        if (json.has("tags") && !json.isNull("tags")) score += 0.05f
        if (overall == null || (amount == null || amount <= overall)) score += 0.05f
        if (type == "Transfer" && json.isNull("expenseCategory") && json.isNull("incomeCategory")) score += 0.05f

        // Penalties for errors
        if (errs.isNotEmpty()) score -= (0.1f * errs.size).coerceAtMost(0.3f)

        score = min(1.0f, max(0.0f, score))
        val valid = errs.isEmpty()
        try { android.util.Log.d("AI.Debug", "ValidationPipeline completed, valid=$valid, errors=${errs.size}") } catch (_: Throwable) {}
        return ValidationOutcome(valid, if (valid) json.toString() else null, errs, score)
    }
}
