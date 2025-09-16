package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionPrompts
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.LinkedHashSet

/**
 * Builds structured prompts for on-device LLM inference (MediaPipe), combining:
 * - A strict system instruction and schema constraints
 * - Targeted few-shot examples selected by heuristics
 * - Lightweight context hints (recent merchants/accounts)
 */
class PromptBuilder {
    /** Build a composed prompt text suitable for ML Kit's rewrite API. */
    @Suppress("UNUSED_PARAMETER")
    fun build(
        input: String,
        context: ParsingContext = ParsingContext(),
        heuristicDraft: HeuristicDraft? = null
    ): String {
        val system = buildSystemInstruction()
        val shots = selectExamples(input, heuristicDraft)
        val contextBlock = buildContextBlock(context)
        val knownFieldsBlock = buildKnownFieldsBlock(heuristicDraft)

        return buildString {
            appendLine(system)
            if (contextBlock.isNotBlank()) {
                appendLine()
                appendLine("Context:")
                appendLine(contextBlock)
            }
            if (knownFieldsBlock.isNotBlank()) {
                appendLine()
                appendLine("Known fields (keep these values, fill remaining as nulls):")
                appendLine(knownFieldsBlock)
            }
            appendLine()
            appendLine("Examples:")
            appendLine(formatExamples(shots))
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
        val summary = mutableListOf<String>()
        if (context.recentMerchants.isNotEmpty()) {
            summary += "recentMerchants=${context.recentMerchants.takeCapped().joinToString()}"
        }
        if (context.knownAccounts.isNotEmpty()) {
            summary += "knownAccounts=${context.knownAccounts.takeCapped().joinToString()}"
        }
        if (context.recentCategories.isNotEmpty()) {
            summary += "recentCategories=${context.recentCategories.takeCapped().joinToString()}"
        }
        if (summary.isNotEmpty()) {
            appendLine(summary.joinToString("; "))
        }

        val allowed = mutableListOf<String>()
        if (context.allowedExpenseCategories.isNotEmpty()) {
            allowed += "expenseCategories=${context.allowedExpenseCategories.takeCapped().joinToString()}"
        }
        if (context.allowedIncomeCategories.isNotEmpty()) {
            allowed += "incomeCategories=${context.allowedIncomeCategories.takeCapped().joinToString()}"
        }
        if (context.allowedTags.isNotEmpty()) {
            allowed += "tags=${context.allowedTags.takeCapped().joinToString()}"
        }
        val accountOptions = (context.allowedAccounts.takeIf { it.isNotEmpty() } ?: context.knownAccounts)
        if (accountOptions.isNotEmpty()) {
            allowed += "accounts=${accountOptions.takeCapped().joinToString()}"
        }
        if (allowed.isNotEmpty()) {
            appendLine("allowedOptions: ${allowed.joinToString("; ")}")
        }
    }.trim()

    private fun buildKnownFieldsBlock(heuristicDraft: HeuristicDraft?): String {
        val draft = heuristicDraft ?: return ""
        val parts = mutableListOf<String>()
        fun <T> appendField(name: String, value: T?, formatter: (T) -> String = { it.toString() }) {
            if (value == null) {
                parts += "\"$name\":null"
            } else {
                parts += "\"$name\":${formatter(value)}"
            }
        }

        appendField("amountUsd", draft.amountUsd) { formatDecimal(it) }
        appendField("merchant", draft.merchant) { quote(it) }
        appendField("description", draft.description) { quote(it) }
        appendField("type", draft.type) { quote(it) }
        appendField("expenseCategory", draft.expenseCategory) { quote(it) }
        appendField("incomeCategory", draft.incomeCategory) { quote(it) }
        appendField("tags", draft.tags) { list ->
            list.joinToString(prefix = "[", postfix = "]") { tag -> quote(tag) }
        }
        appendField("userLocalDate", draft.userLocalDate) { quote(it.format(DateTimeFormatter.ISO_DATE)) }
        appendField("account", draft.account) { quote(it) }
        appendField("splitOverallChargedUsd", draft.splitOverallChargedUsd) { formatDecimal(it) }
        appendField("note", draft.note) { quote(it) }

        return parts.joinToString(prefix = "{", postfix = "}")
    }

    private fun selectExamples(
        input: String,
        heuristicDraft: HeuristicDraft?
    ): List<FewShotExampleRepository.ExamplePair> {
        val lower = input.lowercase()
        val picks = LinkedHashSet<FewShotExampleRepository.ExamplePair>()

        FewShotExampleRepository.defaultExpense()?.let { picks += it }

        val isIncome = (heuristicDraft?.type == "Income") || lower.contains("paycheck") || lower.contains("deposit") || lower.contains("income")
        val isTransfer = (heuristicDraft?.type == "Transfer") || lower.contains("transfer") || lower.contains("moved")
        val isSplit = (heuristicDraft?.splitOverallChargedUsd != null) ||
            (heuristicDraft?.tags?.any { it.contains("split") } == true) ||
            lower.contains("splitwise") || lower.contains("my share") || lower.contains("overall charged")

        if (isSplit) {
            FewShotExampleRepository.splitExpense()?.let { picks += it }
        } else {
            FewShotExampleRepository.subscriptionExpense()?.let { picks += it }
        }

        if (isIncome) {
            FewShotExampleRepository.income()?.let { picks += it }
        }

        if (isTransfer) {
            FewShotExampleRepository.transfer()?.let { picks += it }
        }

        return picks.take(3).toList()
    }

    private fun formatExamples(examples: List<FewShotExampleRepository.ExamplePair>): String =
        examples.joinToString(separator = "\n") { example ->
            "- Input: ${example.input}\n  Output: ${example.outputJson}"
        }

    private fun quote(value: String): String = buildString {
        append('"')
        append(value.replace("\"", "\\\""))
        append('"')
    }

    private fun formatDecimal(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
