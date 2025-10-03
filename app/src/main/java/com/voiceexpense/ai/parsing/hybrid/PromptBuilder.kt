package com.voiceexpense.ai.parsing.hybrid

import android.util.Log
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionPrompts
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.LinkedHashSet
import java.util.Locale

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
        val hintsBlock = buildHintsBlock(heuristicDraft)

        val primary = composePrompt(system, shots, contextBlock, hintsBlock, input, includeContext = true)
        if (primary.length <= MAX_PROMPT_CHARS) return primary

        val reducedShots = shots.take(2)
        val shorter = composePrompt(system, reducedShots, contextBlock, hintsBlock, input, includeContext = true)
        if (shorter.length <= MAX_PROMPT_CHARS) {
            Log.i(TAG, "PromptBuilder trimmed examples to stay within token budget (len=${shorter.length})")
            return shorter
        }

        val single = composePrompt(system, reducedShots.take(1), contextBlock, hintsBlock, input, includeContext = true)
        if (single.length <= MAX_PROMPT_CHARS) {
            Log.i(TAG, "PromptBuilder reduced to single example (len=${single.length})")
            return single
        }

        val noContext = composePrompt(system, reducedShots.take(1), "", hintsBlock, input, includeContext = false)
        if (noContext.length <= MAX_PROMPT_CHARS) {
            Log.i(TAG, "PromptBuilder dropped context block to meet length constraints (len=${noContext.length})")
            return noContext
        }

        val fallback = composePrompt(system, emptyList(), "", hintsBlock, input, includeContext = false)
        if (fallback.length <= MAX_PROMPT_CHARS) {
            Log.w(TAG, "PromptBuilder using minimal prompt (len=${fallback.length}) due to size constraints")
            return fallback
        }

        val noHints = composePrompt(system, emptyList(), "", "", input, includeContext = false)
        if (noHints.length <= MAX_PROMPT_CHARS) {
            Log.w(TAG, "PromptBuilder dropped heuristic hints to satisfy token budget (len=${noHints.length})")
            return noHints
        }

        val ultraMinimal = composePrompt(MINIMAL_SYSTEM_INSTRUCTION, emptyList(), "", "", input, includeContext = false)
        if (ultraMinimal.length > MAX_PROMPT_CHARS) {
            Log.w(TAG, "PromptBuilder ultra minimal prompt still long; truncating to ${MAX_PROMPT_CHARS} chars")
            return ultraMinimal.take(MAX_PROMPT_CHARS)
        }
        Log.w(TAG, "PromptBuilder using ultra minimal prompt (len=${ultraMinimal.length})")
        return ultraMinimal
    }

    private fun composePrompt(
        system: String,
        shots: List<FewShotExampleRepository.ExamplePair>,
        contextBlock: String,
        hintsBlock: String,
        input: String,
        includeContext: Boolean
    ): String {
        return buildString {
            appendLine(system)
            if (includeContext && contextBlock.isNotBlank()) {
                appendLine()
                appendLine("Context:")
                appendLine(contextBlock)
            }
            if (hintsBlock.isNotBlank()) {
                appendLine()
                appendLine("Heuristic hints (confidence 0..1; adjust if incorrect):")
                appendLine(hintsBlock)
            }
            if (shots.isNotEmpty()) {
                appendLine()
                appendLine("Examples:")
                appendLine(formatExamples(shots))
            }
            appendLine()
            append("Input: ")
            append(input)
        }
    }

    private fun buildSystemInstruction(): String = TransactionPrompts.SYSTEM_INSTRUCTION.trim()

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

    private fun buildHintsBlock(heuristicDraft: HeuristicDraft?): String {
        val draft = heuristicDraft ?: return ""
        val hints = mutableListOf<String>()

        fun appendHint(name: String, rawValue: String?, confidence: Float) {
            if (rawValue == null) return
            val builder = StringBuilder()
            builder.append('"')
            builder.append(name)
            builder.append("\":{")
            builder.append("\"value\":")
            builder.append(rawValue)
            if (confidence > 0f) {
                builder.append(",\"confidence\":")
                builder.append(String.format(Locale.US, "%.2f", confidence))
            }
            builder.append('}')
            hints += builder.toString()
        }
        appendHint("amountUsd", draft.amountUsd?.let { formatDecimal(it) }, draft.confidence(FieldKey.AMOUNT_USD))
        appendHint("merchant", draft.merchant?.let { quote(it) }, draft.confidence(FieldKey.MERCHANT))
        appendHint("type", draft.type?.let { quote(it) }, draft.confidence(FieldKey.TYPE))
        appendHint("expenseCategory", draft.expenseCategory?.let { quote(it) }, draft.confidence(FieldKey.EXPENSE_CATEGORY))
        appendHint("incomeCategory", draft.incomeCategory?.let { quote(it) }, draft.confidence(FieldKey.INCOME_CATEGORY))
        appendHint(
            "tags",
            draft.tags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "[", postfix = "]") { quote(it) },
            draft.confidence(FieldKey.TAGS)
        )
        appendHint(
            "userLocalDate",
            draft.userLocalDate?.let { quote(it.format(DateTimeFormatter.ISO_DATE)) },
            draft.confidence(FieldKey.USER_LOCAL_DATE)
        )
        appendHint("account", draft.account?.let { quote(it) }, draft.confidence(FieldKey.ACCOUNT))
        appendHint(
            "splitOverallChargedUsd",
            draft.splitOverallChargedUsd?.let { formatDecimal(it) },
            draft.confidence(FieldKey.SPLIT_OVERALL_CHARGED_USD)
        )
        appendHint("note", draft.note?.let { quote(it) }, draft.confidence(FieldKey.NOTE))

        return if (hints.isEmpty()) "" else hints.joinToString(prefix = "{", postfix = "}")
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

    companion object {
        private const val TAG = "AI.Trace"
        private const val MAX_PROMPT_CHARS = 1700
        private val MINIMAL_SYSTEM_INSTRUCTION = "Return ONLY valid JSON."
    }
}
