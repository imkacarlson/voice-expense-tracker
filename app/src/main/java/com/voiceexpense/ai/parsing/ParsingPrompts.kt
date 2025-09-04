package com.voiceexpense.ai.parsing

object ParsingPrompts {
    const val SYSTEM_PROMPT = """
You are a parser that outputs strict JSON for expense entries.
Return only JSON with fields: amountUsd, merchant, description, type, expenseCategory, incomeCategory,
tags, userLocalDate (YYYY-MM-DD), account, splitOverallChargedUsd, note, confidence.
Type is one of Expense, Income, Transfer. USD only, no currency symbols.
""" 

    // Example utterances to guide structured output (expand with steering doc set)
    val EXAMPLES: List<String> = listOf(
        "I spent $23 at Starbucks for coffee",
        "Dinner at La Fiesta thirty dollars, my share twenty, overall charged thirty",
        "Paycheck two thousand, tag July",
        "Transfer 100 from Checking to Savings"
    )

    fun expenseTemplate(merchant: String, amount: String, description: String? = null): String =
        buildString {
            append("I spent ")
            append(amount)
            append(" at ")
            append(merchant)
            if (!description.isNullOrBlank()) {
                append(" for ")
                append(description)
            }
        }

    fun incomeTemplate(source: String, amount: String, tag: String? = null): String =
        buildString {
            append("Paycheck ")
            append(amount)
            if (!tag.isNullOrBlank()) {
                append(", tag ")
                append(tag)
            }
        }

    fun transferTemplate(amount: String, from: String, to: String): String =
        "Transfer $amount from $from to $to"
}
