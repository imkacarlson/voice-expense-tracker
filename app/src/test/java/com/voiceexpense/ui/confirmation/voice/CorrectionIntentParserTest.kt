package com.voiceexpense.ui.confirmation.voice

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CorrectionIntentParserTest {
    private fun draft(type: TransactionType = TransactionType.Expense) = Transaction(
        userLocalDate = LocalDate.of(2025, 1, 1),
        amountUsd = BigDecimal("10.00"),
        merchant = "Test",
        description = null,
        type = type,
        expenseCategory = if (type == TransactionType.Expense) "Food" else null,
        incomeCategory = if (type == TransactionType.Income) "Salary" else null,
        tags = emptyList(),
        account = null,
        splitOverallChargedUsd = null,
        note = null,
        confidence = 1f,
        status = TransactionStatus.DRAFT
    )

    @Test fun parse_confirm_cancel_repeat() {
        val p = CorrectionIntentParser()
        assertThat(p.parse("yes", draft())).isInstanceOf(CorrectionIntent.Confirm::class.java)
        assertThat(p.parse("cancel", draft())).isInstanceOf(CorrectionIntent.Cancel::class.java)
        assertThat(p.parse("repeat", draft())).isInstanceOf(CorrectionIntent.Repeat::class.java)
    }

    @Test fun parse_amount_and_overall() {
        val p = CorrectionIntentParser()
        val amount = p.parse("25.00", draft()) as CorrectionIntent.SetAmount
        assertThat(amount.amount.toPlainString()).isEqualTo("25.00")
        val overall = p.parse("overall 100", draft()) as CorrectionIntent.SetOverallCharged
        assertThat(overall.amount.toPlainString()).isEqualTo("100")
    }

    @Test fun parse_type_and_categories() {
        val p = CorrectionIntentParser()
        assertThat(p.parse("income", draft()).javaClass).isEqualTo(CorrectionIntent.SetType(TransactionType.Income).javaClass)
        val expCat = p.parse("category groceries", draft()) as CorrectionIntent.SetExpenseCategory
        assertThat(expCat.name).isEqualTo("groceries")
        val incCat = p.parse("income category bonus", draft(TransactionType.Income)) as CorrectionIntent.SetIncomeCategory
        assertThat(incCat.name).isEqualTo("bonus")
    }

    @Test fun parse_tags_append_and_replace() {
        val p = CorrectionIntentParser()
        val append = p.parse("tags: coffee, lunch", draft()) as CorrectionIntent.SetTags
        assertThat(append.tags).containsExactly("coffee", "lunch").inOrder()
        assertThat(append.replace).isFalse()
        val replace = p.parse("replace tags: groceries, weekly", draft()) as CorrectionIntent.SetTags
        assertThat(replace.tags).containsExactly("groceries", "weekly").inOrder()
        assertThat(replace.replace).isTrue()
    }
}

