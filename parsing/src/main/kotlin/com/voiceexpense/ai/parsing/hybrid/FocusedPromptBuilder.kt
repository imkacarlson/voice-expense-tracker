package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.logging.Log
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
        Log.d(
            tag,
            "built prompt len=${clamped.length} fields=${orderedFields.joinToString()}"
        )
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
                val options = formatOptions(field, heuristicDraft, context, input)
                if (options.isNotBlank()) {
                    appendLine("Allowed values: $options")
                }
                appendLine("Instruction: ${instructionFor(field)}")
                appendLine()
            }
            appendGuidelines(fields.toSet())
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
        val options = buildSharedOptions(fields, heuristicDraft, context, input)

        return buildString {
            appendLine(FOCUSED_SYSTEM)
            appendLine()
            appendLine("Input: $input")
            appendLine()
            appendLine("Return a JSON object with only these keys: $keys")
            appendLine("Heuristic summary:")
            appendLine(heuristics)
            if (options.isNotBlank()) {
                appendLine("Allowed values: $options")
            }
            appendGuidelines(fields)
        }
    }

    private fun formatHeuristic(field: FieldKey, draft: HeuristicDraft): String {
        val confidence = draft.confidence(field)

        // Only include heuristic value if confidence is above threshold
        // Low-confidence values can mislead the AI model
        val valueDescription = if (confidence < HEURISTIC_INCLUSION_THRESHOLD) {
            "missing"
        } else {
            when (field) {
                FieldKey.MERCHANT -> draft.merchant?.quoteOrMissing()
                FieldKey.DESCRIPTION -> draft.description?.quoteOrMissing()
                FieldKey.EXPENSE_CATEGORY -> draft.expenseCategory?.quoteOrMissing()
                FieldKey.INCOME_CATEGORY -> draft.incomeCategory?.quoteOrMissing()
                FieldKey.ACCOUNT -> draft.account?.quoteOrMissing()
                FieldKey.TAGS -> draft.tags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "[", postfix = "]") { it.quoteValue() }
                else -> null
            } ?: "missing"
        }

        return buildString {
            append(valueDescription)
            append(" (confidence ")
            append(String.format(Locale.US, "%.2f", confidence))
            append(')')
        }
    }

    private fun formatOptions(
        field: FieldKey,
        draft: HeuristicDraft,
        context: ParsingContext,
        input: String
    ): String {
        val raw = when (field) {
            FieldKey.MERCHANT -> context.recentMerchants
            FieldKey.DESCRIPTION -> emptyList()
            FieldKey.EXPENSE_CATEGORY -> context.allowedExpenseCategories.ifEmpty { context.recentCategories }
            FieldKey.INCOME_CATEGORY -> context.allowedIncomeCategories
            FieldKey.ACCOUNT -> context.allowedAccounts.ifEmpty { context.knownAccounts }
            FieldKey.TAGS -> context.allowedTags
            else -> emptyList()
        }

        val filtered = if (field == FieldKey.TAGS) {
            filterTagOptions(raw, draft, input)
        } else {
            raw
        }

        val options = if (field == FieldKey.ACCOUNT) filtered else filtered.take(MAX_OPTIONS)
        return if (options.isEmpty()) "" else options.joinToString()
    }

    private fun buildSharedOptions(
        fields: Set<FieldKey>,
        draft: HeuristicDraft,
        context: ParsingContext,
        input: String
    ): String {
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
        if (fields.any { it == FieldKey.ACCOUNT }) {
            val accounts = context.allowedAccounts.ifEmpty { context.knownAccounts }
            if (accounts.isNotEmpty()) {
                parts += "accounts=${accounts.joinToString()}"
            }
        }
        if (fields.any { it == FieldKey.TAGS } && context.allowedTags.isNotEmpty()) {
            val tags = filterTagOptions(context.allowedTags, draft, input).take(MAX_OPTIONS)
            if (tags.isNotEmpty()) {
                parts += "tags=${tags.joinToString()}"
            }
        }
        return parts.joinToString("; ")
    }

    private fun instructionFor(field: FieldKey): String = when (field) {
            FieldKey.MERCHANT -> "Return the merchant name exactly as a user would expect to see it (e.g., \"Starbucks\", \"Target\"). If the input mentions payment methods (e.g., payment apps like Splitwise, Venmo, PayPal, Zelle, etc; or payment cards), identify the actual merchant or service being paid for, NOT the payment method. Examples: 'my roommate put into Splitwise that they reloaded our E-ZPass' → \"E-ZPass\" (NOT \"Splitwise\"); 'my card was charged for the appointment' → null (provider unknown). Return null if the merchant is genuinely unknown."
            FieldKey.DESCRIPTION -> "Provide a concise noun phrase describing what was actually purchased or the service received (e.g., \"Coffee and pastry\", \"Household items\", \"Utility bill\"). Preserve key numbers or modifiers from the input; do not mention payment methods or account names. Avoid verbs. Do not use generic placeholders—describe the specific goods or service mentioned."
            FieldKey.EXPENSE_CATEGORY -> """Choose the best matching expense category based on what was purchased or the service received:
- 'Eating Out': restaurant meals, takeout, coffee shops, fast food, snacks, drinks
- 'Transportation': vehicle gas/gasoline/fuel for cars, parking, tolls, transit fares, rideshares, vehicle expenses
- 'Groceries': supermarket shopping, food purchases at grocery stores
- 'Home': household supplies, cleaning products, organization items, furniture, home improvement, utilities (gas/electric/water/internet bills - these are home utility bills, not vehicle fuel)
- 'Personal': subscriptions (streaming services, news, software, online services), personal care, entertainment, memberships
- 'Health/medical': doctor visits, therapy appointments, prescriptions, medical equipment, vision care, dental
Return null if none of these categories apply to the transaction."""
            FieldKey.INCOME_CATEGORY -> "Choose the best matching income category."
            FieldKey.ACCOUNT -> "Return the account or card name from the allowed list. Match phonetic variations, case differences, and minor spelling differences common in voice-to-text (e.g., 'built' → 'Bilt', 'siti' → 'Citi', 'chase sapphire' → 'Chase Sapphire Preferred'). Return null if no account name is mentioned."
            FieldKey.TAGS -> "Return tags from the allowed list that match the transaction. Use both explicit mentions (with fuzzy/phonetic matching like 'autopaid' → 'Auto-Paid') and semantic inference (e.g., if 'paid automatically' or 'autopay' is mentioned, select 'Auto-Paid'; if it's a recurring service or subscription, consider 'Subscription')."
            else -> "" // Should not be requested here.
    }

    private fun fieldLabel(field: FieldKey): String = when (field) {
        FieldKey.MERCHANT -> "Merchant"
        FieldKey.DESCRIPTION -> "Description"
        FieldKey.EXPENSE_CATEGORY -> "Expense category"
        FieldKey.INCOME_CATEGORY -> "Income category"
        FieldKey.ACCOUNT -> "Account"
        FieldKey.TAGS -> "Tags"
        else -> field.name.lowercase(Locale.US)
    }

    private fun jsonKey(field: FieldKey): String = when (field) {
        FieldKey.MERCHANT -> "merchant"
        FieldKey.DESCRIPTION -> "description"
        FieldKey.EXPENSE_CATEGORY -> "expenseCategory"
        FieldKey.INCOME_CATEGORY -> "incomeCategory"
        FieldKey.ACCOUNT -> "account"
        FieldKey.TAGS -> "tags"
        else -> field.name.lowercase(Locale.US)
    }

    private fun String.quoteOrMissing(): String = this.takeIf { it.isNotBlank() }?.quoteValue() ?: "missing"

    private fun String.quoteValue(): String = buildString {
        append('"')
        append(this@quoteValue)
        append('"')
    }

    private fun StringBuilder.appendGuidelines(fields: Set<FieldKey>) {
        appendLine("Respond with compact JSON containing only the listed keys.")
        fields.sortedBy { FIELD_ORDER.indexOf(it).takeIf { idx -> idx >= 0 } ?: Int.MAX_VALUE }.forEach { field ->
            guidelineFor(field)?.let { rule ->
                appendLine("Guideline for ${jsonKey(field)}: $rule")
            }
        }
    }

    private fun filterTagOptions(
        options: List<String>,
        draft: HeuristicDraft,
        input: String
    ): List<String> {
        if (options.isEmpty()) return options
        val allowSplitwise = draft.tags.any { it.equals("splitwise", ignoreCase = true) } ||
            SPLIT_HINT_REGEX.containsMatchIn(input.lowercase(Locale.US))
        return options.filterNot { tag ->
            tag.equals("Splitwise", ignoreCase = true) && !allowSplitwise
        }
    }

    companion object {
        private const val MAX_PROMPT_LENGTH = 1000
        private const val MAX_OPTIONS = 6
        private const val FOCUSED_SYSTEM = "Refine only the requested transaction fields and keep JSON minimal."

        /**
         * Minimum confidence threshold for including heuristic values in prompts.
         * Values below this threshold are shown as "missing" to avoid misleading the AI.
         */
        private const val HEURISTIC_INCLUSION_THRESHOLD = 0.3f

        private val FIELD_ORDER = listOf(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.INCOME_CATEGORY,
            FieldKey.TAGS,
            FieldKey.ACCOUNT
        )

        private val SPLIT_HINT_REGEX = Regex("""(?i)(splitwise|split|splitting|my share|owe|i owe|owed)""")

        private fun guidelineFor(field: FieldKey): String? = when (field) {
            FieldKey.MERCHANT -> "Return only the merchant or vendor name—no verbs, adjectives, or trailing phrases. Avoid payment methods."
            FieldKey.DESCRIPTION -> "Provide a concise noun phrase that preserves meaningful numbers or modifiers from the input and avoids payment method names."
            FieldKey.EXPENSE_CATEGORY -> "Choose exactly one expense category: 'Eating Out' for food/drinks, 'Home' for household items/utility bills (gas/electric/water/internet bills), 'Personal' for subscriptions/services, 'Transportation' for vehicle fuel/parking/transit, 'Groceries' for supermarket food, 'Health/medical' for medical services. Return null if none apply."
            FieldKey.INCOME_CATEGORY -> "Choose exactly one income category from the allowed list; return null if none apply."
            FieldKey.ACCOUNT -> "Return the account/card name from the allowed list, matching phonetic variations and spelling differences from voice-to-text (e.g., 'built' → 'Bilt', 'siti' → 'Citi'). Return null if none apply."
            FieldKey.TAGS -> "Return an array of distinct tags chosen only from the allowed list. Match both explicit mentions and semantic meanings. If no allowed tag applies, return an empty array."
            else -> null
        }
    }
}
