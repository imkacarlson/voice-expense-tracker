package com.voiceexpense.ai.parsing

import java.util.Locale

internal object TagNormalizer {
    fun normalize(raw: List<String>, allowed: List<String>): List<String> {
        if (raw.isEmpty()) return emptyList()

        val canonical = linkedMapOf<String, String>().apply {
            allowed.forEach { option ->
                val trimmed = option.trim()
                if (trimmed.isNotEmpty()) {
                    put(trimmed.lowercase(Locale.US), trimmed)
                }
            }
        }

        return raw.mapNotNull { tag ->
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val normalizedKey = trimmed.lowercase(Locale.US)
            canonical[normalizedKey] ?: toTitleCase(trimmed)
        }.distinct()
    }

    private fun toTitleCase(value: String): String {
        if (value.isEmpty()) return value
        val lower = value.lowercase(Locale.US)
        val builder = StringBuilder(value.length)
        var capitalizeNext = true
        lower.forEachIndexed { index, ch ->
            when {
                ch.isLetter() -> {
                    val letter = if (capitalizeNext) ch.titlecase(Locale.US) else ch.toString()
                    builder.append(letter)
                    capitalizeNext = false
                }
                ch.isDigit() -> {
                    builder.append(value[index])
                    capitalizeNext = false
                }
                else -> {
                    builder.append(value[index])
                    capitalizeNext = ch == ' ' || ch == '-' || ch == '_' || ch == '/'
                }
            }
        }
        return builder.toString()
    }
}
