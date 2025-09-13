package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionPrompts

/**
 * Builds structured prompts for on-device LLM inference (MediaPipe), combining:
 * - A strict system instruction and schema constraints
 * - Targeted few-shot examples selected by heuristics
 * - Lightweight context hints (recent merchants/accounts)
 */
class PromptBuilder(
    private val examples: FewShotExampleRepository = FewShotExampleRepository
) {
    /** Build a composed prompt text suitable for ML Kit's rewrite API. */
    fun build(input: String, context: ParsingContext = ParsingContext()): String {
        val system = buildSystemInstruction()
        val shots = selectExamples(input)
        val contextBlock = buildContextBlock(context)

        val examplesBlock = shots.joinToString("\n") { "- $it" }
        return buildString {
            appendLine(system)
            if (contextBlock.isNotBlank()) {
                appendLine()
                appendLine("Context:")
                appendLine(contextBlock)
            }
            appendLine()
            appendLine("Examples:")
            appendLine(examplesBlock)
            appendLine()
            append("Input: ")
            append(input)
        }
    }

    private fun buildSystemInstruction(): String {
        // Start from existing strict instruction and add concise constraints.
        return buildString {
            appendLine(TransactionPrompts.SYSTEM_INSTRUCTION.trim())
            appendLine("Additional constraints:")
            appendLine("- Output ONLY JSON (no prose, no code fences)")
            appendLine("- If splitOverallChargedUsd present, amountUsd <= splitOverallChargedUsd")
            appendLine("- If type = Transfer: amountUsd is the moved amount; expenseCategory/incomeCategory = null")
            appendLine("- Default merchant 'Unknown' if not inferred")
            appendLine("- Keep tags as lowercase single words")
            appendLine("- For fields with allowed options, choose ONLY from the provided lists. If no match, leave null.")
        }
    }

    private fun buildContextBlock(context: ParsingContext): String = buildString {
        val cap = 8 // cap each list to keep prompt compact
        fun <T> List<T>.takeCapped() = this.take(cap)
        if (context.recentMerchants.isNotEmpty()) {
            appendLine("recentMerchants: ${context.recentMerchants.takeCapped().joinToString()}")
        }
        if (context.knownAccounts.isNotEmpty()) {
            appendLine("knownAccounts: ${context.knownAccounts.takeCapped().joinToString()}")
        }
        if (context.recentCategories.isNotEmpty()) {
            appendLine("recentCategories: ${context.recentCategories.takeCapped().joinToString()}")
        }
        if (context.allowedExpenseCategories.isNotEmpty()) {
            appendLine("allowedExpenseCategories: ${context.allowedExpenseCategories.takeCapped().joinToString()}")
        }
        if (context.allowedIncomeCategories.isNotEmpty()) {
            appendLine("allowedIncomeCategories: ${context.allowedIncomeCategories.takeCapped().joinToString()}")
        }
        if (context.allowedTags.isNotEmpty()) {
            appendLine("allowedTags: ${context.allowedTags.takeCapped().joinToString()}")
        }
        if (context.allowedAccounts.isNotEmpty()) {
            appendLine("allowedAccounts: ${context.allowedAccounts.takeCapped().joinToString()}")
        }
    }.trim()

    private fun selectExamples(input: String): List<String> {
        val lower = input.lowercase()
        val chosen = mutableListOf<String>()

        val isTransfer = lower.contains("transfer") || lower.contains("moved")
        val looksSplit = lower.contains("split") || lower.contains("my share") || lower.contains("overall")
        val isIncome = lower.contains("paycheck") || lower.contains("refund") || lower.contains("interest") || lower.contains("from ") && lower.contains("venmo")

        when {
            isTransfer -> {
                chosen += examples.TRANSFER
                if (looksSplit) chosen += examples.SPLIT
            }
            isIncome -> {
                chosen += examples.INCOME
            }
            looksSplit -> {
                chosen += examples.SPLIT
                chosen += examples.EXPENSE
            }
            else -> {
                chosen += examples.EXPENSE
            }
        }

        // Add a small subset of edge cases to improve robustness
        chosen += examples.EDGE_CASES.take(3)

        // Cap size to keep prompt compact (fewer shots -> faster responses)
        return chosen.take(6)
    }
}
