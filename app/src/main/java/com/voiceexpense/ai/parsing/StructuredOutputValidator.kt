package com.voiceexpense.ai.parsing

import com.squareup.moshi.JsonReader
import okio.Buffer
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
     * Uses Moshi JsonReader to robustly inspect the `tags` field without relying on org.json behavior differences.
     * Rule: if present and non-null, `tags` must be an array of strings (no nulls, no numbers/objects).
     */
    fun validateTransactionJson(json: String): ValidationResult {
        return try {
            val reader = JsonReader.of(Buffer().writeUtf8(json))
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                return ValidationResult(false, "invalid json: not object")
            }
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == "tags") {
                    when (reader.peek()) {
                        JsonReader.Token.NULL -> {
                            reader.nextNull<Unit>() // null is allowed
                        }
                        JsonReader.Token.BEGIN_ARRAY -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                when (reader.peek()) {
                                    JsonReader.Token.NULL -> return ValidationResult(false, "tags must be array of strings")
                                    JsonReader.Token.STRING -> reader.nextString()
                                    else -> return ValidationResult(false, "tags must be array of strings")
                                }
                            }
                            reader.endArray()
                        }
                        else -> {
                            // Anything other than array/null is invalid for tags
                            return ValidationResult(false, "tags must be array")
                        }
                    }
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            ValidationResult(true)
        } catch (t: Throwable) {
            ValidationResult(false, "invalid json: ${t.message}")
        }
    }

    /** Sanitize and normalize amounts and fields. */
    fun sanitizeAmounts(parsed: ParsedResult): ParsedResult {
        fun normalize(v: BigDecimal?): BigDecimal? = v?.let {
            val nonNeg = if (it < BigDecimal.ZERO) it.negate() else it
            nonNeg.setScale(2, java.math.RoundingMode.HALF_UP)
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
