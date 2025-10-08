package com.voiceexpense.ai.parsing.hybrid

import android.util.Log
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import java.util.Locale

/**
 * Builds compact prompts that target only the specified low-confidence fields.
 *
 * The builder emits template-style prompts when one or two fields need help and
 * falls back to a minimal JSON request for larger sets. All prompts are clamped
 * to the 1000 character budget defined in the spec requirements.
 */
class FocusedPromptBuilder {

    private val tag = "FocusedPrompt"

    fun buildFocusedPrompt(
        input: String,
        heuristicDraft: HeuristicDraft,
        targetFields: Set<FieldKey>,
        context: ParsingContext = ParsingContext()
    ): String {
        if (targetFields.isEmpty()) {
            return buildString {
                appendLine(FOCUSED_SYSTEM)
                append("Input: ")
                append(input)
            }
        }

        val orderedFields = targetFields
            .sortedBy { FIELD_ORDER.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }

        val prompt = if (orderedFields.size <= 2) {
            buildTemplatePrompt(orderedFields, input, heuristicDraft, context)
        } else {
            buildMultiFieldPrompt(orderedFields.toSet(), input, heuristicDraft, context)
        }

        val clamped = prompt.take(MAX_PROMPT_LENGTH)
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(
                tag,
                "built prompt len=${clamped.length} fields=${orderedFields.joinToString()}"
            )
        }
        return clamped
    }

    private fun buildTemplatePrompt(
        fields: List<FieldKey>,
        input: String,
        heuristicDraft: HeuristicDraft,
        context: ParsingContext
    ): String {
        return buildString {
            appendLine(FOCUSED_SYSTEM)
            appendLine()
            appendLine("Input: $input")
            appendLine()
            fields.forEach { field ->
                appendLine("Field: ${fieldLabel(field)} (key \"${jsonKey(field)}\")")
                appendLine("Heuristic: ${formatHeuristic(field, heuristicDraft)}")
                val options = formatOptions(field, context)
                if (options.isNotBlank()) {
                    appendLine("Options: $options")
                }
                appendLine("Instruction: ${instructionFor(field)}")
                appendLine()
            }
            appendGuidelines()
        }
    }

    private fun buildMultiFieldPrompt(
        fields: Set<FieldKey>,
        input: String,
        heuristicDraft: HeuristicDraft,
        context: ParsingContext
    ): String {
        val keys = fields.joinToString { "\"${jsonKey(it)}\"" }
        val heuristics = fields.joinToString(separator = "\n") { field ->
            "- ${jsonKey(field)}: ${formatHeuristic(field, heuristicDraft)}"
        }
        val options = buildSharedOptions(fields, context)

        return buildString {
            appendLine(FOCUSED_SYSTEM)
            appendLine()
            appendLine("Input: $input")
            appendLine()
            appendLine("Return a JSON object with only these keys: $keys")
            appendLine("Heuristic summary:")
            appendLine(heuristics)
            if (options.isNotBlank()) {
                appendLine("Options: $options")
            }
            appendGuidelines()
        }
    }

    private fun formatHeuristic(field: FieldKey, draft: HeuristicDraft): String {
        val valueDescription = when (field) {
            FieldKey.MERCHANT -> draft.merchant?.quoteOrMissing()
            FieldKey.DESCRIPTION -> draft.description?.quoteOrMissing()
            FieldKey.EXPENSE_CATEGORY -> draft.expenseCategory?.quoteOrMissing()
            FieldKey.INCOME_CATEGORY -> draft.incomeCategory?.quoteOrMissing()
            FieldKey.TAGS -> draft.tags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "[", postfix = "]") { it.quoteValue() }
            FieldKey.NOTE -> draft.note?.quoteOrMissing()
            else -> null
        } ?: "missing"

        val confidence = draft.confidence(field)
        return buildString {
            append(valueDescription)
            append(" (confidence ")
            append(String.format(Locale.US, "%.2f", confidence))
            append(')')
        }
    }

    private fun formatOptions(field: FieldKey, context: ParsingContext): String {
        val options = when (field) {
            FieldKey.MERCHANT -> context.recentMerchants
            FieldKey.DESCRIPTION -> emptyList()
            FieldKey.EXPENSE_CATEGORY -> context.allowedExpenseCategories.ifEmpty { context.recentCategories }
            FieldKey.INCOME_CATEGORY -> context.allowedIncomeCategories
            FieldKey.TAGS -> context.allowedTags
            FieldKey.NOTE -> emptyList()
            else -> emptyList()
        }.take(MAX_OPTIONS)

        return if (options.isEmpty()) "" else options.joinToString()
    }

    private fun buildSharedOptions(fields: Set<FieldKey>, context: ParsingContext): String {
        val parts = mutableListOf<String>()
        if (fields.any { it == FieldKey.MERCHANT } && context.recentMerchants.isNotEmpty()) {
            parts += "recentMerchants=${context.recentMerchants.take(MAX_OPTIONS).joinToString()}"
        }
        if (fields.any { it == FieldKey.EXPENSE_CATEGORY } && context.allowedExpenseCategories.isNotEmpty()) {
            parts += "expenseCategories=${context.allowedExpenseCategories.take(MAX_OPTIONS).joinToString()}"
        }
        if (fields.any { it == FieldKey.INCOME_CATEGORY } && context.allowedIncomeCategories.isNotEmpty()) {
            parts += "incomeCategories=${context.allowedIncomeCategories.take(MAX_OPTIONS).joinToString()}"
        }
        if (fields.any { it == FieldKey.TAGS } && context.allowedTags.isNotEmpty()) {
            parts += "tags=${context.allowedTags.take(MAX_OPTIONS).joinToString()}"
        }
        return parts.joinToString("; ")
    }

    private fun instructionFor(field: FieldKey): String = when (field) {
            FieldKey.MERCHANT -> "Return the merchant name exactly as a user would expect to see it (e.g., 'CVS', 'Trader Joe's')."
            FieldKey.DESCRIPTION -> "Provide a 1-3 word noun phrase describing the purchase (examples: 'Prescription', 'Birthday card', 'Lunch'). Avoid verbs."
            FieldKey.EXPENSE_CATEGORY -> "Choose the best matching expense category."
            FieldKey.INCOME_CATEGORY -> "Choose the best matching income category."
            FieldKey.TAGS -> "Return an array of tags chosen from the allowed list. Only include 'Splitwise' when the input mentions multiple amounts or splitting."
            FieldKey.NOTE -> "Return a brief note only when the input explicitly provides one, otherwise null."
            else -> "" // Should not be requested here.
    }

    private fun fieldLabel(field: FieldKey): String = when (field) {
        FieldKey.MERCHANT -> "Merchant"
        FieldKey.DESCRIPTION -> "Description"
        FieldKey.EXPENSE_CATEGORY -> "Expense category"
        FieldKey.INCOME_CATEGORY -> "Income category"
        FieldKey.TAGS -> "Tags"
        FieldKey.NOTE -> "Note"
        else -> field.name.lowercase(Locale.US)
    }

    private fun jsonKey(field: FieldKey): String = when (field) {
        FieldKey.MERCHANT -> "merchant"
        FieldKey.DESCRIPTION -> "description"
        FieldKey.EXPENSE_CATEGORY -> "expenseCategory"
        FieldKey.INCOME_CATEGORY -> "incomeCategory"
        FieldKey.TAGS -> "tags"
        FieldKey.NOTE -> "note"
        else -> field.name.lowercase(Locale.US)
    }

    private fun String.quoteOrMissing(): String = this.takeIf { it.isNotBlank() }?.quoteValue() ?: "missing"

    private fun String.quoteValue(): String = buildString {
        append('"')
        append(this@quoteValue)
        append('"')
    }

    private fun StringBuilder.appendGuidelines() {
        appendLine("Respond with compact JSON containing only the listed keys.")
        appendLine("Guidelines:")
        appendLine("- Do not wrap the JSON in markdown or code fences.")
        appendLine("- Description must be a short noun phrase (no verbs or sentences).")
        appendLine("- Tags must be a JSON array; include only allowed tags. Use 'Splitwise' only when the user mentioned splitting or multiple amounts.")
        appendLine("- Prefer provided options when selecting categories or merchants.")
        append("If a value cannot be improved, return null or omit the field rather than guessing.")
    }

    companion object {
        private const val MAX_PROMPT_LENGTH = 1000
        private const val MAX_OPTIONS = 6
        private const val FOCUSED_SYSTEM = "Refine only the requested transaction fields and keep JSON minimal."

        private val FIELD_ORDER = listOf(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.INCOME_CATEGORY,
            FieldKey.TAGS,
            FieldKey.NOTE
        )
    }
}
