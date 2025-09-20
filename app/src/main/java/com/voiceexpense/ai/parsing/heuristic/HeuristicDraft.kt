package com.voiceexpense.ai.parsing.heuristic

import java.math.BigDecimal
import java.time.LocalDate

/** Keys tracked by heuristics to determine coverage and confidence. */
@Suppress("unused")
enum class FieldKey {
    AMOUNT_USD,
    MERCHANT,
    DESCRIPTION,
    TYPE,
    EXPENSE_CATEGORY,
    INCOME_CATEGORY,
    TAGS,
    USER_LOCAL_DATE,
    ACCOUNT,
    SPLIT_OVERALL_CHARGED_USD,
    NOTE
}

/** Per-field confidence thresholds for deciding whether AI help is required. */
data class FieldConfidenceThresholds(
    val mandatoryThresholds: Map<FieldKey, Float> = DEFAULT_MANDATORY_THRESHOLDS,
    val defaultThreshold: Float = 0f
) {
    val mandatoryFields: Set<FieldKey> = mandatoryThresholds.keys

    fun thresholdFor(field: FieldKey): Float = mandatoryThresholds[field] ?: defaultThreshold

    companion object {
        private val DEFAULT_MANDATORY_THRESHOLDS: Map<FieldKey, Float> = mapOf(
            FieldKey.AMOUNT_USD to 0.8f,
            FieldKey.USER_LOCAL_DATE to 0.75f,
            FieldKey.TYPE to 0.6f,
            FieldKey.MERCHANT to 0.6f,
            FieldKey.ACCOUNT to 0.7f
        )

        val DEFAULT = FieldConfidenceThresholds()
    }
}

/** Result produced by heuristic parsing prior to LLM invocation. */
data class HeuristicDraft(
    val amountUsd: BigDecimal? = null,
    val merchant: String? = null,
    val description: String? = null,
    val type: String? = null,
    val expenseCategory: String? = null,
    val incomeCategory: String? = null,
    val tags: List<String> = emptyList(),
    val userLocalDate: LocalDate? = null,
    val account: String? = null,
    val splitOverallChargedUsd: BigDecimal? = null,
    val note: String? = null,
    val confidences: Map<FieldKey, Float> = emptyMap()
) {
    val coverageScore: Float by lazy {
        val thresholds = FieldConfidenceThresholds.DEFAULT
        val scores = thresholds.mandatoryFields.map { confidence(it) }
        if (scores.isEmpty()) 0f else scores.sum() / scores.size
    }

    fun confidence(field: FieldKey): Float = confidences[field] ?: 0f

    fun requiresAi(thresholds: FieldConfidenceThresholds = FieldConfidenceThresholds.DEFAULT): Boolean {
        val hasMerchantLikeField = confidence(FieldKey.MERCHANT) >= thresholds.thresholdFor(FieldKey.MERCHANT) ||
            confidence(FieldKey.DESCRIPTION) >= thresholds.thresholdFor(FieldKey.MERCHANT)
        if (!hasMerchantLikeField) return true

        return thresholds.mandatoryFields.any { field ->
            val conf = confidence(field)
            conf <= 0f || conf < thresholds.thresholdFor(field)
        }
    }
}
