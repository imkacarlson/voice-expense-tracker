package com.voiceexpense.ai.parsing.heuristic

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HeuristicExtractorTest {
    private val extractor = HeuristicExtractor()

    @Test
    fun `non split expense populates core fields`() {
        val context = ParsingContext(
            defaultDate = LocalDate.of(2025, 9, 13),
            allowedAccounts = listOf("Citi Double Cash Card"),
            allowedTags = listOf("auto-paid")
        )

        val input = "On September 12th I spent 11.10 getting a takeout pizza from Domino's on my Citi Double Cash card"

        val draft = extractor.extract(input, context)

        assertEquals("Expense", draft.type)
        assertEquals(LocalDate.of(2025, 9, 12), draft.userLocalDate)
        assertEquals("Citi Double Cash Card", draft.account)
        assertEquals("11.10", draft.amountUsd?.toPlainString())
        assertEquals(0, draft.tags.size)
        assertFalse(draft.requiresAi())
    }

    @Test
    fun `split expense captures share and overall`() {
        val context = ParsingContext(
            defaultDate = LocalDate.of(2025, 9, 13),
            allowedAccounts = listOf("Vanguard Cash Plus (Savings)")
        )

        val input = "On September 11th the gas bill was charged to my Vanguard Cash Plus account for 22.24 and after splitting with Emily I will only owe 11.12"

        val draft = extractor.extract(input, context)

        assertEquals(LocalDate.of(2025, 9, 11), draft.userLocalDate)
        assertEquals("Vanguard Cash Plus (Savings)", draft.account)
        assertNotNull(draft.amountUsd)
        assertEquals("splitwise", draft.tags.first())
        assertNotNull(draft.splitOverallChargedUsd)
        assertTrue(draft.splitOverallChargedUsd!! >= draft.amountUsd!!)
    }

    @Test
    fun detects_auto_paid_and_subscription_tags() {
        val context = ParsingContext(
            defaultDate = LocalDate.of(2025, 9, 13),
            allowedTags = listOf("Auto-Paid", "Subscription")
        )

        val input = "On September 10th my New York Times subscription payment was auto charged and it was 26.50"

        val draft = extractor.extract(input, context)

        assertThat(draft.tags).contains("auto-paid")
        assertThat(draft.tags).contains("subscription")
        assertFalse(draft.requiresAi())
    }
}
