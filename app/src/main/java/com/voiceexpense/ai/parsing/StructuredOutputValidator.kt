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
        fun check(candidate: String): ValidationResult {
            return try {
                val reader = JsonReader.of(Buffer().writeUtf8(candidate)).apply { isLenient = true }
                if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                    return ValidationResult(false, "invalid json: not object")
                }
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (name == "tags") {
                        when (reader.peek()) {
                            JsonReader.Token.NULL -> reader.nextNull<Unit>()
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
                            else -> return ValidationResult(false, "tags must be array")
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

        // First attempt (lenient)
        val first = check(json)
        if (first.valid) return first

        // Second attempt: strip trailing commas before } or ] and retry
        val noTrailingCommas = json
            .replace(Regex(""",\s*]"""), "]")
            .replace(Regex(""",\s*}"""), "}")
        val second = check(noTrailingCommas)
        return if (second.valid) second else first
    }

    /**
     * ML Kit responses may include surrounding prose or markdown code fences.
     * Attempt to extract the first complete JSON object payload robustly.
     */
    fun normalizeMlKitJson(text: String): String {
        // 1) Remove any Markdown code fences and leading language labels
        var s = text
            .replace("\r", "")
            .replace("```json", "```")
            .replace("```JSON", "```")
            .replace("```", "")
            .trim()

        // Remove a leading language token like 'json' if present
        s = s.replace(Regex("^(?i)json\\s*"), "").trim()

        // 2) Extract the first top-level JSON object by brace matching
        val candidate = s
        var depth = 0
        var start = -1
        var inString = false
        var escape = false
        var lastTopLevelComma = -1
        for (i in candidate.indices) {
            val ch = candidate[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        // Found a balanced top-level object
                        return candidate.substring(start, i + 1)
                    }
                }
                ',' -> if (depth == 1) lastTopLevelComma = i
            }
        }

        // If we didn't find a balanced object but we did see the start of one,
        // attempt a best-effort salvage by truncating to the last complete
        // top-level field (last comma) and closing the object.
        if (start >= 0) {
            val end = if (lastTopLevelComma > start) lastTopLevelComma else candidate.lastIndex
            var trimmed = candidate.substring(start, (end + 1).coerceAtMost(candidate.length))
            // Remove a trailing comma and whitespace if present
            trimmed = trimmed.replace(Regex(",\s*$"), "")
            // Close the object
            return "$trimmed}"
        }

        // Fallback: return the cleaned string as-is
        return candidate
    }

    /** Validate a raw ML Kit text response, attempting recovery before validation. */
    fun validateMlKitTransactionJson(text: String): ValidationResult {
        val json = normalizeMlKitJson(text)
        val res = validateTransactionJson(json)
        return if (res.valid) res else ValidationResult(false, "mlkit: ${res.error}")
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
