package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.logging.Log
import com.voiceexpense.ai.parsing.StructuredOutputValidator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val mapAdapter = moshi.adapter<MutableMap<String, Any?>>(
        MutableMap::class.java as Class<MutableMap<String, Any?>>
    )

    fun validateRawResponse(text: String): ValidationOutcome {
        try {
            Log.d("AI.Debug", "ValidationPipeline.validateRawResponse() started, text length=${text.length}")
            Log.d("AI.Debug", "Raw AI text being validated: '$text'")
        } catch (_: Throwable) {}
        val errs = mutableListOf<String>()

        // 1) Recover the JSON payload from potential prose or code fences
        try { Log.d("AI.Debug", "Calling normalizeMlKitJson()") } catch (_: Throwable) {}
        val normalized = StructuredOutputValidator.normalizeMlKitJson(text)
        try {
            Log.d("AI.Debug", "normalizeMlKitJson() completed, normalized length=${normalized.length}")
            Log.d("AI.Debug", "Normalized JSON: '$normalized'")
        } catch (_: Throwable) {}

        // 2) Schema-level validation (tags array shape etc.)
        try { Log.d("AI.Debug", "Calling validateTransactionJson()") } catch (_: Throwable) {}
        val schemaRes = StructuredOutputValidator.validateTransactionJson(normalized)
        try { Log.d("AI.Debug", "validateTransactionJson() completed, valid=${schemaRes.valid}") } catch (_: Throwable) {}
        if (!schemaRes.valid) {
            // Record schema error but continue to attempt parsing and rule checks
            errs += schemaRes.error ?: "invalid schema"
            try { Log.d("AI.Debug", "Schema validation failed, continuing for robust handling") } catch (_: Throwable) {}
        }

        // 3) Business rule checks over parsed object
        try { Log.d("AI.Debug", "Creating JSON map from normalized JSON") } catch (_: Throwable) {}
        val json = try { mapAdapter.fromJson(normalized)?.toMutableMap() ?: mutableMapOf() } catch (t: Throwable) {
            errs += "invalid json: ${t.message}"
            try { Log.d("AI.Debug", "JSON parsing failed: ${t.message}") } catch (_: Throwable) {}
            return ValidationOutcome(false, null, errs, 0.0f)
        }
        try { Log.d("AI.Debug", "JSON map created successfully") } catch (_: Throwable) {}

        // 3a) Field aliasing: tolerate common schema deviations from the LLM
        // - Some models emit "amount" instead of "amountUsd"
        // - Some may emit "overall"/"total" instead of "splitOverallChargedUsd"
        try {
            if (!json.containsKey("amountUsd") && json.containsKey("amount") && json["amount"] != null) {
                val amt = (json["amount"] as? Number)?.toDouble()
                if (amt != null && !amt.isNaN()) json["amountUsd"] = amt
            }
            if (!json.containsKey("splitOverallChargedUsd")) {
                val overall = when {
                    json.containsKey("overall") && json["overall"] != null -> (json["overall"] as? Number)?.toDouble()
                    json.containsKey("total") && json["total"] != null -> (json["total"] as? Number)?.toDouble()
                    else -> null
                }
                if (overall != null && !overall.isNaN()) json["splitOverallChargedUsd"] = overall
            }
        } catch (_: Throwable) { /* best-effort normalization */ }

        // Required/typed fields
        // Be tolerant to non-string or null types; treat anything non-string as empty
        val rawTypeAny: Any? = try { json["type"] } catch (_: Throwable) { null }
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
        try { json["type"] = type } catch (_: Throwable) {}

        fun dec(name: String): Double? {
            val value = json[name]
            if (value == null) return null
            val num = (value as? Number)?.toDouble()
            return if (num != null && !num.isNaN()) num else null
        }

        val amount = dec("amountUsd")
        val overall = dec("splitOverallChargedUsd")
        if (amount != null && overall != null && amount > overall) {
            errs += "share exceeds overall"
        }

        if (type == "Transfer") {
            if (json["expenseCategory"] != null) errs += "transfer: expenseCategory must be null"
            if (json["incomeCategory"] != null) errs += "transfer: incomeCategory must be null"
        }

        // 4) Confidence scoring based on completeness and rule adherence
        var score = 0.4f // base when schema is valid
        if (typeValid) score += 0.15f
        val merchant = (json["merchant"] as? String)?.trim()
        if (!merchant.isNullOrBlank()) score += 0.1f
        if (amount != null) score += 0.15f
        if (json.containsKey("tags") && json["tags"] != null) score += 0.05f
        if (overall == null || (amount == null || amount <= overall)) score += 0.05f
        if (type == "Transfer" && json["expenseCategory"] == null && json["incomeCategory"] == null) score += 0.05f

        // Penalties for errors
        if (errs.isNotEmpty()) score -= (0.1f * errs.size).coerceAtMost(0.3f)

        score = min(1.0f, max(0.0f, score))
        val valid = errs.isEmpty()
        try { Log.d("AI.Debug", "ValidationPipeline completed, valid=$valid, errors=${errs.size}") } catch (_: Throwable) {}
        val jsonString = if (valid) mapAdapter.toJson(json) else null
        return ValidationOutcome(valid, jsonString, errs, score)
    }
}
