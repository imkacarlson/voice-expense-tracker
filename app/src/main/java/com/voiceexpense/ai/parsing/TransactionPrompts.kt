package com.voiceexpense.ai.parsing

/**
 * Structured prompt templates specifically for transaction parsing via on-device GenAI.
 * Provides a strict system instruction and representative examples to nudge
 * the model toward consistent JSON output.
 */
object TransactionPrompts {
    const val SYSTEM_INSTRUCTION = """
You convert short, informal spoken texts into STRICT JSON describing a financial transaction.
Return ONLY JSON. Do not include comments or extra text. Fields:
- amountUsd: number | null
- merchant: string (default "Unknown")
- description: string | null
- type: "Expense" | "Income" | "Transfer"
- expenseCategory: string | null
- incomeCategory: string | null
- tags: string[]
- userLocalDate: string (YYYY-MM-DD)
- account: string | null
- splitOverallChargedUsd: number | null
- note: string | null
- confidence: number (0..1)
Rules:
- USD only numbers (no currency symbols)
- Do NOT emit a field named 'amount'; use 'amountUsd' only
- If type = Transfer, amountUsd applies to the amount moved and expenseCategory/incomeCategory must be null
- Keep tags concise lowercase single words
"""

    // Compact example utterances that help guide the model.
    val EXAMPLE_UTTERANCES: List<String> = listOf(
        "bought coffee at starbucks 4.75",
        "paycheck 2200, tag july",
        "transfer 100 from checking to savings"
    )

    fun expense(merchant: String, amount: Number, what: String? = null): String =
        buildString {
            append("spent ")
            append(amount)
            append(" at ")
            append(merchant)
            if (!what.isNullOrBlank()) {
                append(" for ")
                append(what)
            }
        }

    fun income(source: String, amount: Number, tag: String? = null): String =
        buildString {
            append("paycheck ")
            append(amount)
            if (!tag.isNullOrBlank()) {
                append(", tag ")
                append(tag)
            }
        }

    fun transfer(amount: Number, from: String, to: String): String =
        "transfer $amount from $from to $to"
}
