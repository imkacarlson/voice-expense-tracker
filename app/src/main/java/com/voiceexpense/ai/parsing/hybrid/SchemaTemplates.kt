package com.voiceexpense.ai.parsing.hybrid

/** Schema template provider used by prompt construction. */
object SchemaTemplates {
    enum class Kind { BASIC, SPLIT, TRANSFER }

    fun schema(kind: Kind = Kind.BASIC): String = when (kind) {
        Kind.BASIC -> BASIC
        Kind.SPLIT -> SPLIT
        Kind.TRANSFER -> TRANSFER
    }

    // Concise, model-friendly schema descriptions. Keep stable ordering.
    private val COMMON = """
Fields:
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
- Keep tags concise lowercase single words
""".trimIndent()

    private val BASIC = """
${COMMON}
If type = Expense or Income: amountUsd is the entry amount.
""".trimIndent()

    private val SPLIT = """
${COMMON}
Split constraint: if splitOverallChargedUsd present, amountUsd <= splitOverallChargedUsd.
""".trimIndent()

    private val TRANSFER = """
${COMMON}
If type = Transfer: amountUsd is the moved amount; expenseCategory = null; incomeCategory = null.
""".trimIndent()
}

