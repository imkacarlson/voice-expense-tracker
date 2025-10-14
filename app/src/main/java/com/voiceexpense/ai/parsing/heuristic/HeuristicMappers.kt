package com.voiceexpense.ai.parsing.heuristic

import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.StructuredOutputValidator
import java.util.Locale

/** Converts heuristic drafts into ParsedResult instances for downstream use. */
fun HeuristicDraft.toParsedResult(context: ParsingContext): ParsedResult {
    val type = this.type ?: "Expense"
    val expenseCategory = if (type == "Expense") this.expenseCategory ?: "Uncategorized" else null
    val incomeCategory = if (type == "Income") this.incomeCategory ?: "Salary" else null
    val amount = if (type == "Transfer") null else this.amountUsd

    // Normalize tags to match allowed tags with proper capitalization
    val normalizedTags = if (context.allowedTags.isNotEmpty()) {
        normalizeTags(this.tags, context.allowedTags)
    } else {
        this.tags
    }

    val merchant = capitalizeFirst(this.merchant?.trim()).takeUnless { it.isNullOrEmpty() } ?: "Unknown"
    val description = this.description?.trim()?.let(::capitalizeFirst)

    val parsed = ParsedResult(
        amountUsd = amount,
        merchant = merchant,
        description = description,
        type = type,
        expenseCategory = expenseCategory,
        incomeCategory = incomeCategory,
        tags = normalizedTags,
        userLocalDate = this.userLocalDate ?: context.defaultDate,
        account = this.account,
        splitOverallChargedUsd = this.splitOverallChargedUsd,
        note = this.note,
        confidence = this.coverageScore.coerceIn(0f, 1f)
    )
    return StructuredOutputValidator.sanitizeAmounts(parsed)
}

private fun normalizeTags(tags: List<String>, allowed: List<String>): List<String> {
    if (allowed.isEmpty()) return tags
    val normalizedAllowed = allowed.associateBy { it.trim().lowercase(Locale.US) }
    return tags.mapNotNull { tag ->
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        normalizedAllowed[trimmed.lowercase(Locale.US)]
    }.distinct()
}

private fun capitalizeFirst(text: String?): String? {
    if (text.isNullOrEmpty()) return text
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}
