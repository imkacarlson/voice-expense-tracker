package com.voiceexpense.ai.parsing.hybrid

import android.util.Log
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import java.util.Locale

/**
 * Determines which fields should be refined by Stage 2 AI calls.
 *
 * The strategy looks at heuristic confidence scores, missing values, and applies
 * a hard cap to keep focused prompts short. Amount/date/account fields are excluded
 * by design because they are owned by the heuristic pipeline.
 */
object FieldSelectionStrategy {

    private const val TAG = "FieldSelection"

    private const val MAX_REFINABLE_FIELDS = 6

    private val FIELD_ORDER: List<FieldKey> = listOf(
        FieldKey.MERCHANT,
        FieldKey.DESCRIPTION,
        FieldKey.EXPENSE_CATEGORY,
        FieldKey.INCOME_CATEGORY,
        FieldKey.TAGS,
        FieldKey.ACCOUNT
    )
    private val FIELD_ORDER_INDEX: Map<FieldKey, Int> = FIELD_ORDER.withIndex().associate { it.value to it.index }

    val AI_REFINABLE_FIELDS: Set<FieldKey> = setOf(
        FieldKey.MERCHANT,
        FieldKey.DESCRIPTION,
        FieldKey.EXPENSE_CATEGORY,
        FieldKey.INCOME_CATEGORY,
        FieldKey.TAGS,
        FieldKey.ACCOUNT
    )

    /**
     * Returns the ordered list of fields that should be refined by the AI stage.
     */
    fun selectFieldsForRefinement(
        heuristicDraft: HeuristicDraft,
        thresholds: FieldConfidenceThresholds = FieldConfidenceThresholds.DEFAULT
    ): List<FieldKey> {
        val allowedFields = filterFieldsForType(heuristicDraft)
        val candidates: List<FieldCandidate> = allowedFields
            .mapNotNull { field -> buildCandidate(field, heuristicDraft, thresholds) }
            .sortedWith(candidateComparator)

        val selected = candidates
            .map(FieldCandidate::field)
            .take(MAX_REFINABLE_FIELDS)

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            val selectedNames = selected.joinToString(separator = ",") { it.name }
            val missingNames = candidates
                .filter { it.missingValue }
                .joinToString(separator = ",") { it.field.name }
            Log.d(TAG, "Selected fields=$selectedNames missingValues=$missingNames")
        }
        return selected
    }

    private fun buildCandidate(
        field: FieldKey,
        draft: HeuristicDraft,
        thresholds: FieldConfidenceThresholds
    ): FieldCandidate? {
        val confidence = draft.confidence(field)
        val threshold = thresholds.thresholdFor(field)

        val belowThreshold = confidence <= 0f || confidence < threshold
        val missingValue = isMissing(field, draft)

        if (!belowThreshold && !missingValue) {
            return null
        }

        return FieldCandidate(
            field = field,
            confidence = confidence,
            missingValue = missingValue
        )
    }

    private fun filterFieldsForType(draft: HeuristicDraft): Set<FieldKey> {
        val type = draft.type?.lowercase(Locale.US)
        return when (type) {
            "income" -> AI_REFINABLE_FIELDS - FieldKey.EXPENSE_CATEGORY
            "transfer" -> AI_REFINABLE_FIELDS - setOf(FieldKey.EXPENSE_CATEGORY, FieldKey.INCOME_CATEGORY)
            else -> AI_REFINABLE_FIELDS - if (type == "expense") setOf(FieldKey.INCOME_CATEGORY) else emptySet()
        }
    }

    private fun isMissing(field: FieldKey, draft: HeuristicDraft): Boolean {
        val type = draft.type?.lowercase(Locale.US)
        return when (field) {
            FieldKey.MERCHANT -> draft.merchant.isNullOrBlank()
            FieldKey.DESCRIPTION -> draft.description.isNullOrBlank()
            FieldKey.EXPENSE_CATEGORY -> when (type) {
                "income", "transfer" -> false
                else -> draft.expenseCategory.isNullOrBlank()
            }
            FieldKey.INCOME_CATEGORY -> when (type) {
                "income" -> draft.incomeCategory.isNullOrBlank()
                else -> false
            }
            FieldKey.ACCOUNT -> draft.account.isNullOrBlank()
            FieldKey.TAGS -> draft.tags.isEmpty()
            else -> false
        }
    }

    private val candidateComparator = Comparator<FieldCandidate> { a, b ->
        val priorityCompare = a.priority().compareTo(b.priority())
        if (priorityCompare != 0) {
            return@Comparator priorityCompare
        }

        val orderCompare = FIELD_ORDER_INDEX
            .getOrElse(a.field) { Int.MAX_VALUE }
            .compareTo(FIELD_ORDER_INDEX.getOrElse(b.field) { Int.MAX_VALUE })
        if (orderCompare != 0) {
            return@Comparator orderCompare
        }

        a.confidence.compareTo(b.confidence)
    }

    private data class FieldCandidate(
        val field: FieldKey,
        val confidence: Float,
        val missingValue: Boolean
    ) {
        fun priority(): Int = when {
            field in CRITICAL_FIELDS && missingValue -> 0
            missingValue -> 1
            else -> 2
        }
    }

    private val CRITICAL_FIELDS: Set<FieldKey> = setOf(
        FieldKey.MERCHANT,
        FieldKey.DESCRIPTION
    )
}
