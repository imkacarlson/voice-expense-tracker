package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class StagedParsingResultTest {

    @Test
    fun helper_methods_report_refinements_correctly() {
        val draft = HeuristicDraft(
            amountUsd = BigDecimal("12.34"),
            merchant = "Unknown",
            userLocalDate = LocalDate.of(2025, 10, 1)
        )
        val parsed = ParsedResult(
            amountUsd = draft.amountUsd,
            merchant = "Blue Bottle",
            description = "Coffee",
            type = "Expense",
            expenseCategory = "Dining",
            incomeCategory = null,
            tags = listOf("coffee"),
            userLocalDate = draft.userLocalDate!!,
            account = null,
            splitOverallChargedUsd = null,
            confidence = 0.9f
        )
        val result = StagedParsingResult(
            heuristicDraft = draft,
            refinedFields = mapOf(FieldKey.MERCHANT to "Blue Bottle"),
            mergedResult = parsed,
            fieldsRefined = setOf(FieldKey.MERCHANT),
            targetFields = setOf(FieldKey.MERCHANT),
            refinementErrors = emptyList(),
            stage1DurationMs = 20,
            stage2DurationMs = 200
        )

        assertThat(result.wasFieldRefined(FieldKey.MERCHANT)).isTrue()
        assertThat(result.wasFieldRefined(FieldKey.DESCRIPTION)).isFalse()
        assertThat(result.refinementValue(FieldKey.MERCHANT)).isEqualTo("Blue Bottle")
        assertThat(result.refinementValue(FieldKey.DESCRIPTION)).isNull()
        assertThat(result.hasErrors).isFalse()
        assertThat(result.totalDurationMs).isEqualTo(220)
    }

    @Test
    fun hasErrors_is_true_when_errors_present() {
        val result = StagedParsingResult(
            heuristicDraft = HeuristicDraft(),
            refinedFields = emptyMap(),
            mergedResult = ParsedResult(
                amountUsd = null,
                merchant = "Unknown",
                description = null,
                type = "Expense",
                expenseCategory = null,
                incomeCategory = null,
                tags = emptyList(),
                userLocalDate = LocalDate.now(),
                account = null,
                splitOverallChargedUsd = null,
                confidence = 0.5f
            ),
            fieldsRefined = emptySet(),
            targetFields = emptySet(),
            refinementErrors = listOf("timeout"),
            stage1DurationMs = 10,
            stage2DurationMs = 0
        )

        assertThat(result.hasErrors).isTrue()
    }
}
