package com.voiceexpense.ai.parsing.heuristic

import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.StructuredOutputValidator

/** Converts heuristic drafts into ParsedResult instances for downstream use. */
fun HeuristicDraft.toParsedResult(context: ParsingContext): ParsedResult {
    val type = this.type ?: "Expense"
    val expenseCategory = if (type == "Expense") this.expenseCategory ?: "Uncategorized" else null
    val incomeCategory = if (type == "Income") this.incomeCategory ?: "Salary" else null
    val amount = if (type == "Transfer") null else this.amountUsd
    val parsed = ParsedResult(
        amountUsd = amount,
        merchant = this.merchant ?: "Unknown",
        description = this.description,
        type = type,
        expenseCategory = expenseCategory,
        incomeCategory = incomeCategory,
        tags = this.tags,
        userLocalDate = this.userLocalDate ?: context.defaultDate,
        account = this.account,
        splitOverallChargedUsd = this.splitOverallChargedUsd,
        note = this.note,
        confidence = this.coverageScore.coerceIn(0f, 1f)
    )
    return StructuredOutputValidator.sanitizeAmounts(parsed)
}
