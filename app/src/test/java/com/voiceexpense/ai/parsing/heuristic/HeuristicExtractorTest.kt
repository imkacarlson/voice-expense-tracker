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
            allowedAccounts = listOf("Everyday Rewards Card"),
            allowedTags = listOf("auto-paid")
        )

        val input = "On September 12th I spent 11.10 getting a takeout pizza from Domino's on my everyday rewards card"

        val draft = extractor.extract(input, context)

        assertEquals("Expense", draft.type)
        assertEquals(LocalDate.of(2025, 9, 12), draft.userLocalDate)
        assertEquals("Everyday Rewards Card", draft.account)
        assertEquals("Domino's", draft.merchant)
        assertEquals("11.10", draft.amountUsd?.toPlainString())
        assertEquals(0, draft.tags.size)
        assertFalse(draft.requiresAi())
    }

    @Test
    fun `split expense captures share and overall`() {
        val context = ParsingContext(
            defaultDate = LocalDate.of(2025, 9, 13),
            allowedAccounts = listOf("Example Savings Account (1234)")
        )

        val input = "On September 11th the gas bill was charged to my example savings account for 22.24 and after splitting with Sam I will only owe 11.12"

        val draft = extractor.extract(input, context)

        assertEquals(LocalDate.of(2025, 9, 11), draft.userLocalDate)
        assertEquals("Example Savings Account (1234)", draft.account)
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

    @Test
    fun `verbose merchant falls back for ai refinement`() {
        val context = ParsingContext(defaultDate = LocalDate.of(2025, 9, 13))
        val input = "at Taco Bell I just got a couple of seven layer burritos for 6 dollars"

        val draft = extractor.extract(input, context)

        assertThat(draft.merchant).isEqualTo("Taco Bell I just got a couple of seven layer burritos")
        assertThat(draft.confidence(FieldKey.MERCHANT)).isWithin(0.0001f).of(0f)
    }

    @Test
    fun `pronoun led merchant candidate remains low confidence`() {
        val context = ParsingContext(
            defaultDate = LocalDate.of(2025, 10, 14),
            allowedAccounts = listOf("Example Cashback Card (9876)")
        )
        val input = "I went to trader Joe's today to get groceries. the total cost on my example cashback card was $50.68 and from that I owe $25.34 after split wise with a friend"

        val draft = extractor.extract(input, context)

        assertThat(draft.merchant).isEqualTo("that I owe")
        assertThat(draft.confidence(FieldKey.MERCHANT)).isWithin(0.0001f).of(0f)
    }

    @Test
    fun `split expense with long hints captures share and overall`() {
        val context = ParsingContext(defaultDate = LocalDate.of(2025, 10, 14))
        val input = "on October 13th, I grabbed a ride home in a taxi. the total amount charged to my example travel rewards card was $84.60 and the amount I owe after splitting with a friend is $42.30"

        val draft = extractor.extract(input, context)

        assertEquals("42.30", draft.amountUsd?.toPlainString())
        assertEquals("84.60", draft.splitOverallChargedUsd?.toPlainString())
        assertFalse(draft.requiresAi())
    }

    @Test
    fun `heuristic parsed result capitalizes merchant`() {
        val context = ParsingContext(defaultDate = LocalDate.of(2025, 10, 14))
        val parsed = HeuristicDraft(merchant = "the airport").toParsedResult(context)

        assertEquals("The airport", parsed.merchant)
    }

    @Test
    fun `splitwise tag canonicalized without allowed options`() {
        val context = ParsingContext(defaultDate = LocalDate.of(2025, 10, 14))
        val parsed = HeuristicDraft(tags = listOf("splitwise")).toParsedResult(context)

        assertThat(parsed.tags).containsExactly("Splitwise")
    }

    @Test
    fun `heuristic parsed result keeps only allowed tags`() {
        val context = ParsingContext(
            defaultDate = LocalDate.of(2025, 10, 14),
            allowedTags = listOf("Tag One", "TagTwo")
        )
        val parsed = HeuristicDraft(
            tags = listOf("tag one", "watch repair", "TAGTWO")
        ).toParsedResult(context)

        assertThat(parsed.tags).containsExactly("Tag One", "TagTwo").inOrder()
    }
}
