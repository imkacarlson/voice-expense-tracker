package com.voiceexpense.ai.parsing

import java.util.Locale

internal object TagNormalizer {
    fun normalize(raw: List<String>, allowed: List<String>): List<String> {
        if (raw.isEmpty()) return emptyList()

        val canonical = buildCanonicalMap(allowed)

        if (canonical.isNotEmpty()) {
            val normalized = linkedSetOf<String>()
            raw.forEach { tag ->
                val trimmed = tag.trim()
                if (trimmed.isEmpty()) return@forEach
                val normalizedKey = trimmed.lowercase(Locale.US)
                val collapsedKey = collapse(normalizedKey)
                val match = canonical[normalizedKey] ?: canonical[collapsedKey]
                if (match != null) {
                    normalized += match
                }
            }
            return normalized.toList()
        }

        return raw.mapNotNull { tag ->
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            toTitleCase(trimmed)
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

    private fun buildCanonicalMap(allowed: List<String>): LinkedHashMap<String, String> {
        val canonical = linkedMapOf<String, String>()
        allowed.forEach { option ->
            val trimmed = option.trim()
            if (trimmed.isEmpty()) return@forEach
            val lower = trimmed.lowercase(Locale.US)
            canonical.putIfAbsent(lower, trimmed)
            val collapsed = collapse(lower)
            if (collapsed != lower) {
                canonical.putIfAbsent(collapsed, trimmed)
            }
        }
        return canonical
    }

    private fun collapse(value: String): String {
        if (value.isEmpty()) return value
        val builder = StringBuilder(value.length)
        value.forEach { ch ->
            if (ch.isLetterOrDigit()) {
                builder.append(ch)
            }
        }
        return builder.toString()
    }
}
