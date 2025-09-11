package com.voiceexpense.ai.parsing

import java.math.BigDecimal
import java.time.LocalDate

data class ParsedResult(
    val amountUsd: BigDecimal?,
    val merchant: String,
    val description: String?,
    val type: String, // "Expense" | "Income" | "Transfer"
    val expenseCategory: String?,
    val incomeCategory: String?,
    val tags: List<String> = emptyList(),
    val userLocalDate: LocalDate,
    val account: String?,
    val splitOverallChargedUsd: BigDecimal?,
    val note: String?,
    val confidence: Float
)

data class ParsingContext(
    val recentMerchants: List<String> = emptyList(),
    val recentCategories: List<String> = emptyList(),
    val knownAccounts: List<String> = emptyList(),
    val defaultDate: LocalDate = LocalDate.now(),
    // Allowed options configured by the user; models should choose only from these.
    val allowedExpenseCategories: List<String> = emptyList(),
    val allowedIncomeCategories: List<String> = emptyList(),
    val allowedTags: List<String> = emptyList(),
    val allowedAccounts: List<String> = emptyList()
)
