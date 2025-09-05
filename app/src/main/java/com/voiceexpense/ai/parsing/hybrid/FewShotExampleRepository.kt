package com.voiceexpense.ai.parsing.hybrid

/**
 * Curated few-shot utterance examples to guide on-device ML Kit
 * toward consistent, structured transaction outputs.
 *
 * Notes:
 * - Keep examples short, natural, and varied.
 * - Cover Expense, Income, Transfer, and Split scenarios.
 * - Include edge cases (ambiguous merchants, partial info, tags).
 * - USD amounts only; some amounts in words to improve robustness.
 */
object FewShotExampleRepository {
    // Expense examples
    val EXPENSE: List<String> = listOf(
        "spent 4.75 at starbucks for latte",
        "uber 18.20 ride home",
        "groceries at trader joes 56.90, tag food",
        "amazon 23.49 usb cable",
        "target 32 socks and tees",
        "chipotle 12 burrito bowl",
        "dinner la fiesta thirty dollars",
        "costco 101.05 household",
        "apple 0.99 icloud",
        "parking meter 3 downtown"
    )

    // Income examples
    val INCOME: List<String> = listOf(
        "paycheck 2200, tag july",
        "venmo from alice 35 for lunch payback",
        "cashback 12.34 credit card",
        "refund 45 from amazon",
        "interest 1.23 savings"
    )

    // Transfer examples
    val TRANSFER: List<String> = listOf(
        "transfer 100 from checking to savings",
        "moved 250 to brokerage from checking",
        "transfer 40 from cash to wallet",
        "transfer fifty from savings to checking"
    )

    // Split-bill scenarios (include overall charge vs my share)
    val SPLIT: List<String> = listOf(
        "dinner at la fiesta 60, my share 20, overall charged 60",
        "uber 30 to airport, my share fifteen, overall thirty",
        "hotel 300 conference, split three ways, my share 100",
        "pizza night 48, my part 16, total charged 48"
    )

    // Edge cases and robustness boosters
    val EDGE_CASES: List<String> = listOf(
        "coffee three dollars",
        "unknown 0.99",
        "bought lunch 12",
        "gym 29 monthly",
        "best buy 0, return processed",
        "gift 50 to john, tag birthday"
    )

    /** All examples merged (stable order). */
    fun all(): List<String> = buildList {
        addAll(EXPENSE)
        addAll(SPLIT)
        addAll(INCOME)
        addAll(TRANSFER)
        addAll(EDGE_CASES)
    }
}

