package com.voiceexpense.ai.parsing.heuristic

import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.StructuredOutputValidator
import com.voiceexpense.ai.parsing.TagNormalizer
import java.util.Locale
/** Converts heuristic drafts into ParsedResult instances for downstream use. */
fun HeuristicDraft.toParsedResult(context: ParsingContext): ParsedResult {
    val type = this.type ?: "Expense"
    val expenseCategory = if (type == "Expense") this.expenseCategory ?: "Uncategorized" else null
    val incomeCategory = if (type == "Income") this.incomeCategory ?: "Salary" else null
    val amount = if (type == "Transfer") null else this.amountUsd

    // Normalize tags to match allowed tags with proper capitalization
    val normalizedTags = TagNormalizer.normalize(this.tags, context.allowedTags)

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

private fun capitalizeFirst(text: String?): String? {
    if (text.isNullOrEmpty()) return text
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}
