package com.voiceexpense.ai.parsing

import java.math.BigDecimal

class TransactionParser {
    // Placeholder: Replace with ML Kit GenAI Gemini Nano integration.
    suspend fun parse(text: String, context: ParsingContext = ParsingContext()): ParsedResult {
        // Extremely naive defaults to make the pipeline testable before model integration
        val isIncome = text.lowercase().contains("income") || text.lowercase().contains("paycheck")
        val isTransfer = text.lowercase().contains("transfer")
        val type = when {
            isTransfer -> "Transfer"
            isIncome -> "Income"
            else -> "Expense"
        }
        val amountRegex = Regex("(\\d+)(?:\\.(\\d{1,2}))?")
        val amount = amountRegex.find(text)?.value?.let { BigDecimal(it) }
        return ParsedResult(
            amountUsd = if (type == "Transfer") null else amount,
            merchant = if (type == "Expense") "Unknown" else "",
            description = null,
            type = type,
            expenseCategory = if (type == "Expense") "Uncategorized" else null,
            incomeCategory = if (type == "Income") "Salary" else null,
            tags = emptyList(),
            userLocalDate = context.defaultDate,
            account = null,
            splitOverallChargedUsd = null,
            note = null,
            confidence = 0.5f
        )
    }
}

