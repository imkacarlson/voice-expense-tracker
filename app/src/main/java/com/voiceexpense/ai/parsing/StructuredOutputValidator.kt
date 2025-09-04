package com.voiceexpense.ai.parsing

import org.json.JSONObject
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

    /** Minimal JSON schema validation for GenAI structured output. */
    fun validateTransactionJson(json: String): ValidationResult {
        return try {
            val obj = JSONObject(json)
            val type = obj.optString("type", "")
            if (type !in setOf("Expense", "Income", "Transfer")) return ValidationResult(false, "invalid type")

            // amountUsd must be absent/null for Transfer, else non-negative number
            if (type != "Transfer") {
                if (obj.has("amountUsd") && !obj.isNull("amountUsd")) {
                    val amt = obj.optDouble("amountUsd", -1.0)
                    if (amt < 0.0) return ValidationResult(false, "amountUsd must be non-negative")
                } else {
                    return ValidationResult(false, "amountUsd required for $type")
                }
            }

            // tags when present should be array
            if (obj.has("tags") && !obj.isNull("tags") && !obj.get("tags").toString().startsWith("[")) {
                return ValidationResult(false, "tags must be array")
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
            merchant = parsed.merchant?.trim().takeUnless { it.isNullOrEmpty() },
            description = parsed.description?.trim().takeUnless { it.isNullOrEmpty() },
            note = parsed.note?.trim().takeUnless { it.isNullOrEmpty() }
        )
    }
}
