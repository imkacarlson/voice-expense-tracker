package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import org.junit.Test

class FieldSelectionStrategyTest {

    @Test
    fun returns_empty_when_all_confident() {
        val draft = HeuristicDraft(
            merchant = "Starbucks",
            description = "Coffee run",
            expenseCategory = "Dining",
            incomeCategory = null,
            tags = listOf("personal"),
            confidences = mapOf(
                FieldKey.MERCHANT to 0.8f,
                FieldKey.DESCRIPTION to 0.85f,
                FieldKey.EXPENSE_CATEGORY to 0.7f,
                FieldKey.TAGS to 0.9f
            )
        )

        val fields = FieldSelectionStrategy.selectFieldsForRefinement(draft)

        assertThat(fields).isEmpty()
    }

    @Test
    fun includes_fields_below_threshold_or_missing() {
        val draft = HeuristicDraft(
            merchant = null,
            description = "",
            expenseCategory = "Dining",
            incomeCategory = "",
            tags = emptyList(),
            confidences = mapOf(
                FieldKey.MERCHANT to 0.2f,
                FieldKey.DESCRIPTION to 0.4f,
                FieldKey.EXPENSE_CATEGORY to 0.9f,
                FieldKey.INCOME_CATEGORY to 0.1f,
                FieldKey.TAGS to 0.05f
            )
        )

        val fields = FieldSelectionStrategy.selectFieldsForRefinement(draft)

        assertThat(fields).containsExactlyElementsIn(
            listOf(
                FieldKey.MERCHANT,
                FieldKey.DESCRIPTION,
                FieldKey.EXPENSE_CATEGORY,
                FieldKey.INCOME_CATEGORY,
                FieldKey.TAGS,
                FieldKey.ACCOUNT
            )
        )
    }

    @Test
    fun limits_to_maximum_refinable_fields_prioritizing_missing_merchant_and_description() {
        val draft = HeuristicDraft(
            merchant = null,
            description = null,
            expenseCategory = null,
            incomeCategory = null,
            tags = emptyList(),
            confidences = FieldSelectionStrategy.AI_REFINABLE_FIELDS.associateWith { 0f }
        )

        val fields = FieldSelectionStrategy.selectFieldsForRefinement(draft)

        assertThat(fields).hasSize(6)
        assertThat(fields[0]).isEqualTo(FieldKey.MERCHANT)
        assertThat(fields[1]).isEqualTo(FieldKey.DESCRIPTION)
        assertThat(fields).contains(FieldKey.ACCOUNT)
    }

    @Test
    fun account_is_selected_after_tags_when_both_need_refinement() {
        val draft = HeuristicDraft(
            merchant = "",
            description = "",
            type = "Expense",
            expenseCategory = "",
            tags = emptyList(),
            account = null,
            confidences = mapOf(
                FieldKey.MERCHANT to 0f,
                FieldKey.DESCRIPTION to 0f,
                FieldKey.EXPENSE_CATEGORY to 0f,
                FieldKey.TAGS to 0f,
                FieldKey.ACCOUNT to 0f
            )
        )

        val fields = FieldSelectionStrategy.selectFieldsForRefinement(draft)

        assertThat(fields).containsExactly(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.TAGS,
            FieldKey.ACCOUNT
        ).inOrder()
    }

    @Test
    fun when_expense_type_skips_income_category() {
        val draft = HeuristicDraft(
            type = "Expense",
            expenseCategory = null,
            incomeCategory = null,
            confidences = mapOf(
                FieldKey.EXPENSE_CATEGORY to 0.1f,
                FieldKey.INCOME_CATEGORY to 0.1f
            )
        )

        val fields = FieldSelectionStrategy.selectFieldsForRefinement(draft)

        assertThat(fields).contains(FieldKey.EXPENSE_CATEGORY)
        assertThat(fields).doesNotContain(FieldKey.INCOME_CATEGORY)
        assertThat(fields).contains(FieldKey.ACCOUNT)
    }

    @Test
    fun when_income_type_skips_expense_category() {
        val draft = HeuristicDraft(
            type = "Income",
            expenseCategory = null,
            incomeCategory = null,
            confidences = mapOf(
                FieldKey.EXPENSE_CATEGORY to 0.1f,
                FieldKey.INCOME_CATEGORY to 0.1f
            )
        )

        val fields = FieldSelectionStrategy.selectFieldsForRefinement(draft)

        assertThat(fields).contains(FieldKey.INCOME_CATEGORY)
        assertThat(fields).doesNotContain(FieldKey.EXPENSE_CATEGORY)
        assertThat(fields).contains(FieldKey.ACCOUNT)
    }

    @Test
    fun respects_custom_thresholds() {
        val draft = HeuristicDraft(
            merchant = "Coffee Shop",
            description = "",
            expenseCategory = "Dining",
            confidences = mapOf(
                FieldKey.MERCHANT to 0.55f,
                FieldKey.DESCRIPTION to 0.4f
            )
        )

        val thresholds = FieldConfidenceThresholds(
            mandatoryThresholds = mapOf(
                FieldKey.MERCHANT to 0.7f,
                FieldKey.DESCRIPTION to 0.6f
            )
        )

        val fields = FieldSelectionStrategy.selectFieldsForRefinement(draft, thresholds)

        assertThat(fields).containsExactly(FieldKey.MERCHANT, FieldKey.DESCRIPTION).inOrder()
    }
}
