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

    /**
     * Builds a focused prompt for a SINGLE field refinement.
     * The orchestrator calls this once per field that needs AI refinement.
     */
    fun buildFocusedPrompt(
        input: String,
        heuristicDraft: HeuristicDraft,
        targetFields: Set<FieldKey>,
        context: ParsingContext = ParsingContext()
    ): String {
        // In practice, targetFields always contains exactly one field since the orchestrator
        // refines fields one at a time. This assertion ensures we catch any misuse.
        require(targetFields.size <= 1) {
            "FocusedPromptBuilder only supports single-field refinement. Got ${targetFields.size} fields: ${targetFields.joinToString()}"
        }

        if (targetFields.isEmpty()) {
            return buildString {
                appendLine(FOCUSED_SYSTEM)
                append("Input: ")
                append(input)
            }
        }

        val field = targetFields.single()
        val prompt = buildSingleFieldPrompt(field, input, heuristicDraft, context)

        val clamped = prompt.take(MAX_PROMPT_LENGTH)
        Log.d(
            tag,
            "built prompt len=${clamped.length} field=${field.name}"
        )
        return clamped
    }

    private fun buildSingleFieldPrompt(
        field: FieldKey,
        input: String,
        heuristicDraft: HeuristicDraft,
        context: ParsingContext
    ): String {
        return buildString {
            appendLine(FOCUSED_SYSTEM)
            appendLine()
            appendLine("Input: $input")
            appendLine()
            appendLine("Field: ${fieldLabel(field)} (key \"${jsonKey(field)}\")")
            appendLine("Heuristic: ${formatHeuristic(field, heuristicDraft)}")
            val options = formatOptions(field, heuristicDraft, context, input)
            if (options.isNotBlank()) {
                appendLine("Allowed values: $options")
            }
            appendLine("Instruction: ${instructionFor(field, heuristicDraft)}")
            appendLine()
            appendGuideline(field)
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

    private fun instructionFor(field: FieldKey, draft: HeuristicDraft = HeuristicDraft()): String = when (field) {
            FieldKey.MERCHANT -> "Return the merchant name exactly as a user would expect to see it (e.g., \"Starbucks\", \"Target\"). If the input mentions payment methods (e.g., payment apps like Splitwise, Venmo, PayPal, Zelle, etc; or payment cards), identify the actual merchant or service being paid for, NOT the payment method. Examples: 'my roommate put into Splitwise that they reloaded our E-ZPass' → \"E-ZPass\" (NOT \"Splitwise\"); 'my card was charged for the appointment' → null (provider unknown). Return null if the merchant is genuinely unknown."
            FieldKey.DESCRIPTION -> buildString {
                append("Provide a concise noun phrase describing what was actually purchased or the service received (e.g., \"Coffee and pastry\", \"Household items\", \"Utility bill\"). Preserve key numbers or modifiers from the input; do not mention payment methods or account names. Avoid verbs. Do not use generic placeholders—describe the specific goods or service mentioned. Do not repeat the merchant name in the description.")
                // If merchant is already known from earlier refinement, make it explicit
                val merchant = draft.merchant
                val merchantConfidence = draft.confidence(FieldKey.MERCHANT)
                if (!merchant.isNullOrBlank() && merchantConfidence >= HEURISTIC_INCLUSION_THRESHOLD) {
                    append(" The merchant is '$merchant' - do not repeat this name in the description.")
                }
            }
            FieldKey.EXPENSE_CATEGORY -> """Choose the best matching expense category based on what was purchased or the service received:
- 'Eating Out': restaurant meals, takeout, coffee shops, fast food, snacks, drinks
- 'Transportation': vehicle gas/gasoline/fuel for cars, parking, tolls, transit fares, rideshares, vehicle expenses
- 'Groceries': supermarket shopping, food purchases at grocery stores
- 'Home': household supplies, cleaning products, organization items, furniture, home improvement, utilities (gas/electric/water/internet bills - these are home utility bills, not vehicle fuel)
- 'Personal': subscriptions (streaming services, news, software, online services), personal care, entertainment, memberships
- 'Health/medical': doctor visits, therapy appointments, prescriptions, medical equipment, vision care, dental
Return null if none of these categories apply to the transaction."""
            FieldKey.INCOME_CATEGORY -> "Choose the best matching income category."
            FieldKey.ACCOUNT -> "Return the account or card name from the allowed list. Match phonetic variations, case differences, and minor spelling differences common in voice-to-text (e.g., 'built' → 'Bilt', 'siti' → 'Citi', 'chase sapphire' → 'Chase Sapphire Preferred'). Do NOT infer or guess account names from generic references like 'her card' or 'my card'. Only match if the actual account name is explicitly stated in the input. Return null if no account name is mentioned."
            FieldKey.TAGS -> "Return tags from the allowed list that match the transaction. Only include tags if explicitly mentioned or clearly implied in the input. Use fuzzy/phonetic matching (e.g., 'autopaid' → 'Auto-Paid', 'splitwise' → 'Splitwise'). For 'Auto-Paid': only if the input explicitly mentions automatic payment, autopay, or auto-charge. For 'Subscription': only if the input explicitly mentions it's a subscription or recurring payment. Return empty array if no tags clearly apply."
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

    private fun StringBuilder.appendGuideline(field: FieldKey) {
        appendLine("Respond with compact JSON containing only the listed keys.")
        guidelineFor(field)?.let { rule ->
            appendLine("Guideline for ${jsonKey(field)}: $rule")
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

        private val SPLIT_HINT_REGEX = Regex("""(?i)(splitwise|split|splitting|my share|owe|i owe|owed)""")

        private fun guidelineFor(field: FieldKey): String? = when (field) {
            FieldKey.MERCHANT -> "Return only the merchant or vendor name—no verbs, adjectives, or trailing phrases. Avoid payment methods."
            FieldKey.DESCRIPTION -> "Provide a concise noun phrase that preserves meaningful numbers or modifiers from the input and avoids payment method names. NEVER include the merchant name in the description."
            FieldKey.EXPENSE_CATEGORY -> "Choose exactly one expense category: 'Eating Out' for food/drinks, 'Home' for household items/utility bills (gas/electric/water/internet bills), 'Personal' for subscriptions/services, 'Transportation' for vehicle fuel/parking/transit, 'Groceries' for supermarket food, 'Health/medical' for medical services. Return null if none apply."
            FieldKey.INCOME_CATEGORY -> "Choose exactly one income category from the allowed list; return null if none apply."
            FieldKey.ACCOUNT -> "Return the account/card name from the allowed list, matching phonetic variations and spelling differences from voice-to-text (e.g., 'built' → 'Bilt', 'siti' → 'Citi'). Return null if none apply."
            FieldKey.TAGS -> "Return an array of distinct tags chosen only from the allowed list. Only include tags that are explicitly mentioned or clearly implied in the input. If no allowed tag clearly applies, return an empty array."
            else -> null
        }
    }
}
