package com.voiceexpense.ai.parsing

import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal

data class ValidationResult(val valid: Boolean, val error: String? = null)

object StructuredOutputValidator {
    fun validate(result: ParsedResult): ValidationResult {
        val typeOk = result.type in setOf("Expense", "Income", "Transfer")
        if (!typeOk) return ValidationResult(false, "invalid type")

        // USD-only: Reject currency symbols in note/description (basic heuristic)
        if ((result.description ?: "").contains('$')) return ValidationResult(false, "currency symbol not allowed")

        // Split constraint: amount <= overall when both present
        val a: BigDecimal? = result.amountUsd
        val overall: BigDecimal? = result.splitOverallChargedUsd
        if (a != null && overall != null && a > overall) return ValidationResult(false, "share exceeds overall")

        return ValidationResult(true)
    }

    /** Minimal JSON schema validation for GenAI structured output.
     * Intentionally permissive: only enforces that when present, `tags` is a JSON array of strings.
     */
    fun validateTransactionJson(json: String): ValidationResult {
        return try {
            val obj = JSONObject(json)

            // tags when present should be a JSON array of strings (no coercion)
            if (obj.has("tags") && !obj.isNull("tags")) {
                val arr: JSONArray = obj.optJSONArray("tags")
                    ?: return ValidationResult(false, "tags must be array")
                for (i in 0 until arr.length()) {
                    val v: Any? = arr.opt(i)
                    if (v !is String) return ValidationResult(false, "tags must be array of strings")
                }
            }

            ValidationResult(true)
        } catch (t: Throwable) {
            ValidationResult(false, "invalid json: ${t.message}")
        }
    }

    /** Sanitize and normalize amounts and fields. */
    fun sanitizeAmounts(parsed: ParsedResult): ParsedResult {
        fun normalize(v: BigDecimal?): BigDecimal? = v?.let {
            val nonNeg = if (it < BigDecimal.ZERO) it.negate() else it
            nonNeg.setScale(2, BigDecimal.ROUND_HALF_UP)
        }

        return parsed.copy(
            amountUsd = normalize(parsed.amountUsd),
            splitOverallChargedUsd = normalize(parsed.splitOverallChargedUsd),
            // merchant is non-nullable in ParsedResult; keep trimmed value
            merchant = parsed.merchant.trim(),
            description = parsed.description?.trim().takeUnless { it.isNullOrEmpty() },
            note = parsed.note?.trim().takeUnless { it.isNullOrEmpty() }
        )
    }
}
