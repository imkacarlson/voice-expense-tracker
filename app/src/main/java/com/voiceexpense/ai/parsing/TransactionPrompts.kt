package com.voiceexpense.ai.parsing

/**
 * Structured prompt templates specifically for transaction parsing via on-device GenAI.
 * Provides a strict system instruction and representative examples to nudge
 * the model toward consistent JSON output.
 */
object TransactionPrompts {
    const val SYSTEM_INSTRUCTION = """
You convert informal spoken expense descriptions into STRICT JSON.
Return JSON only, no prose or markdown. Schema fields:
- amountUsd: number | null
- merchant: string (default "Unknown")
- description: string | null
- type: "Expense" | "Income" | "Transfer"
- expenseCategory: string | null
- incomeCategory: string | null
- tags: string[] (lowercase single words)
- userLocalDate: string (YYYY-MM-DD)
- account: string | null
- splitOverallChargedUsd: number | null
- note: string | null
- confidence: number (0..1)
Rules:
- Numbers are USD (no $ symbol, commas allowed).
- If type = Transfer, expenseCategory and incomeCategory must be null.
- If splitOverallChargedUsd present, amountUsd ≤ splitOverallChargedUsd.
- Tags array must be lowercase words; omit if none.
- Heuristic hints may be provided; treat them as suggestions and correct them if the utterance disagrees.
- Match amountUsd to the spoken spend/share; never invent implausibly large values.
- When the utterance mentions a card or account, map it to the closest allowed account option (case-insensitive, tolerate small spelling differences).
- For expenses, choose an expenseCategory from allowed options that best fits the merchant/description.
- For description: keep concise (2-6 words); focus on WHAT was purchased or the PURPOSE (e.g., "Groceries", "Dinner", "Running shoes"), not the action of going/buying; omit if it would just repeat the merchant name.
"""

    data class ExamplePair(
        val id: String,
        val input: String,
        val outputJson: String,
        val tags: Set<PromptCategory>
    )

    enum class PromptCategory { EXPENSE, SPLIT, SUBSCRIPTION, INCOME, TRANSFER }

    val SAMPLE_MAPPINGS: List<ExamplePair> = listOf(
        ExamplePair(
            id = "groceries-simple",
            input = "I just went to Trader Joe's to get some groceries and I spent 30 dollars",
            outputJson = "{" +
                "\"amountUsd\":30," +
                "\"merchant\":\"Trader Joe's\"," +
                "\"description\":\"Groceries\"," +
                "\"type\":\"Expense\"," +
                "\"expenseCategory\":\"Groceries\"," +
                "\"incomeCategory\":null," +
                "\"tags\":[]," +
                "\"userLocalDate\":\"2025-10-07\"," +
                "\"account\":null," +
                "\"splitOverallChargedUsd\":null," +
                "\"note\":null," +
                "\"confidence\":0.85}",
            tags = setOf(PromptCategory.EXPENSE)
        ),
        ExamplePair(
            id = "splitwise-utilities",
            input = "On September 11th the gas bill was charged to my Vanguard Cash Plus account for 22.24 and after splitting with Emily I will only owe 11.12",
            outputJson = "{" +
                "\"amountUsd\":11.12," +
                "\"merchant\":\"Gas Bill\"," +
                "\"description\":null," +
                "\"type\":\"Expense\"," +
                "\"expenseCategory\":\"Utilities\"," +
                "\"incomeCategory\":null," +
                "\"tags\":[\"auto-paid\",\"splitwise\"]," +
                "\"userLocalDate\":\"2025-09-11\"," +
                "\"account\":\"Vanguard Cash Plus (Savings)\"," +
                "\"splitOverallChargedUsd\":22.24," +
                "\"note\":null," +
                "\"confidence\":0.92}",
            tags = setOf(PromptCategory.EXPENSE, PromptCategory.SPLIT)
        ),
        ExamplePair(
            id = "subscription-expense",
            input = "On September 10th my New York Times subscription payment was auto charged and it was 26.50 and it was charged to my Chase Sapphire Preferred Card",
            outputJson = "{" +
                "\"amountUsd\":26.5," +
                "\"merchant\":\"NY Times Subscription\"," +
                "\"description\":null," +
                "\"type\":\"Expense\"," +
                "\"expenseCategory\":\"Personal\"," +
                "\"incomeCategory\":null," +
                "\"tags\":[\"auto-paid\",\"subscription\"]," +
                "\"userLocalDate\":\"2025-09-10\"," +
                "\"account\":\"Chase Sapphire Preferred\"," +
                "\"splitOverallChargedUsd\":null," +
                "\"note\":null," +
                "\"confidence\":0.9}",
            tags = setOf(PromptCategory.EXPENSE, PromptCategory.SUBSCRIPTION)
        ),
        ExamplePair(
            id = "income-paycheck",
            input = "On September 12th I got my paycheck deposit into my Vanguard Cash Plus account and it came out to 3030.09",
            outputJson = "{" +
                "\"amountUsd\":3030.09," +
                "\"merchant\":\"Paycheck + incentive award\"," +
                "\"description\":null," +
                "\"type\":\"Income\"," +
                "\"expenseCategory\":null," +
                "\"incomeCategory\":\"Paycheck\"," +
                "\"tags\":[]," +
                "\"userLocalDate\":\"2025-09-12\"," +
                "\"account\":\"Vanguard Cash Plus (Savings)\"," +
                "\"splitOverallChargedUsd\":null," +
                "\"note\":null," +
                "\"confidence\":0.88}",
            tags = setOf(PromptCategory.INCOME)
        ),
        ExamplePair(
            id = "transfer-savings",
            input = "On September 9th I transferred 250 dollars from my checking account into Vanguard Cash Plus savings",
            outputJson = "{" +
                "\"amountUsd\":250.0," +
                "\"merchant\":\"Transfer\"," +
                "\"description\":\"Transfer from checking\"," +
                "\"type\":\"Transfer\"," +
                "\"expenseCategory\":null," +
                "\"incomeCategory\":null," +
                "\"tags\":[]," +
                "\"userLocalDate\":\"2025-09-09\"," +
                "\"account\":\"Checking Account\"," +
                "\"splitOverallChargedUsd\":null," +
                "\"note\":null," +
                "\"confidence\":0.85}",
            tags = setOf(PromptCategory.TRANSFER)
        )
    )
}
